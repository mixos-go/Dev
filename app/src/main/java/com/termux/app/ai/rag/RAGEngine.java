package com.termux.app.ai.rag;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main RAG (Retrieval-Augmented Generation) Engine.
 * Orchestrates document ingestion, embedding, storage, and retrieval.
 */
public class RAGEngine {
    private static final String TAG = "RAGEngine";
    
    private final Context context;
    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final DocumentChunker chunker;
    private final ExecutorService executor;
    private final Handler mainHandler;
    
    // RAG settings
    private int topK = 5;
    private float minSimilarity = 0.3f;
    private boolean useEmbeddings = true;

    public interface RAGCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    public interface DocumentCallback {
        void onSuccess(int documentCount);
        void onError(String error);
        void onProgress(int current, int total);
    }

    public RAGEngine(Context context) {
        this.context = context;
        this.vectorStore = new VectorStore(context);
        this.embeddingService = new EmbeddingService(context);
        this.chunker = new DocumentChunker();
        this.executor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Ingest a single document into the knowledge base.
     */
    public void ingestDocument(Document document, DocumentCallback callback) {
        executor.execute(() -> {
            try {
                List<Document> chunks = chunker.chunkDocument(document);
                int total = chunks.size();
                int processed = 0;
                
                for (Document chunk : chunks) {
                    // Generate embedding
                    if (useEmbeddings) {
                        try {
                            float[] embedding = embeddingService.generateEmbeddingSync(chunk.getContent());
                            chunk.setEmbedding(embedding);
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to generate embedding, storing without", e);
                        }
                    }
                    
                    vectorStore.addDocument(chunk);
                    processed++;
                    
                    final int current = processed;
                    mainHandler.post(() -> callback.onProgress(current, total));
                }
                
                mainHandler.post(() -> callback.onSuccess(chunks.size()));
                
            } catch (Exception e) {
                Log.e(TAG, "Error ingesting document", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Ingest a file from the filesystem.
     */
    public void ingestFile(String filePath, DocumentCallback callback) {
        executor.execute(() -> {
            try {
                File file = new File(filePath);
                if (!file.exists() || !file.canRead()) {
                    mainHandler.post(() -> callback.onError("Cannot read file: " + filePath));
                    return;
                }
                
                // Read file content
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                }
                
                // Determine document type
                String type = determineFileType(filePath);
                
                // Create document
                Document document = new Document(content.toString(), filePath, type);
                document.getMetadata().setTitle(file.getName());
                
                // Ingest
                ingestDocument(document, callback);
                
            } catch (Exception e) {
                Log.e(TAG, "Error ingesting file", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Ingest an entire directory recursively.
     */
    public void ingestDirectory(String directoryPath, DocumentCallback callback) {
        executor.execute(() -> {
            try {
                File dir = new File(directoryPath);
                if (!dir.exists() || !dir.isDirectory()) {
                    mainHandler.post(() -> callback.onError("Invalid directory: " + directoryPath));
                    return;
                }
                
                List<File> files = collectFiles(dir);
                int total = files.size();
                int processed = 0;
                int successCount = 0;
                
                for (File file : files) {
                    try {
                        // Read file
                        StringBuilder content = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                content.append(line).append("\n");
                            }
                        }
                        
                        String type = determineFileType(file.getAbsolutePath());
                        Document document = new Document(content.toString(), file.getAbsolutePath(), type);
                        document.getMetadata().setTitle(file.getName());
                        
                        // Chunk and store
                        List<Document> chunks = chunker.chunkDocument(document);
                        for (Document chunk : chunks) {
                            if (useEmbeddings) {
                                try {
                                    float[] embedding = embeddingService.generateEmbeddingSync(chunk.getContent());
                                    chunk.setEmbedding(embedding);
                                } catch (Exception e) {
                                    // Continue without embedding
                                }
                            }
                            vectorStore.addDocument(chunk);
                        }
                        successCount += chunks.size();
                        
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to process file: " + file.getAbsolutePath(), e);
                    }
                    
                    processed++;
                    final int current = processed;
                    mainHandler.post(() -> callback.onProgress(current, total));
                }
                
                final int finalCount = successCount;
                mainHandler.post(() -> callback.onSuccess(finalCount));
                
            } catch (Exception e) {
                Log.e(TAG, "Error ingesting directory", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Ingest conversation history for context.
     */
    public void ingestConversation(String sessionId, List<String> messages) {
        executor.execute(() -> {
            try {
                StringBuilder conversationText = new StringBuilder();
                for (int i = 0; i < messages.size(); i++) {
                    String role = (i % 2 == 0) ? "User" : "Assistant";
                    conversationText.append(role).append(": ").append(messages.get(i)).append("\n\n");
                }
                
                Document document = new Document(
                    conversationText.toString(),
                    "session:" + sessionId,
                    Document.Types.CONVERSATION
                );
                document.getMetadata().setTitle("Conversation " + sessionId);
                
                List<Document> chunks = chunker.chunkDocument(document);
                for (Document chunk : chunks) {
                    if (useEmbeddings) {
                        try {
                            float[] embedding = embeddingService.generateEmbeddingSync(chunk.getContent());
                            chunk.setEmbedding(embedding);
                        } catch (Exception e) {
                            // Continue without embedding
                        }
                    }
                    vectorStore.addDocument(chunk);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error ingesting conversation", e);
            }
        });
    }

    /**
     * Retrieve relevant context for a query.
     */
    public void retrieveContext(String query, RAGCallback callback) {
        executor.execute(() -> {
            try {
                List<VectorStore.SearchResult> results;
                
                if (useEmbeddings) {
                    // Generate query embedding
                    float[] queryEmbedding = embeddingService.generateEmbeddingSync(query);
                    results = vectorStore.search(queryEmbedding, topK, minSimilarity);
                } else {
                    // Fallback to keyword search
                    results = vectorStore.searchByKeyword(query, topK);
                }
                
                // Build context string
                StringBuilder context = new StringBuilder();
                if (!results.isEmpty()) {
                    context.append("Relevant context from knowledge base:\n\n");
                    for (int i = 0; i < results.size(); i++) {
                        VectorStore.SearchResult result = results.get(i);
                        context.append("--- Source: ")
                               .append(result.getDocument().getSource())
                               .append(" (relevance: ")
                               .append(String.format("%.2f", result.getScore()))
                               .append(") ---\n")
                               .append(result.getDocument().getContent())
                               .append("\n\n");
                    }
                }
                
                final String contextStr = context.toString();
                mainHandler.post(() -> callback.onSuccess(contextStr));
                
            } catch (Exception e) {
                Log.e(TAG, "Error retrieving context", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Synchronous context retrieval for integration with AIAgent.
     */
    public String retrieveContextSync(String query) {
        try {
            List<VectorStore.SearchResult> results;
            
            if (useEmbeddings) {
                float[] queryEmbedding = embeddingService.generateEmbeddingSync(query);
                results = vectorStore.search(queryEmbedding, topK, minSimilarity);
            } else {
                results = vectorStore.searchByKeyword(query, topK);
            }
            
            if (results.isEmpty()) {
                return "";
            }
            
            StringBuilder context = new StringBuilder();
            context.append("Relevant context from knowledge base:\n\n");
            
            for (VectorStore.SearchResult result : results) {
                context.append("--- Source: ")
                       .append(result.getDocument().getSource())
                       .append(" ---\n")
                       .append(result.getDocument().getContent())
                       .append("\n\n");
            }
            
            return context.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error in sync context retrieval", e);
            return "";
        }
    }

    /**
     * Add manual knowledge entry.
     */
    public void addKnowledge(String title, String content, RAGCallback callback) {
        executor.execute(() -> {
            try {
                Document document = new Document(content, "manual:" + title, Document.Types.MANUAL);
                document.getMetadata().setTitle(title);
                
                if (useEmbeddings) {
                    float[] embedding = embeddingService.generateEmbeddingSync(content);
                    document.setEmbedding(embedding);
                }
                
                vectorStore.addDocument(document);
                mainHandler.post(() -> callback.onSuccess("Knowledge added: " + title));
                
            } catch (Exception e) {
                Log.e(TAG, "Error adding knowledge", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Clear all knowledge base data.
     */
    public void clearKnowledgeBase() {
        vectorStore.clear();
    }

    /**
     * Get knowledge base statistics.
     */
    public KnowledgeBaseStats getStats() {
        KnowledgeBaseStats stats = new KnowledgeBaseStats();
        stats.totalDocuments = vectorStore.getDocumentCount();
        stats.fileDocuments = vectorStore.getDocumentsByType(Document.Types.FILE).size();
        stats.codeDocuments = vectorStore.getDocumentsByType(Document.Types.CODE).size();
        stats.conversationDocuments = vectorStore.getDocumentsByType(Document.Types.CONVERSATION).size();
        stats.manualDocuments = vectorStore.getDocumentsByType(Document.Types.MANUAL).size();
        return stats;
    }

    // Helper methods

    private String determineFileType(String filePath) {
        String lower = filePath.toLowerCase();
        
        // Code files
        if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".py") ||
            lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".jsx") ||
            lower.endsWith(".tsx") || lower.endsWith(".go") || lower.endsWith(".rs") ||
            lower.endsWith(".cpp") || lower.endsWith(".c") || lower.endsWith(".h") ||
            lower.endsWith(".swift") || lower.endsWith(".rb") || lower.endsWith(".php")) {
            return Document.Types.CODE;
        }
        
        return Document.Types.FILE;
    }

    private List<File> collectFiles(File directory) {
        List<File> files = new ArrayList<>();
        collectFilesRecursive(directory, files);
        return files;
    }

    private void collectFilesRecursive(File directory, List<File> files) {
        File[] contents = directory.listFiles();
        if (contents == null) return;
        
        for (File file : contents) {
            // Skip hidden files and common ignored directories
            if (file.getName().startsWith(".")) continue;
            if (file.getName().equals("node_modules")) continue;
            if (file.getName().equals("build")) continue;
            if (file.getName().equals("__pycache__")) continue;
            
            if (file.isDirectory()) {
                collectFilesRecursive(file, files);
            } else if (file.isFile() && file.canRead()) {
                // Only process text-like files
                if (isTextFile(file.getName())) {
                    files.add(file);
                }
            }
        }
    }

    private boolean isTextFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".java") ||
               lower.endsWith(".kt") || lower.endsWith(".py") || lower.endsWith(".js") ||
               lower.endsWith(".ts") || lower.endsWith(".jsx") || lower.endsWith(".tsx") ||
               lower.endsWith(".go") || lower.endsWith(".rs") || lower.endsWith(".cpp") ||
               lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".swift") ||
               lower.endsWith(".rb") || lower.endsWith(".php") || lower.endsWith(".xml") ||
               lower.endsWith(".json") || lower.endsWith(".yaml") || lower.endsWith(".yml") ||
               lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".html") ||
               lower.endsWith(".css") || lower.endsWith(".scss") || lower.endsWith(".sql") ||
               lower.endsWith(".gradle") || lower.endsWith(".properties");
    }

    // Setters for configuration

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public void setMinSimilarity(float minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public void setUseEmbeddings(boolean useEmbeddings) {
        this.useEmbeddings = useEmbeddings;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Statistics about the knowledge base.
     */
    public static class KnowledgeBaseStats {
        public int totalDocuments;
        public int fileDocuments;
        public int codeDocuments;
        public int conversationDocuments;
        public int manualDocuments;
    }
}
