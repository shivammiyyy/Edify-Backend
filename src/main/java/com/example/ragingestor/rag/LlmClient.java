package com.example.ragingestor.rag;

public interface LlmClient {
    /**
     * Single-turn completion: system prompt + user prompt in, raw text answer out.
     * Kept deliberately minimal (no streaming, no tool calls) since both callers here
     * (intent classification, answer synthesis) just need one shot in/out.
     */
    String complete(String systemPrompt, String userPrompt);
}
