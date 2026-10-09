package com.example.ragingestor.rag;

import com.example.ragingestor.domain.ChunkVectorRepository.ScoredChunk;
import com.example.ragingestor.security.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightedHybridRerankerTest {

    private final WeightedHybridReranker reranker = new WeightedHybridReranker();

    @Test
    void combinesVectorAndNormalizedKeywordScoresAndReturnsTopResults() {
        ScoredChunk hybrid = chunk(0.8);
        ScoredChunk vectorOnly = chunk(0.9);
        ScoredChunk keywordOnly = chunk(0);

        List<ScoredChunk> ranked = reranker.rerank(
                List.of(hybrid, vectorOnly),
                List.of(withScore(hybrid, 0.5), withScore(keywordOnly, 1.0)),
                2);

        assertEquals(List.of(hybrid.chunkId(), vectorOnly.chunkId()),
                ranked.stream().map(ScoredChunk::chunkId).toList());
        assertEquals(0.71, ranked.get(0).score(), 0.0001);
        assertEquals(0.63, ranked.get(1).score(), 0.0001);
    }

    private static ScoredChunk chunk(double score) {
        UUID id = UUID.randomUUID();
        return new ScoredChunk(id, UUID.randomUUID(), UUID.randomUUID(), "policy.txt", "example",
                Role.READ, score, null, null);
    }

    private static ScoredChunk withScore(ScoredChunk chunk, double score) {
        return new ScoredChunk(chunk.chunkId(), chunk.documentId(), chunk.documentVersionId(),
                chunk.documentName(), chunk.content(), chunk.minRoleRequired(), score, chunk.page(), chunk.section());
    }
}
