package com.termux.app.ai.mcp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates MCP tool execution and manages multi-step tool chains.
 * Handles tool calls from AI responses and coordinates execution.
 */
public class MCPOrchestrator {
    
    private static final String TAG = "MCPOrchestrator";
    private static final int MAX_TOOL_CHAIN_DEPTH = 10;
    
    private final Context context;
    private final MCPToolRegistry registry;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Gson gson;
    
    private boolean requireUserConfirmation = true;
    private ToolExecutionListener listener;

    public interface ToolExecutionListener {
        void onToolStarted(String toolName, JsonObject input);
        void onToolCompleted(String toolName, MCPToolResult result);
        void onToolError(String toolName, String error);
        void onConfirmationRequired(String toolName, String message, ConfirmationCallback callback);
    }

    public interface ConfirmationCallback {
        void onConfirmed();
        void onDenied();
    }

    public interface ExecutionCallback {
        void onResult(List<ToolExecutionResult> results);
        void onError(String error);
    }

    public MCPOrchestrator(Context context) {
        this.context = context;
        this.registry = new MCPToolRegistry(context);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
    }

    /**
     * Set the listener for tool execution events.
     */
    public void setListener(ToolExecutionListener listener) {
        this.listener = listener;
    }

    /**
     * Enable or disable user confirmation for dangerous operations.
     */
    public void setRequireUserConfirmation(boolean require) {
        this.requireUserConfirmation = require;
    }

    /**
     * Get the tool registry.
     */
    public MCPToolRegistry getRegistry() {
        return registry;
    }

    /**
     * Get tool definitions for inclusion in AI prompts.
     */
    public JsonArray getToolDefinitions() {
        return registry.getToolDefinitionsForAI();
    }

