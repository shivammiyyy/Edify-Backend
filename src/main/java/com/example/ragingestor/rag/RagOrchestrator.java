package com.example.ragingestor.rag;

import com.example.ragingestor.domain.ChunkVectorRepository.ScoredChunk;
import com.example.ragingestor.security.Role;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RagOrchestrator {

    private static final String ANSWER_SYSTEM_PROMPT = """
            You are a retrieval-augmented assistant. Answer the user's question using ONLY
            the provided context chunks. If the context does not contain the answer, say so
            plainly instead of guessing. Cite chunk numbers like [1], [2] inline where you use them.
            """;

    private final QueryIntentClassifier intentClassifier;
    private final VectorSearchService vectorSearchService;
    private final LlmClient llmClient;

    public RagOrchestrator(QueryIntentClassifier intentClassifier,
                            VectorSearchService vectorSearchService,
                            LlmClient llmClient) {
        this.intentClassifier = intentClassifier;
        this.vectorSearchService = vectorSearchService;
        this.llmClient = llmClient;
    }

    public record RagAnswer(String answer, boolean inScope, String category, List<UUID> sourceChunkIds, List<UUID> sourceDocumentIds) {}

    public RagAnswer answer(String userQuery, Role callerRole) {
        QueryIntentClassifier.IntentResult intent = intentClassifier.classify(userQuery);

        if (!intent.inScope()) {
            return new RagAnswer(
                    "I can only help with questions about the ingested document corpus. " + intent.reason(),
                    false, intent.category(), List.of(), List.of());
        }

        List<ScoredChunk> chunks = vectorSearchService.search(userQuery, callerRole);
        if (chunks.isEmpty()) {
            return new RagAnswer(
                    "I couldn't find anything in the documents you have access to that answers this.",
                    true, intent.category(), List.of(), List.of());
        }

        String context = buildContext(chunks);
        String userPrompt = "Context:\n" + context + "\n\nQuestion: " + userQuery;
        String answer = llmClient.complete(ANSWER_SYSTEM_PROMPT, userPrompt);

        return new RagAnswer(
                answer,
                true,
                intent.category(),
                chunks.stream().map(ScoredChunk::chunkId).toList(),
                chunks.stream().map(ScoredChunk::documentId).distinct().toList()
        );
    }

    private String buildContext(List<ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(chunks.get(i).content()).append("\n\n");
        }
        return sb.toString();
    }
}
