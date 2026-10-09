package com.example.ragingestor.rag;

import com.example.ragingestor.domain.ChunkVectorRepository;
import com.example.ragingestor.domain.ChunkVectorRepository.ScoredChunk;
import com.example.ragingestor.security.Role;
import com.example.ragingestor.observability.EdifyMetrics;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorSearchService {

    private static final int CANDIDATE_K = 20;
    private static final int TOP_K = 6;

    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingCache embeddingCache;
    private final ChunkVectorRepository chunkVectorRepository;
    private final Reranker reranker;
    private final EdifyMetrics metrics;

    public VectorSearchService(EmbeddingProvider embeddingProvider, EmbeddingCache embeddingCache,
                               ChunkVectorRepository chunkVectorRepository, Reranker reranker,
                               EdifyMetrics metrics) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingCache = embeddingCache;
        this.chunkVectorRepository = chunkVectorRepository;
        this.reranker = reranker;
        this.metrics = metrics;
    }

    public List<ScoredChunk> search(String query, Role callerRole, RetrievalFilters filters) {
        float[] queryEmbedding = metrics.time("edify.embedding.duration",
                () -> embeddingCache.embed(embeddingProvider, query));
        return metrics.time("edify.retrieval.duration", () -> retrieve(query, callerRole, filters, queryEmbedding));
    }

    private List<ScoredChunk> retrieve(String query, Role callerRole, RetrievalFilters filters,
                                       float[] queryEmbedding) {
        List<ScoredChunk> vectorResults = chunkVectorRepository.vectorTopK(
                queryEmbedding, callerRole, filters, CANDIDATE_K);
        List<ScoredChunk> keywordResults = chunkVectorRepository.keywordTopK(
                query, callerRole, filters, CANDIDATE_K);
        return reranker.rerank(vectorResults, keywordResults, TOP_K);
    }
}
