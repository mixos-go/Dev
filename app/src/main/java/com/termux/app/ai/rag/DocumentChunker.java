package com.termux.app.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for chunking documents into smaller pieces for embedding.
 * Supports multiple chunking strategies based on document type.
 */
public class DocumentChunker {
    
    // Default chunk settings
    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;
    private static final int MIN_CHUNK_SIZE = 100;
    
    private int chunkSize;
    private int chunkOverlap;

    public DocumentChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public DocumentChunker(int chunkSize, int chunkOverlap) {
        this.chunkSize = Math.max(chunkSize, MIN_CHUNK_SIZE);
        this.chunkOverlap = Math.min(chunkOverlap, chunkSize / 2);
    }

    /**
     * Chunk a document based on its type.
     */
    public List<Document> chunkDocument(Document document) {
        String type = document.getType();
        
        if (Document.Types.CODE.equals(type)) {
            return chunkCode(document);
        } else if (Document.Types.CONVERSATION.equals(type)) {
            return chunkConversation(document);
        } else {
            return chunkText(document);
        }
    }

    /**
     * Simple text chunking with overlap.
     */
    public List<Document> chunkText(Document document) {
        List<Document> chunks = new ArrayList<>();
        String content = document.getContent();
        
        if (content.length() <= chunkSize) {
            chunks.add(document);
            return chunks;
        }
        
        // Try to split on paragraph boundaries first
        String[] paragraphs = content.split("\n\n+");
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        
        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > chunkSize && currentChunk.length() > 0) {
                // Save current chunk
                chunks.add(createChunk(document, currentChunk.toString(), chunkIndex, -1));
                chunkIndex++;
                
                // Start new chunk with overlap
                String overlap = getOverlapText(currentChunk.toString());
                currentChunk = new StringBuilder(overlap);
            }
            
