package com.example.ragingestor.rag;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
public class HttpLlmClient implements LlmClient {

    private final WebClient webClient;
    private final String url;
    private final String model;

    public HttpLlmClient(WebClient webClient,
                          @Value("${app.llm.url}") String url,
                          @Value("${app.llm.model}") String model) {
        this.webClient = webClient;
        this.url = url;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        JsonObject body = new JsonObject()
                .put("model", model)
                .put("stream", false)
                .put("think", false)
                .put("messages", new JsonArray()
                        .add(new JsonObject().put("role", "system").put("content", systemPrompt))
                        .add(new JsonObject().put("role", "user").put("content", userPrompt)));

        CompletableFuture<String> future = new CompletableFuture<>();
        webClient.postAbs(url)
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(body, ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                        return;
                    }
                    try {
                        if (ar.result().statusCode() < 200 || ar.result().statusCode() >= 300) {
                            throw new IllegalStateException("LLM provider returned HTTP " + ar.result().statusCode());
                        }
                        JsonObject response = ar.result().bodyAsJsonObject();
                        JsonObject message = response == null ? null : response.getJsonObject("message");
                        String content = message == null ? null : message.getString("content");
                        if (content == null) {
                            throw new IllegalStateException("Ollama response has no message content");
                        }
                        future.complete(content);
                    } catch (RuntimeException e) {
                        future.completeExceptionally(e);
                    }
                });

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call failed", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("LLM call failed", e.getCause());
        }
    }
}
