package com.example.ragingestor.rag;

import com.example.ragingestor.domain.ChunkVectorRepository.ScoredChunk;

import java.util.List;

public interface Reranker {
    List<ScoredChunk> rerank(List<ScoredChunk> vectorResults, List<ScoredChunk> keywordResults, int limit);
}
