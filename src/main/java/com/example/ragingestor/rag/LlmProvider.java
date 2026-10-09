package com.example.ragingestor.rag;

import java.util.function.Consumer;

public interface LlmProvider {
    String complete(String systemPrompt, String userPrompt);

    default void stream(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        tokenConsumer.accept(complete(systemPrompt, userPrompt));
    }
}
