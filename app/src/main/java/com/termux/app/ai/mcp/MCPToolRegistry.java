package com.termux.app.ai.mcp;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.termux.app.ai.mcp.tools.FileOperationTool;
import com.termux.app.ai.mcp.tools.PackageManagerTool;
import com.termux.app.ai.mcp.tools.TerminalTool;
import com.termux.app.ai.mcp.tools.WebBrowserTool;
import com.termux.app.ai.mcp.tools.WebSearchTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for MCP tools. Manages tool registration, discovery, and lookup.
 */
public class MCPToolRegistry {
    
    private static final String TAG = "MCPToolRegistry";
    
    private final Context context;
    private final Map<String, MCPTool> tools;
    private final Map<String, List<MCPTool>> toolsByCategory;

    public MCPToolRegistry(Context context) {
        this.context = context;
        this.tools = new HashMap<>();
        this.toolsByCategory = new HashMap<>();
        
        // Register built-in tools
        registerBuiltInTools();
    }

    /**
     * Register all built-in tools.
     */
    private void registerBuiltInTools() {
        // Terminal execution
        registerTool(new TerminalTool(context));
        
        // File operations
        registerTool(new FileOperationTool(context));
        
        // Web search (Tavily)
        registerTool(new WebSearchTool(context));
        
        // Web browser
        registerTool(new WebBrowserTool(context));
        
        // Package manager
        registerTool(new PackageManagerTool(context));
    }

    /**
     * Register a new tool.
     */
    public void registerTool(MCPTool tool) {
        if (tool == null || tool.getName() == null) {
            return;
        }
        
        tools.put(tool.getName(), tool);
        
        // Add to category index
        String category = tool.getCategory();
        if (!toolsByCategory.containsKey(category)) {
            toolsByCategory.put(category, new ArrayList<>());
        }
        toolsByCategory.get(category).add(tool);
    }

    /**
     * Unregister a tool by name.
     */
    public void unregisterTool(String name) {
        MCPTool tool = tools.remove(name);
        if (tool != null) {
            List<MCPTool> categoryList = toolsByCategory.get(tool.getCategory());
            if (categoryList != null) {
                categoryList.remove(tool);
            }
        }
    }

    /**
     * Get a tool by name.
     */
    public MCPTool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Get all registered tools.
     */
    public List<MCPTool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * Get tools by category.
     */
    public List<MCPTool> getToolsByCategory(String category) {
        return toolsByCategory.getOrDefault(category, new ArrayList<>());
    }

    /**
     * Get all available tools (checking availability).
     */
    public List<MCPTool> getAvailableTools() {
        List<MCPTool> available = new ArrayList<>();
        for (MCPTool tool : tools.values()) {
            if (tool.isAvailable()) {
                available.add(tool);
            }
        }
        return available;
    }

    /**
     * Get tool definitions for AI model (Claude/Anthropic format).
     */
    public JsonArray getToolDefinitionsForAI() {
        JsonArray toolsArray = new JsonArray();
        
        for (MCPTool tool : getAvailableTools()) {
            JsonObject toolDef = new JsonObject();
            toolDef.addProperty("name", tool.getName());
            toolDef.addProperty("description", tool.getDescription());
            toolDef.add("input_schema", tool.getInputSchema());
            toolsArray.add(toolDef);
        }
        
        return toolsArray;
    }

    /**
     * Execute a tool by name with given input.
     */
    public MCPToolResult executeTool(String toolName, JsonObject input) {
        MCPTool tool = tools.get(toolName);
        
        if (tool == null) {
            return MCPToolResult.error("Unknown tool: " + toolName);
        }
        
        if (!tool.isAvailable()) {
            return MCPToolResult.error("Tool not available: " + toolName);
        }
        
        return tool.execute(input);
    }

    /**
     * Check if a tool requires confirmation.
     */
    public boolean toolRequiresConfirmation(String toolName) {
        MCPTool tool = tools.get(toolName);
        return tool != null && tool.requiresConfirmation();
    }

    /**
     * Get all categories.
     */
    public List<String> getCategories() {
        return new ArrayList<>(toolsByCategory.keySet());
    }

    /**
     * Get tool count.
     */
    public int getToolCount() {
        return tools.size();
    }

    /**
     * Check if a tool exists.
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }
}