    /**
     * Execute a single tool call.
     */
    public void executeTool(String toolName, JsonObject input, ExecutionCallback callback) {
        executor.execute(() -> {
            try {
                if (listener != null) {
                    mainHandler.post(() -> listener.onToolStarted(toolName, input));
                }
                
                // Check if tool requires confirmation
                if (requireUserConfirmation && registry.toolRequiresConfirmation(toolName)) {
                    MCPTool tool = registry.getTool(toolName);
                    String message = "Allow " + toolName + " to execute with:\n" + gson.toJson(input);
                    
                    mainHandler.post(() -> {
                        if (listener != null) {
                            listener.onConfirmationRequired(toolName, message, new ConfirmationCallback() {
                                @Override
                                public void onConfirmed() {
                                    executor.execute(() -> executeToolInternal(toolName, input, callback));
                                }

                                @Override
                                public void onDenied() {
                                    List<ToolExecutionResult> results = new ArrayList<>();
                                    results.add(new ToolExecutionResult(toolName, 
                                        MCPToolResult.error("User denied execution")));
                                    mainHandler.post(() -> callback.onResult(results));
                                }
                            });
                        } else {
                            // No listener, execute anyway
                            executor.execute(() -> executeToolInternal(toolName, input, callback));
                        }
                    });
                } else {
                    executeToolInternal(toolName, input, callback);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error executing tool", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private void executeToolInternal(String toolName, JsonObject input, ExecutionCallback callback) {
        MCPToolResult result = registry.executeTool(toolName, input);
        
        if (listener != null) {
            mainHandler.post(() -> {
                if (result.isError()) {
                    listener.onToolError(toolName, result.getErrorMessage());
                } else {
                    listener.onToolCompleted(toolName, result);
                }
            });
        }
        
        List<ToolExecutionResult> results = new ArrayList<>();
        results.add(new ToolExecutionResult(toolName, result));
        mainHandler.post(() -> callback.onResult(results));
    }

    /**
     * Execute multiple tool calls in sequence (from AI response).
     */
    public void executeToolChain(List<ToolCall> toolCalls, ExecutionCallback callback) {
        executor.execute(() -> {
            List<ToolExecutionResult> results = new ArrayList<>();
            
            for (ToolCall call : toolCalls) {
                if (listener != null) {
                    mainHandler.post(() -> listener.onToolStarted(call.getName(), call.getInput()));
                }
                
                MCPToolResult result;
                
                // Check confirmation requirement
                if (requireUserConfirmation && registry.toolRequiresConfirmation(call.getName())) {
                    result = MCPToolResult.requiresConfirmation(
                        "Tool " + call.getName() + " requires confirmation");
                } else {
                    result = registry.executeTool(call.getName(), call.getInput());
                }
                
                results.add(new ToolExecutionResult(call.getName(), result));
                
                if (listener != null) {
                    final MCPToolResult finalResult = result;
                    mainHandler.post(() -> {
                        if (finalResult.isError()) {
                            listener.onToolError(call.getName(), finalResult.getErrorMessage());
                        } else {
                            listener.onToolCompleted(call.getName(), finalResult);
                        }
                    });
                }
                
                // Stop chain on error
                if (result.isError()) {
                    break;
                }
            }
            
            mainHandler.post(() -> callback.onResult(results));
        });
    }

    /**
     * Parse tool calls from an AI response.
     */
    public List<ToolCall> parseToolCalls(JsonArray toolUseBlocks) {
        List<ToolCall> calls = new ArrayList<>();
        
        for (int i = 0; i < toolUseBlocks.size(); i++) {
            JsonObject block = toolUseBlocks.get(i).getAsJsonObject();
            
            if (block.has("type") && "tool_use".equals(block.get("type").getAsString())) {
                String name = block.get("name").getAsString();
                String id = block.has("id") ? block.get("id").getAsString() : null;
                JsonObject input = block.has("input") ? block.getAsJsonObject("input") : new JsonObject();
                
                calls.add(new ToolCall(id, name, input));
            }
        }
        
        return calls;
    }

    /**
     * Format tool results for sending back to AI.
     */
    public JsonArray formatToolResults(List<ToolExecutionResult> results) {
        JsonArray resultsArray = new JsonArray();
        
        for (ToolExecutionResult result : results) {
            JsonObject resultObj = new JsonObject();
            resultObj.addProperty("type", "tool_result");
            
            if (result.getToolCallId() != null) {
                resultObj.addProperty("tool_use_id", result.getToolCallId());
            }
            
            if (result.getResult().isSuccess()) {
                resultObj.addProperty("content", result.getResult().getContent());
            } else {
                resultObj.addProperty("content", "Error: " + result.getResult().getErrorMessage());
                resultObj.addProperty("is_error", true);
            }
            
            resultsArray.add(resultObj);
        }
        
        return resultsArray;
    }

    /**
     * Get a summary of available tools.
     */
    public String getToolsSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Available tools:\n\n");
        
        for (MCPTool tool : registry.getAvailableTools()) {
            summary.append("- ").append(tool.getName())
                   .append(" [").append(tool.getCategory()).append("]: ")
                   .append(tool.getDescription())
                   .append("\n");
        }
        
        return summary.toString();
    }

    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Represents a tool call request.
     */
    public static class ToolCall {
        private final String id;
        private final String name;
        private final JsonObject input;

        public ToolCall(String id, String name, JsonObject input) {
            this.id = id;
            this.name = name;
            this.input = input;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public JsonObject getInput() {
            return input;
        }
    }

    /**
     * Represents the result of a tool execution.
     */
    public static class ToolExecutionResult {
        private final String toolName;
        private final String toolCallId;
        private final MCPToolResult result;

        public ToolExecutionResult(String toolName, MCPToolResult result) {
            this(toolName, null, result);
        }

        public ToolExecutionResult(String toolName, String toolCallId, MCPToolResult result) {
            this.toolName = toolName;
            this.toolCallId = toolCallId;
            this.result = result;
        }

        public String getToolName() {
            return toolName;
        }

        public String getToolCallId() {
            return toolCallId;
        }

        public MCPToolResult getResult() {
            return result;
        }
    }
}
