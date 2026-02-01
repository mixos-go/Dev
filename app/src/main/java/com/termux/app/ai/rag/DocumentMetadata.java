package com.termux.app.ai.rag;

import java.util.HashMap;
import java.util.Map;

/**
 * Metadata associated with a document in the RAG system.
 */
public class DocumentMetadata {
    private String title;
    private String author;
    private String language;
    private int chunkIndex;
    private int totalChunks;
    private String parentDocId;
    private Map<String, String> customFields;

    public DocumentMetadata() {
        this.customFields = new HashMap<>();
        this.chunkIndex = 0;
        this.totalChunks = 1;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public String getParentDocId() {
        return parentDocId;
    }

    public void setParentDocId(String parentDocId) {
        this.parentDocId = parentDocId;
    }

    public Map<String, String> getCustomFields() {
        return customFields;
    }

    public void setCustomField(String key, String value) {
        this.customFields.put(key, value);
    }

    public String getCustomField(String key) {
        return this.customFields.get(key);
    }
}
