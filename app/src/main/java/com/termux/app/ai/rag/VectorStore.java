package com.termux.app.ai.rag;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple vector store for RAG document storage and retrieval.
 * Uses file-based persistence for documents and embeddings.
 */
public class VectorStore {
    private static final String TAG = "VectorStore";
    private static final String STORE_FILENAME = "vector_store.json";
    private static final String EMBEDDINGS_FILENAME = "embeddings.bin";
    
    private final Context context;
    private final Gson gson;
    private final ConcurrentHashMap<String, Document> documents;
    private final File storeFile;
    
    public VectorStore(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.documents = new ConcurrentHashMap<>();
        this.storeFile = new File(context.getFilesDir(), STORE_FILENAME);
        
        loadFromDisk();
    }

    /**
     * Add a document to the vector store.
     */
    public void addDocument(Document document) {
        if (document == null || document.getId() == null) {
            return;
        }
        documents.put(document.getId(), document);
        saveToDisk();
    }

    /**
     * Add multiple documents at once.
     */
    public void addDocuments(List<Document> docs) {
        for (Document doc : docs) {
            if (doc != null && doc.getId() != null) {
                documents.put(doc.getId(), doc);
            }
        }
        saveToDisk();
    }

    /**
     * Remove a document by ID.
     */
    public void removeDocument(String documentId) {
        documents.remove(documentId);
        saveToDisk();
    }

    /**
     * Get a document by ID.
     */
    public Document getDocument(String documentId) {
        return documents.get(documentId);
    }

    /**
     * Get all documents.
     */
    public List<Document> getAllDocuments() {
        return new ArrayList<>(documents.values());
    }

    /**
     * Get documents by type.
     */
    public List<Document> getDocumentsByType(String type) {
        List<Document> result = new ArrayList<>();
        for (Document doc : documents.values()) {
            if (type.equals(doc.getType())) {
                result.add(doc);
            }
        }
        return result;
    }

    /**
     * Search documents by similarity to query embedding.
     * Returns top-k most similar documents.
     */
    public List<SearchResult> search(float[] queryEmbedding, int topK) {
        List<SearchResult> results = new ArrayList<>();
        
        for (Document doc : documents.values()) {
            if (doc.getEmbedding() != null) {
                float similarity = EmbeddingService.cosineSimilarity(queryEmbedding, doc.getEmbedding());
                results.add(new SearchResult(doc, similarity));
            }
        }
        
        // Sort by similarity descending
        Collections.sort(results, (a, b) -> Float.compare(b.getScore(), a.getScore()));
        
        // Return top-k
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    /**
     * Search documents with a minimum similarity threshold.
     */
    public List<SearchResult> search(float[] queryEmbedding, int topK, float minSimilarity) {
        List<SearchResult> results = search(queryEmbedding, topK);
        List<SearchResult> filtered = new ArrayList<>();
        
        for (SearchResult result : results) {
            if (result.getScore() >= minSimilarity) {
                filtered.add(result);
            }
        }
        
        return filtered;
    }

    /**
     * Search documents by keyword (fallback when no embeddings).
     */
    public List<SearchResult> searchByKeyword(String query, int topK) {
        String queryLower = query.toLowerCase();
        String[] queryTerms = queryLower.split("\\s+");
        List<SearchResult> results = new ArrayList<>();
        
        for (Document doc : documents.values()) {
            String contentLower = doc.getContent().toLowerCase();
            float score = 0;
            
            // Simple term frequency scoring
            for (String term : queryTerms) {
                if (contentLower.contains(term)) {
                    // Count occurrences
                    int count = 0;
                    int index = 0;
                    while ((index = contentLower.indexOf(term, index)) != -1) {
                        count++;
                        index += term.length();
                    }
                    score += count * (1.0f / queryTerms.length);
                }
            }
            
            if (score > 0) {
                // Normalize by document length
                score = score / (float) Math.log(doc.getContent().length() + 1);
                results.add(new SearchResult(doc, score));
            }
        }
        
        // Sort by score descending
        Collections.sort(results, (a, b) -> Float.compare(b.getScore(), a.getScore()));
        
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    /**
     * Get total document count.
     */
    public int getDocumentCount() {
        return documents.size();
    }

    /**
     * Clear all documents.
     */
    public void clear() {
        documents.clear();
        saveToDisk();
    }

    /**
     * Save documents to disk.
     */
    private void saveToDisk() {
        try {
            List<DocumentDTO> dtos = new ArrayList<>();
            for (Document doc : documents.values()) {
                dtos.add(DocumentDTO.fromDocument(doc));
            }
            
            String json = gson.toJson(dtos);
            try (FileWriter writer = new FileWriter(storeFile)) {
                writer.write(json);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving vector store", e);
        }
    }

    /**
     * Load documents from disk.
     */
    private void loadFromDisk() {
        if (!storeFile.exists()) {
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(storeFile))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            Type listType = new TypeToken<List<DocumentDTO>>(){}.getType();
            List<DocumentDTO> dtos = gson.fromJson(json.toString(), listType);
            
            if (dtos != null) {
                for (DocumentDTO dto : dtos) {
                    Document doc = dto.toDocument();
                    documents.put(doc.getId(), doc);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading vector store", e);
        }
    }

    /**
     * Search result wrapper.
     */
    public static class SearchResult {
        private final Document document;
        private final float score;

        public SearchResult(Document document, float score) {
            this.document = document;
            this.score = score;
        }

        public Document getDocument() {
            return document;
        }

        public float getScore() {
            return score;
        }
    }

    /**
     * DTO for JSON serialization.
     */
    private static class DocumentDTO {
        String id;
        String content;
        String source;
        String type;
        long timestamp;
        float[] embedding;

        static DocumentDTO fromDocument(Document doc) {
            DocumentDTO dto = new DocumentDTO();
            dto.id = doc.getId();
            dto.content = doc.getContent();
            dto.source = doc.getSource();
            dto.type = doc.getType();
            dto.timestamp = doc.getTimestamp();
            dto.embedding = doc.getEmbedding();
            return dto;
        }

        Document toDocument() {
            Document doc = new Document(id, content, source, type);
            doc.setEmbedding(embedding);
            return doc;
        }
    }
}
