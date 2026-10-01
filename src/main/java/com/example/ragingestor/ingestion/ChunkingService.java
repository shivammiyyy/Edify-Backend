package com.example.ragingestor.ingestion;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Word-based sliding window chunker with overlap. Good enough as a first pass;
 * swap for a token-count-aware (tiktoken-style) splitter before going to production
 * so chunk size lines up with the embedding model's actual token limit rather than
 * an approximate word count.
 */
@Service
public class ChunkingService {

    private static final int DEFAULT_CHUNK_SIZE_WORDS = 220;
    private static final int DEFAULT_OVERLAP_WORDS = 40;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE_WORDS, DEFAULT_OVERLAP_WORDS);
    }

    public List<String> chunk(String text, int chunkSizeWords, int overlapWords) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] words = text.trim().split("\\s+");
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSizeWords - overlapWords);

        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(words.length, start + chunkSizeWords);
            chunks.add(String.join(" ", java.util.Arrays.asList(words).subList(start, end)));
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }
}
