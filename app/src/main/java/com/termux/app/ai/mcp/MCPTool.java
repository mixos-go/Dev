package com.termux.app.ai.mcp;

import com.google.gson.JsonObject;

/**
 * Base interface for MCP (Model Context Protocol) tools.
 * Tools provide capabilities that the AI agent can use to interact
 * with the system and external services.
 */
public interface MCPTool {
    
    /**
     * Get the unique name of this tool.
     */
    String getName();
    
    /**
     * Get a human-readable description of what this tool does.
     */
    String getDescription();
    
    /**
     * Get the JSON schema for the tool's input parameters.
     */
    JsonObject getInputSchema();
    
    /**
     * Execute the tool with the given input parameters.
     * @param input The input parameters as a JSON object
     * @return The result of the tool execution
     */
    MCPToolResult execute(JsonObject input);
    
    /**
     * Check if this tool requires user confirmation before execution.
     */
    default boolean requiresConfirmation() {
        return false;
    }
    
    /**
     * Get the category of this tool for organization.
     */
    default String getCategory() {
        return "general";
    }
    
    /**
     * Check if this tool is available (e.g., has required dependencies).
     */
    default boolean isAvailable() {
        return true;
    }
}
