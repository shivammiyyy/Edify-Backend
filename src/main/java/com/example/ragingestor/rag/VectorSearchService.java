package com.example.ragingestor.rag;

import com.example.ragingestor.domain.ChunkVectorRepository;
import com.example.ragingestor.domain.ChunkVectorRepository.ScoredChunk;
import com.example.ragingestor.security.Role;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorSearchService {

    private static final int TOP_K = 6;

    private final EmbeddingService embeddingService;
    private final ChunkVectorRepository chunkVectorRepository;

    public VectorSearchService(EmbeddingService embeddingService, ChunkVectorRepository chunkVectorRepository) {
        this.embeddingService = embeddingService;
        this.chunkVectorRepository = chunkVectorRepository;
    }

    public List<ScoredChunk> search(String query, Role callerRole) {
        float[] queryEmbedding = embeddingService.embed(query);
        return chunkVectorRepository.topK(queryEmbedding, callerRole, TOP_K);
    }
}
