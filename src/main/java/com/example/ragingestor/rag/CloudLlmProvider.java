package com.example.ragingestor.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cloud")
public class CloudLlmProvider implements LlmProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String model;
    private final String apiKey;

    public CloudLlmProvider(ObjectMapper objectMapper,
                            @Value("${app.llm.url}") String endpoint,
                            @Value("${app.llm.model}") String model,
                            @Value("${app.llm.api-key:}") String apiKey,
                            @Value("${app.llm.timeout:PT90S}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.streamingClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    private final Duration timeout;
    private final HttpClient streamingClient;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        ObjectNode payload = objectMapper.createObjectNode().put("model", model);
        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        try {
            JsonNode response = restClient.post().uri(endpoint)
                    .headers(headers -> {
                        if (!apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .body(payload)
                    .retrieve().body(JsonNode.class);
            JsonNode content = response == null ? null : response.at("/choices/0/message/content");
            if (content == null || !content.isTextual()) {
                throw new IllegalStateException("Cloud LLM response has no completion content");
            }
            return content.textValue();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new IllegalStateException("Cloud LLM request failed with HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cloud LLM request failed", e);
        }
    }

    @Override
    public void stream(String systemPrompt, String userPrompt, Consumer<String> tokenConsumer) {
        ObjectNode payload = objectMapper.createObjectNode().put("model", model).put("stream", true);
        ArrayNode messages = payload.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (!apiKey.isBlank()) requestBuilder.header("Authorization", "Bearer " + apiKey);
            HttpResponse<Stream<String>> response = streamingClient.send(
                    requestBuilder.build(), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IllegalStateException("Cloud LLM request failed with HTTP " + response.statusCode());
            }
            try (Stream<String> lines = response.body()) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.equals("[DONE]")) break;
                    JsonNode event = objectMapper.readTree(data);
                    JsonNode content = event.at("/choices/0/delta/content");
                    if (content.isTextual() && !content.textValue().isEmpty()) {
                        tokenConsumer.accept(content.textValue());
                    }
                    JsonNode error = event.get("error");
                    if (error != null) throw new IllegalStateException("Cloud LLM stream returned an error");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cloud LLM stream was interrupted", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cloud LLM stream failed", e);
        }
    }
}