            if (paragraph.length() > chunkSize) {
                // Paragraph too large, chunk by sentences
                List<String> sentences = splitIntoSentences(paragraph);
                for (String sentence : sentences) {
                    if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                        chunks.add(createChunk(document, currentChunk.toString(), chunkIndex, -1));
                        chunkIndex++;
                        String overlap = getOverlapText(currentChunk.toString());
                        currentChunk = new StringBuilder(overlap);
                    }
                    currentChunk.append(sentence).append(" ");
                }
            } else {
                currentChunk.append(paragraph).append("\n\n");
            }
        }
        
        // Add remaining content
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(document, currentChunk.toString().trim(), chunkIndex, -1));
        }
        
        // Update total chunks count
        int total = chunks.size();
        for (Document chunk : chunks) {
            chunk.getMetadata().setTotalChunks(total);
        }
        
        return chunks;
    }

    /**
     * Code-aware chunking that tries to keep functions/classes together.
     */
    public List<Document> chunkCode(Document document) {
        List<Document> chunks = new ArrayList<>();
        String content = document.getContent();
        String source = document.getSource();
        
        // Detect language from source extension
        String language = detectLanguage(source);
        
        // Try to split on function/class boundaries
        List<String> codeBlocks = splitCodeBlocks(content, language);
        int chunkIndex = 0;
        StringBuilder currentChunk = new StringBuilder();
        
        for (String block : codeBlocks) {
            if (currentChunk.length() + block.length() > chunkSize && currentChunk.length() > 0) {
                Document chunk = createChunk(document, currentChunk.toString(), chunkIndex, -1);
                chunk.getMetadata().setLanguage(language);
                chunks.add(chunk);
                chunkIndex++;
                currentChunk = new StringBuilder();
            }
            
            if (block.length() > chunkSize) {
                // Block too large, split by lines
                String[] lines = block.split("\n");
                for (String line : lines) {
                    if (currentChunk.length() + line.length() > chunkSize && currentChunk.length() > 0) {
                        Document chunk = createChunk(document, currentChunk.toString(), chunkIndex, -1);
                        chunk.getMetadata().setLanguage(language);
                        chunks.add(chunk);
                        chunkIndex++;
                        currentChunk = new StringBuilder();
                    }
                    currentChunk.append(line).append("\n");
                }
            } else {
                currentChunk.append(block).append("\n\n");
            }
        }
        
        if (currentChunk.length() > 0) {
            Document chunk = createChunk(document, currentChunk.toString().trim(), chunkIndex, -1);
            chunk.getMetadata().setLanguage(language);
            chunks.add(chunk);
        }
        
        int total = chunks.size();
        for (Document chunk : chunks) {
            chunk.getMetadata().setTotalChunks(total);
        }
        
        return chunks;
    }

    /**
     * Conversation chunking that keeps message pairs together.
     */
    public List<Document> chunkConversation(Document document) {
        List<Document> chunks = new ArrayList<>();
        String content = document.getContent();
        
        // Split by message markers (User:/Assistant: or similar)
        Pattern messagePattern = Pattern.compile("(?m)^(User|Assistant|Human|AI|System):\\s*", Pattern.CASE_INSENSITIVE);
        String[] messages = messagePattern.split(content);
        Matcher matcher = messagePattern.matcher(content);
        List<String> roles = new ArrayList<>();
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        int messageIndex = 0;
        
        for (String message : messages) {
            if (message.trim().isEmpty()) continue;
            
            String role = messageIndex < roles.size() ? roles.get(messageIndex) + ": " : "";
            String fullMessage = role + message.trim();
            
            if (currentChunk.length() + fullMessage.length() > chunkSize && currentChunk.length() > 0) {
                chunks.add(createChunk(document, currentChunk.toString(), chunkIndex, -1));
                chunkIndex++;
                currentChunk = new StringBuilder();
            }
            
            currentChunk.append(fullMessage).append("\n\n");
            messageIndex++;
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(createChunk(document, currentChunk.toString().trim(), chunkIndex, -1));
        }
        
        int total = chunks.size();
        for (Document chunk : chunks) {
            chunk.getMetadata().setTotalChunks(total);
        }
        
        return chunks;
    }

    private Document createChunk(Document parent, String content, int chunkIndex, int totalChunks) {
        Document chunk = new Document(content, parent.getSource(), parent.getType());
        chunk.getMetadata().setParentDocId(parent.getId());
        chunk.getMetadata().setChunkIndex(chunkIndex);
        chunk.getMetadata().setTotalChunks(totalChunks);
        chunk.getMetadata().setTitle(parent.getMetadata().getTitle());
        return chunk;
    }

    private String getOverlapText(String text) {
        if (text.length() <= chunkOverlap) {
            return text;
        }
        return text.substring(text.length() - chunkOverlap);
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Pattern pattern = Pattern.compile("[^.!?]*[.!?]+\\s*|[^.!?]+$");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private String detectLanguage(String source) {
        if (source == null) return "unknown";
        
        String lower = source.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".kt")) return "kotlin";
        if (lower.endsWith(".swift")) return "swift";
        if (lower.endsWith(".go")) return "go";
        if (lower.endsWith(".rs")) return "rust";
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".c")) return "c/c++";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "bash";
        if (lower.endsWith(".xml")) return "xml";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        
        return "unknown";
    }

    private List<String> splitCodeBlocks(String content, String language) {
        List<String> blocks = new ArrayList<>();
        
        // Language-specific patterns for function/class detection
        Pattern blockPattern;
        
        switch (language) {
            case "java":
            case "kotlin":
                blockPattern = Pattern.compile(
                    "(?m)^\\s*(public|private|protected|static|final|abstract|class|interface|enum|@|/\\*\\*)[^{]*\\{",
                    Pattern.MULTILINE
                );
                break;
            case "python":
                blockPattern = Pattern.compile(
                    "(?m)^\\s*(def |class |async def |@)",
                    Pattern.MULTILINE
                );
                break;
            case "javascript":
            case "typescript":
                blockPattern = Pattern.compile(
                    "(?m)^\\s*(function |const |let |var |class |export |import |async )",
                    Pattern.MULTILINE
                );
                break;
            default:
                // Generic: split on empty lines
                return List.of(content.split("\n\n+"));
        }
        
        Matcher matcher = blockPattern.matcher(content);
        int lastEnd = 0;
        
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String block = content.substring(lastEnd, matcher.start()).trim();
                if (!block.isEmpty()) {
                    blocks.add(block);
                }
            }
            lastEnd = matcher.start();
        }
        
        // Add remaining content
        if (lastEnd < content.length()) {
            String remaining = content.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                blocks.add(remaining);
            }
        }
        
        if (blocks.isEmpty()) {
            blocks.add(content);
        }
        
        return blocks;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = Math.max(chunkSize, MIN_CHUNK_SIZE);
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = Math.min(chunkOverlap, this.chunkSize / 2);
    }
}
