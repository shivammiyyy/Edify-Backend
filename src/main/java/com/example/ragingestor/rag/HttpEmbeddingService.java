package com.example.ragingestor.rag;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Calls Ollama's /api/embeddings endpoint. That endpoint accepts one prompt per
 * request, so a batch is sent sequentially to avoid overwhelming a local model.
 */
@Service
public class HttpEmbeddingService implements EmbeddingService {

    private final WebClient webClient;
    private final String embeddingsUrl;
    private final String model;

    public HttpEmbeddingService(WebClient webClient,
                                 @Value("${app.embeddings.url}") String embeddingsUrl,
                                 @Value("${app.embeddings.model}") String model) {
        this.webClient = webClient;
        this.embeddingsUrl = embeddingsUrl;
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embedOne(text));
        }
        return List.copyOf(vectors);
    }

    private float[] embedOne(String text) {
        CompletableFuture<float[]> future = new CompletableFuture<>();
        webClient.postAbs(embeddingsUrl)
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(new JsonObject().put("model", model).put("prompt", text), ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                        return;
                    }
                    try {
                        if (ar.result().statusCode() < 200 || ar.result().statusCode() >= 300) {
                            throw new IllegalStateException("Ollama embedding endpoint returned HTTP "
                                    + ar.result().statusCode());
                        }
                        JsonObject response = ar.result().bodyAsJsonObject();
                        if (response == null || response.getJsonArray("embedding") == null) {
                            throw new IllegalStateException("Ollama response has no embedding vector");
                        }
                        var values = response.getJsonArray("embedding");
                        float[] vector = new float[values.size()];
                        for (int i = 0; i < values.size(); i++) {
                            Number value = values.getNumber(i);
                            if (value == null || !Float.isFinite(value.floatValue())) {
                                throw new IllegalStateException("Ollama returned an invalid embedding value");
                            }
                            vector[i] = value.floatValue();
                        }
                        future.complete(vector);
                    } catch (RuntimeException e) {
                        future.completeExceptionally(e);
                    }
                });

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama embedding request was interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Ollama embedding request failed", e.getCause());
        }
    }
}
