package com.example.ragingestor.rag;

import com.example.ragingestor.domain.ChunkVectorRepository.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class WeightedHybridReranker implements Reranker {

    private static final double VECTOR_WEIGHT = 0.7;
    private static final double KEYWORD_WEIGHT = 0.3;

    @Override
    public List<ScoredChunk> rerank(List<ScoredChunk> vectorResults, List<ScoredChunk> keywordResults, int limit) {
        Map<UUID, ScoredChunk> chunks = new HashMap<>();
        Map<UUID, Double> vectorScores = new HashMap<>();
        Map<UUID, Double> keywordScores = new HashMap<>();
        double maxKeyword = keywordResults.stream().mapToDouble(ScoredChunk::score).max().orElse(0);
        for (ScoredChunk chunk : vectorResults) {
            chunks.put(chunk.chunkId(), chunk);
            vectorScores.put(chunk.chunkId(), clamp(chunk.score()));
        }
        for (ScoredChunk chunk : keywordResults) {
            chunks.putIfAbsent(chunk.chunkId(), chunk);
            keywordScores.put(chunk.chunkId(), maxKeyword > 0 ? clamp(chunk.score() / maxKeyword) : 0);
        }

        List<ScoredChunk> ranked = new ArrayList<>(chunks.size());
        for (ScoredChunk chunk : chunks.values()) {
            double score = VECTOR_WEIGHT * vectorScores.getOrDefault(chunk.chunkId(), 0.0)
                    + KEYWORD_WEIGHT * keywordScores.getOrDefault(chunk.chunkId(), 0.0);
            ranked.add(new ScoredChunk(chunk.chunkId(), chunk.documentId(), chunk.documentVersionId(),
                    chunk.documentName(), chunk.content(), chunk.minRoleRequired(), score,
                    chunk.page(), chunk.section()));
        }
        return ranked.stream().sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(Math.max(0, limit)).toList();
    }

    private static double clamp(double score) {
        return Math.max(0, Math.min(1, score));
    }
}
