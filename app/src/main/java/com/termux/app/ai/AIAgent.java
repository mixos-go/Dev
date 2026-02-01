package com.termux.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.MCPOrchestrator;
import com.termux.app.ai.mcp.MCPToolResult;
import com.termux.app.ai.rag.RAGEngine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AIAgent {
    private static final String TAG = "AIAgent";
    private static final String PREFS_NAME = "ai_settings";
    private static final String PREF_ANTHROPIC_KEY = "anthropic_api_key";
    private static final String PREF_HUGGINGFACE_KEY = "huggingface_api_key";
    private static final String PREF_TAVILY_KEY = "tavily_api_key";
    private static final String PREF_OLLAMA_URL = "ollama_base_url";
    private static final String PREF_PRIMARY_MODEL = "primary_model";
    private static final String PREF_RAG_ENABLED = "rag_enabled";
    
    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String DEFAULT_MODEL = "claude-3-5-sonnet-20241022";
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final List<ChatMessage> conversationHistory;
    
    // RAG and MCP components
    private final RAGEngine ragEngine;
    private final MCPOrchestrator mcpOrchestrator;
    
    private String anthropicApiKey;
    private String huggingfaceApiKey;
    private String tavilyApiKey;
    private String ollamaBaseUrl;
    private String primaryModel;
    private boolean ragEnabled;
    
    // Tool execution listener
    private ToolExecutionListener toolExecutionListener;

    public interface AIResponseCallback {
        void onResponse(String response);
        void onToolUse(String toolName, String toolInput, String toolResult);
        void onError(String error);
    }

    public interface ToolExecutionListener {
        void onToolExecuting(String toolName, JsonObject input);
        void onToolResult(String toolName, MCPToolResult result);
        void onConfirmationRequired(String message, Runnable onConfirm, Runnable onDeny);
    }

    public AIAgent(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.conversationHistory = new ArrayList<>();
        
        // Initialize RAG engine
        this.ragEngine = new RAGEngine(context);
        
        // Initialize MCP orchestrator
        this.mcpOrchestrator = new MCPOrchestrator(context);
        
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
        
        loadSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        anthropicApiKey = prefs.getString(PREF_ANTHROPIC_KEY, "");
        huggingfaceApiKey = prefs.getString(PREF_HUGGINGFACE_KEY, "");
        tavilyApiKey = prefs.getString(PREF_TAVILY_KEY, "");
        ollamaBaseUrl = prefs.getString(PREF_OLLAMA_URL, "http://localhost:11434");
        primaryModel = prefs.getString(PREF_PRIMARY_MODEL, DEFAULT_MODEL);
        ragEnabled = prefs.getBoolean(PREF_RAG_ENABLED, true);
    }

    public void saveSettings(String anthropicKey, String huggingfaceKey, 
                            String tavilyKey, String ollamaUrl, String model) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .putString(PREF_ANTHROPIC_KEY, anthropicKey)
            .putString(PREF_HUGGINGFACE_KEY, huggingfaceKey)
            .putString(PREF_TAVILY_KEY, tavilyKey)
            .putString(PREF_OLLAMA_URL, ollamaUrl)
            .putString(PREF_PRIMARY_MODEL, model)
            .apply();
        
        loadSettings();
    }

    public void setToolExecutionListener(ToolExecutionListener listener) {
        this.toolExecutionListener = listener;
    }

    public void setRagEnabled(boolean enabled) {
        this.ragEnabled = enabled;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_RAG_ENABLED, enabled).apply();
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public RAGEngine getRagEngine() {
        return ragEngine;
    }

    public MCPOrchestrator getMcpOrchestrator() {
        return mcpOrchestrator;
    }

    public void sendMessage(String message, AIResponseCallback callback) {
        executor.execute(() -> {
            try {
                // Add user message to history
                conversationHistory.add(new ChatMessage(message, true));
                
                // Retrieve relevant context from RAG if enabled
                String ragContext = "";
                if (ragEnabled) {
                    ragContext = ragEngine.retrieveContextSync(message);
                }
                
                String response;
                
                // Try Anthropic first (primary)
                if (anthropicApiKey != null && !anthropicApiKey.isEmpty()) {
                    response = sendToAnthropicWithTools(message, ragContext, callback);
                }
                // Fallback to Ollama (local)
                else if (ollamaBaseUrl != null && !ollamaBaseUrl.isEmpty()) {
                    response = sendToOllama(message);
                }
                // No API configured
                else {
                    mainHandler.post(() -> callback.onError(
                        "No AI provider configured. Please add your API key in Settings."));
                    return;
                }
                
                // Add AI response to history
                conversationHistory.add(new ChatMessage(response, false));
                
                // Store conversation in RAG for future context
                if (ragEnabled && conversationHistory.size() > 0 && conversationHistory.size() % 10 == 0) {
                    List<String> recentMessages = new ArrayList<>();
                    int start = Math.max(0, conversationHistory.size() - 10);
                    for (int i = start; i < conversationHistory.size(); i++) {
                        recentMessages.add(conversationHistory.get(i).getContent());
                    }
                    ragEngine.ingestConversation("session_" + System.currentTimeMillis(), recentMessages);
                }
                
                final String finalResponse = response;
                mainHandler.post(() -> callback.onResponse(finalResponse));
                
            } catch (Exception e) {
                Log.e(TAG, "Error sending message", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private String sendToAnthropicWithTools(String message, String ragContext, AIResponseCallback callback) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", primaryModel);
        requestBody.addProperty("max_tokens", 4096);
        
        // Build messages array with conversation history
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : conversationHistory) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.isUser() ? "user" : "assistant");
            msgObj.addProperty("content", msg.getContent());
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);
        
        // System prompt with RAG context
        String systemPrompt = getSystemPrompt();
        if (!ragContext.isEmpty()) {
            systemPrompt += "\n\n" + ragContext;
        }
        requestBody.addProperty("system", systemPrompt);
        
        // Add MCP tools
        requestBody.add("tools", mcpOrchestrator.getToolDefinitions());
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(ANTHROPIC_API_URL)
            .addHeader("x-api-key", anthropicApiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("API request failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            // Check if response contains tool use
            JsonArray content = jsonResponse.getAsJsonArray("content");
            if (content != null && content.size() > 0) {
                StringBuilder fullResponse = new StringBuilder();
                boolean hasToolUse = false;
                
                for (int i = 0; i < content.size(); i++) {
                    JsonObject block = content.get(i).getAsJsonObject();
                    String type = block.get("type").getAsString();
                    
                    if ("text".equals(type)) {
                        fullResponse.append(block.get("text").getAsString());
                    } else if ("tool_use".equals(type)) {
                        hasToolUse = true;
                        String toolResult = executeToolAndContinue(block, callback);
                        fullResponse.append("\n").append(toolResult);
                    }
                }
                
                return fullResponse.toString();
            }
            
            return "I received your message but couldn't generate a response.";
        }
    }

    private String executeToolAndContinue(JsonObject toolUse, AIResponseCallback callback) {
        String toolName = toolUse.get("name").getAsString();
        String toolId = toolUse.has("id") ? toolUse.get("id").getAsString() : null;
        JsonObject input = toolUse.has("input") ? toolUse.getAsJsonObject("input") : new JsonObject();
        
        // Notify listener about tool execution
        if (toolExecutionListener != null) {
            mainHandler.post(() -> toolExecutionListener.onToolExecuting(toolName, input));
        }
        
        // Execute tool via MCP orchestrator
        MCPToolResult result = mcpOrchestrator.getRegistry().executeTool(toolName, input);
        
        // Notify listener about result
        if (toolExecutionListener != null) {
            mainHandler.post(() -> toolExecutionListener.onToolResult(toolName, result));
        }
        
        // Notify callback about tool use
        if (callback != null) {
            final String inputStr = gson.toJson(input);
            final String resultStr = result.toString();
            mainHandler.post(() -> callback.onToolUse(toolName, inputStr, resultStr));
        }
        
        // Format result for display
        StringBuilder resultText = new StringBuilder();
        resultText.append("\n[Tool: ").append(toolName).append("]\n");
        
        if (result.isSuccess()) {
            resultText.append(result.getContent());
        } else {
            resultText.append("Error: ").append(result.getErrorMessage());
        }
        
        return resultText.toString();
    }

    private String sendToAnthropic(String message) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", primaryModel);
        requestBody.addProperty("max_tokens", 4096);
        
        // Build messages array with conversation history
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : conversationHistory) {
            JsonObject msgObj = new JsonObject();
            msgObj.addProperty("role", msg.isUser() ? "user" : "assistant");
            msgObj.addProperty("content", msg.getContent());
            messagesArray.add(msgObj);
        }
        requestBody.add("messages", messagesArray);
        
        // System prompt for AI Agent
        requestBody.addProperty("system", getSystemPrompt());
        
        // Add tools for agent capabilities
        requestBody.add("tools", getToolsDefinition());
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(ANTHROPIC_API_URL)
            .addHeader("x-api-key", anthropicApiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("API request failed: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            // Parse response content
            JsonArray content = jsonResponse.getAsJsonArray("content");
            if (content != null && content.size() > 0) {
                JsonObject firstContent = content.get(0).getAsJsonObject();
                String type = firstContent.get("type").getAsString();
                
                if ("text".equals(type)) {
                    return firstContent.get("text").getAsString();
                } else if ("tool_use".equals(type)) {
                    // Handle tool calls
                    return handleToolCall(firstContent);
                }
            }
            
            return "I received your message but couldn't generate a response.";
        }
    }

    private String sendToOllama(String message) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "llama3.2");
        requestBody.addProperty("prompt", message);
        requestBody.addProperty("stream", false);
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(ollamaBaseUrl + "/api/generate")
            .addHeader("content-type", "application/json")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama request failed: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            return jsonResponse.get("response").getAsString();
        }
    }

    private String getSystemPrompt() {
        return """
            You are an AI Agent assistant running inside a Termux terminal application on Android.
            You have the ability to:
            1. Execute terminal commands
            2. Create, read, and edit files
            3. Install packages using pkg/apt
            4. Search the web using Tavily
            5. Browse websites
            6. Manage the development environment
            
            When users ask you to perform tasks, use the available tools to help them.
            Be helpful, concise, and proactive in solving problems.
            
            Important notes:
            - You're running on Android via Termux
            - Use pkg install instead of apt install for packages
            - Common Termux packages: python, nodejs, git, vim, nano, curl, wget
            - The home directory is /data/data/com.termux/files/home
            - Be careful with destructive operations and always confirm with the user first
            """;
    }

    private JsonArray getToolsDefinition() {
        JsonArray tools = new JsonArray();
        
        // Terminal command tool
        JsonObject execTool = new JsonObject();
        execTool.addProperty("name", "execute_command");
        execTool.addProperty("description", "Execute a command in the Termux terminal");
        JsonObject execParams = new JsonObject();
        execParams.addProperty("type", "object");
        JsonObject execProps = new JsonObject();
        JsonObject cmdProp = new JsonObject();
        cmdProp.addProperty("type", "string");
        cmdProp.addProperty("description", "The command to execute");
        execProps.add("command", cmdProp);
        execParams.add("properties", execProps);
        JsonArray required = new JsonArray();
        required.add("command");
        execParams.add("required", required);
        execTool.add("input_schema", execParams);
        tools.add(execTool);
        
        // File operations tool
        JsonObject fileTool = new JsonObject();
        fileTool.addProperty("name", "file_operation");
        fileTool.addProperty("description", "Create, read, or edit files");
        JsonObject fileParams = new JsonObject();
        fileParams.addProperty("type", "object");
        JsonObject fileProps = new JsonObject();
        JsonObject opProp = new JsonObject();
        opProp.addProperty("type", "string");
        opProp.addProperty("description", "Operation: create, read, edit, delete");
        fileProps.add("operation", opProp);
        JsonObject pathProp = new JsonObject();
        pathProp.addProperty("type", "string");
        pathProp.addProperty("description", "File path");
        fileProps.add("path", pathProp);
        JsonObject contentProp = new JsonObject();
        contentProp.addProperty("type", "string");
        contentProp.addProperty("description", "File content (for create/edit)");
        fileProps.add("content", contentProp);
        fileParams.add("properties", fileProps);
        JsonArray fileReq = new JsonArray();
        fileReq.add("operation");
        fileReq.add("path");
        fileParams.add("required", fileReq);
        fileTool.add("input_schema", fileParams);
        tools.add(fileTool);
        
        // Web search tool
        JsonObject searchTool = new JsonObject();
        searchTool.addProperty("name", "web_search");
        searchTool.addProperty("description", "Search the web using Tavily");
        JsonObject searchParams = new JsonObject();
        searchParams.addProperty("type", "object");
        JsonObject searchProps = new JsonObject();
        JsonObject queryProp = new JsonObject();
        queryProp.addProperty("type", "string");
        queryProp.addProperty("description", "Search query");
        searchProps.add("query", queryProp);
        searchParams.add("properties", searchProps);
        JsonArray searchReq = new JsonArray();
        searchReq.add("query");
        searchParams.add("required", searchReq);
        searchTool.add("input_schema", searchParams);
        tools.add(searchTool);
        
        return tools;
    }

    private String handleToolCall(JsonObject toolUse) {
        String toolName = toolUse.get("name").getAsString();
        JsonObject input = toolUse.getAsJsonObject("input");
        
        switch (toolName) {
            case "execute_command":
                String command = input.get("command").getAsString();
                return "I'll execute: `" + command + "`\n\n[Executing command in terminal...]";
                
            case "file_operation":
                String operation = input.get("operation").getAsString();
                String path = input.get("path").getAsString();
                return "I'll " + operation + " the file: " + path;
                
            case "web_search":
                String query = input.get("query").getAsString();
                return "Searching the web for: " + query + "\n\n[Searching...]";
                
            default:
                return "Unknown tool: " + toolName;
        }
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public List<ChatMessage> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }

    public boolean hasApiKey() {
        return (anthropicApiKey != null && !anthropicApiKey.isEmpty()) ||
               (huggingfaceApiKey != null && !huggingfaceApiKey.isEmpty());
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public String getHuggingfaceApiKey() {
        return huggingfaceApiKey;
    }

    public String getTavilyApiKey() {
        return tavilyApiKey;
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public void shutdown() {
        executor.shutdown();
        ragEngine.shutdown();
        mcpOrchestrator.shutdown();
    }
}
