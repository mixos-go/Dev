package com.termux.app.ai;

import java.util.UUID;

public class ChatMessage {
    private String id;
    private String content;
    private boolean isUser;
    private boolean isError;
    private long timestamp;
    private String toolCalls;
    private boolean isLoading;

    public ChatMessage(String content, boolean isUser) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.isUser = isUser;
        this.isError = false;
        this.timestamp = System.currentTimeMillis();
        this.toolCalls = null;
        this.isLoading = false;
    }

    public String getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isUser() {
        return isUser;
    }

    public boolean isError() {
        return isError;
    }

    public void setError(boolean error) {
        isError = error;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(String toolCalls) {
        this.toolCalls = toolCalls;
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }
}
