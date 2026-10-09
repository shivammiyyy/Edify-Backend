package com.example.ragingestor.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class EmbeddingCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public EmbeddingCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                          @Value("${app.cache.embedding-ttl:PT168H}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    public float[] embed(EmbeddingProvider provider, String text) {
        return embedBatch(provider, List.of(text)).get(0);
    }

    public List<float[]> embedBatch(EmbeddingProvider provider, List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }

        List<String> keys = texts.stream().map(text -> key(provider.modelId(), text)).toList();
        List<String> cached = redis.opsForValue().multiGet(keys);
        List<float[]> result = new ArrayList<>(java.util.Collections.nCopies(texts.size(), null));
        List<String> missingTexts = new ArrayList<>();
        List<Integer> missingIndexes = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            String value = cached == null ? null : cached.get(i);
            if (value == null) {
                missingTexts.add(texts.get(i));
                missingIndexes.add(i);
            } else {
                result.set(i, validate(decode(value)));
            }
        }

        if (!missingTexts.isEmpty()) {
            List<float[]> computed = provider.embedBatch(missingTexts);
            if (computed.size() != missingTexts.size()) {
                throw new IllegalStateException("embedding provider returned an unexpected number of vectors");
            }
            for (int i = 0; i < computed.size(); i++) {
                float[] vector = validate(computed.get(i));
                result.set(missingIndexes.get(i), vector);
                redis.opsForValue().set(keys.get(missingIndexes.get(i)), encode(vector), ttl);
            }
        }
        return List.copyOf(result);
    }

    private String key(String model, String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return "edify:embedding:" + model + ":" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String encode(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize cached embedding", e);
        }
    }

    private float[] decode(String value) {
        try {
            return objectMapper.readValue(value, float[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize cached embedding", e);
        }
    }

    private static float[] validate(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("embedding vector must not be empty");
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("embedding vector contains an invalid value");
            }
        }
        return vector;
    }
}
