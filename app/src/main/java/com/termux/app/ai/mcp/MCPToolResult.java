package com.termux.app.ai.mcp;

import com.google.gson.JsonObject;

/**
 * Result of an MCP tool execution.
 */
public class MCPToolResult {
    
    public enum Status {
        SUCCESS,
        ERROR,
        PENDING,
        REQUIRES_CONFIRMATION
    }
    
    private Status status;
    private String content;
    private String errorMessage;
    private JsonObject data;
    private boolean isStreaming;
    private String streamId;

    private MCPToolResult(Status status) {
        this.status = status;
        this.isStreaming = false;
    }

    public static MCPToolResult success(String content) {
        MCPToolResult result = new MCPToolResult(Status.SUCCESS);
        result.content = content;
        return result;
    }

    public static MCPToolResult success(String content, JsonObject data) {
        MCPToolResult result = new MCPToolResult(Status.SUCCESS);
        result.content = content;
        result.data = data;
        return result;
    }

    public static MCPToolResult error(String errorMessage) {
        MCPToolResult result = new MCPToolResult(Status.ERROR);
        result.errorMessage = errorMessage;
        return result;
    }

    public static MCPToolResult pending(String message) {
        MCPToolResult result = new MCPToolResult(Status.PENDING);
        result.content = message;
        return result;
    }

    public static MCPToolResult requiresConfirmation(String message) {
        MCPToolResult result = new MCPToolResult(Status.REQUIRES_CONFIRMATION);
        result.content = message;
        return result;
    }

    public static MCPToolResult streaming(String streamId, String initialContent) {
        MCPToolResult result = new MCPToolResult(Status.SUCCESS);
        result.isStreaming = true;
        result.streamId = streamId;
        result.content = initialContent;
        return result;
    }

    public Status getStatus() {
        return status;
    }

    public String getContent() {
        return content;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JsonObject getData() {
        return data;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean requiresConfirmation() {
        return status == Status.REQUIRES_CONFIRMATION;
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public String getStreamId() {
        return streamId;
    }

    @Override
    public String toString() {
        if (isError()) {
            return "Error: " + errorMessage;
        }
        return content != null ? content : "";
    }
}
