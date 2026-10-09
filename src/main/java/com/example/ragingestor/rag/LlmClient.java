package com.example.ragingestor.rag;

/** @deprecated Inject {@link LlmProvider} instead. */
@Deprecated
public interface LlmClient extends LlmProvider {
    /**
     * Single-turn completion: system prompt + user prompt in, raw text answer out.
     * Kept deliberately minimal (no streaming, no tool calls) since both callers here
     * (intent classification, answer synthesis) just need one shot in/out.
     */
}
