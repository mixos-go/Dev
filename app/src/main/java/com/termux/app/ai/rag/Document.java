package com.termux.app.ai.rag;

import java.util.UUID;

/**
 * Represents a document in the RAG knowledge base.
 * Documents are chunked and embedded for semantic search.
 */
public class Document {
    private String id;
    private String content;
    private String source;
    private String type;
    private long timestamp;
    private float[] embedding;
    private DocumentMetadata metadata;

    public Document(String content, String source, String type) {
        this.id = UUID.randomUUID().toString();
        this.content = content;
        this.source = source;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.metadata = new DocumentMetadata();
    }

    public Document(String id, String content, String source, String type) {
        this.id = id;
        this.content = content;
        this.source = source;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.metadata = new DocumentMetadata();
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public DocumentMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(DocumentMetadata metadata) {
        this.metadata = metadata;
    }

    /**
     * Document types for categorization
     */
    public static class Types {
        public static final String FILE = "file";
        public static final String CODE = "code";
        public static final String CONVERSATION = "conversation";
        public static final String WEB = "web";
        public static final String MANUAL = "manual";
        public static final String TERMINAL_OUTPUT = "terminal_output";
    }
}
