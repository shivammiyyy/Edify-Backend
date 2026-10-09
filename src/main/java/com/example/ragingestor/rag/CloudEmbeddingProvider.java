package com.example.ragingestor.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "cloud")
public class CloudEmbeddingProvider implements EmbeddingProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;

    public CloudEmbeddingProvider(ObjectMapper objectMapper,
                                  @Value("${app.embeddings.url}") String endpoint,
                                  @Value("${app.embeddings.model}") String model,
                                  @Value("${app.embeddings.api-key:}") String apiKey,
                                  @Value("${app.embeddings.timeout:PT60S}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
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
        var payload = objectMapper.createObjectNode().put("model", model);
        var input = payload.putArray("input");
        texts.forEach(input::add);
        try {
            JsonNode response = restClient.post().uri(endpoint)
                    .headers(headers -> {
                        if (!apiKey.isBlank()) {
                            headers.setBearerAuth(apiKey);
                        }
                    })
                    .body(payload)
                    .retrieve().body(JsonNode.class);
            JsonNode data = response == null ? null : response.get("data");
            if (data == null || !data.isArray() || data.size() != texts.size()) {
                throw new IllegalStateException("Cloud embedding response has an unexpected number of vectors");
            }
            List<float[]> embeddings = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode vectorNode = item.get("embedding");
                if (vectorNode == null || !vectorNode.isArray()) {
                    throw new IllegalStateException("Cloud embedding response contains no vector");
                }
                float[] vector = new float[vectorNode.size()];
                for (int i = 0; i < vector.length; i++) {
                    JsonNode value = vectorNode.get(i);
                    if (!value.isNumber() || !Float.isFinite(value.floatValue())) {
                        throw new IllegalStateException("Cloud embedding response contains an invalid value");
                    }
                    vector[i] = value.floatValue();
                }
                embeddings.add(vector);
            }
            return List.copyOf(embeddings);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            throw new IllegalStateException("Cloud embedding request failed with HTTP " + e.getStatusCode().value(), e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cloud embedding request failed", e);
        }
    }

    @Override
    public String modelId() {
        return model;
    }
}
