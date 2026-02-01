package com.termux.app.ai.rag;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for generating text embeddings using various providers.
 * Supports: HuggingFace, Ollama (local), and simple TF-IDF fallback.
 */
public class EmbeddingService {
    private static final String TAG = "EmbeddingService";
    private static final String PREFS_NAME = "ai_settings";
    
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    private String huggingfaceApiKey;
    private String ollamaBaseUrl;
    
    // Embedding dimensions
    public static final int DIMENSION_HUGGINGFACE = 384; // all-MiniLM-L6-v2
    public static final int DIMENSION_OLLAMA = 768; // nomic-embed-text
    public static final int DIMENSION_TFIDF = 256; // Simple TF-IDF

    public interface EmbeddingCallback {
        void onSuccess(float[] embedding);
        void onError(String error);
    }

    public interface BatchEmbeddingCallback {
        void onSuccess(List<float[]> embeddings);
        void onError(String error);
    }

    public EmbeddingService(Context context) {
        this.context = context;
        this.gson = new Gson();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
        
        loadSettings();
    }

    private void loadSettings() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        huggingfaceApiKey = prefs.getString("huggingface_api_key", "");
        ollamaBaseUrl = prefs.getString("ollama_base_url", "http://localhost:11434");
    }

    /**
     * Generate embedding for a single text using the best available provider.
     */
    public void generateEmbedding(String text, EmbeddingCallback callback) {
        new Thread(() -> {
            try {
                float[] embedding = generateEmbeddingSync(text);
                callback.onSuccess(embedding);
            } catch (Exception e) {
                Log.e(TAG, "Error generating embedding", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    /**
     * Synchronous embedding generation.
     */
    public float[] generateEmbeddingSync(String text) throws IOException {
        // Try HuggingFace first
        if (huggingfaceApiKey != null && !huggingfaceApiKey.isEmpty()) {
            try {
                return generateHuggingFaceEmbedding(text);
            } catch (Exception e) {
                Log.w(TAG, "HuggingFace embedding failed, trying fallback", e);
            }
        }
        
        // Try Ollama
        if (ollamaBaseUrl != null && !ollamaBaseUrl.isEmpty()) {
            try {
                return generateOllamaEmbedding(text);
            } catch (Exception e) {
                Log.w(TAG, "Ollama embedding failed, using TF-IDF fallback", e);
            }
        }
        
        // Fallback to simple TF-IDF based embedding
        return generateSimpleEmbedding(text);
    }

    /**
     * Generate embedding using HuggingFace Inference API.
     */
    private float[] generateHuggingFaceEmbedding(String text) throws IOException {
        String url = "https://api-inference.huggingface.co/pipeline/feature-extraction/sentence-transformers/all-MiniLM-L6-v2";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("inputs", text);
        requestBody.addProperty("options", true);
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + huggingfaceApiKey)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HuggingFace API error: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonArray embedArray = gson.fromJson(responseBody, JsonArray.class);
            
            float[] embedding = new float[DIMENSION_HUGGINGFACE];
            for (int i = 0; i < Math.min(embedArray.size(), DIMENSION_HUGGINGFACE); i++) {
                embedding[i] = embedArray.get(i).getAsFloat();
            }
            
            return embedding;
        }
    }

    /**
     * Generate embedding using local Ollama instance.
     */
    private float[] generateOllamaEmbedding(String text) throws IOException {
        String url = ollamaBaseUrl + "/api/embeddings";
        
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "nomic-embed-text");
        requestBody.addProperty("prompt", text);
        
        RequestBody body = RequestBody.create(
            gson.toJson(requestBody),
            MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Ollama API error: " + response.code());
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            JsonArray embedArray = jsonResponse.getAsJsonArray("embedding");
            
            float[] embedding = new float[embedArray.size()];
            for (int i = 0; i < embedArray.size(); i++) {
                embedding[i] = embedArray.get(i).getAsFloat();
            }
            
            return embedding;
        }
    }

    /**
     * Simple TF-IDF based embedding as fallback when no API is available.
     * Uses character n-grams and word frequency hashing.
     */
    private float[] generateSimpleEmbedding(String text) {
        float[] embedding = new float[DIMENSION_TFIDF];
        
        // Normalize text
        String normalized = text.toLowerCase().trim();
        String[] words = normalized.split("\\s+");
        
        // Word-level features (first half of embedding)
        for (String word : words) {
            int hash = Math.abs(word.hashCode()) % (DIMENSION_TFIDF / 2);
            embedding[hash] += 1.0f;
        }
        
        // Character trigram features (second half)
        for (int i = 0; i < normalized.length() - 2; i++) {
            String trigram = normalized.substring(i, i + 3);
            int hash = (DIMENSION_TFIDF / 2) + Math.abs(trigram.hashCode()) % (DIMENSION_TFIDF / 2);
            embedding[hash] += 0.5f;
        }
        
        // Normalize the embedding
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
        
        return embedding;
    }

    /**
     * Calculate cosine similarity between two embeddings.
     */
    public static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0f;
        }
        
        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0 || normB == 0) {
            return 0f;
        }
        
        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Get the current embedding dimension based on available provider.
     */
    public int getCurrentDimension() {
        if (huggingfaceApiKey != null && !huggingfaceApiKey.isEmpty()) {
            return DIMENSION_HUGGINGFACE;
        } else if (ollamaBaseUrl != null && !ollamaBaseUrl.isEmpty()) {
            return DIMENSION_OLLAMA;
        }
        return DIMENSION_TFIDF;
    }
}
