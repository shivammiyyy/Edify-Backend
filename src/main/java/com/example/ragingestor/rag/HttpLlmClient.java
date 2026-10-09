package com.example.ragingestor.rag;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class HttpLlmClient implements LlmClient {

    private final WebClient webClient;
    private final String url;
    private final String model;
    private final HttpClient streamingClient;
    private final Duration timeout;

    public HttpLlmClient(WebClient webClient,
                          @Value("${app.llm.url}") String url,
                          @Value("${app.llm.model}") String model,
                          @Value("${app.llm.timeout:PT90S}") Duration timeout) {
        this.webClient = webClient;
        this.url = url;
        this.model = model;
        this.timeout = timeout;
        this.streamingClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
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

    @Override
    public void stream(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        JsonObject body = new JsonObject()
                .put("model", model)
                .put("stream", true)
                .put("think", false)
                .put("messages", new JsonArray()
                        .add(new JsonObject().put("role", "system").put("content", systemPrompt))
                        .add(new JsonObject().put("role", "user").put("content", userPrompt)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.encode()))
                .build();
        try {
            HttpResponse<Stream<String>> response = streamingClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("LLM provider returned HTTP " + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (line.isBlank()) continue;
                    JsonObject item = new JsonObject(line);
                    JsonObject message = item.getJsonObject("message");
                    String content = message == null ? null : message.getString("content");
                    if (content != null && !content.isEmpty()) tokenConsumer.accept(content);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM stream was interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("LLM stream failed", e);
        }
    }
}
