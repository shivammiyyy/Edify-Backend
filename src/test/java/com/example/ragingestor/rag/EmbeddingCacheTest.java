package com.example.ragingestor.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingCacheTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final EmbeddingCache cache = new EmbeddingCache(redis, new ObjectMapper(), Duration.ofHours(1));

    @Test
    void returnsCachedVectorWithoutCallingProvider() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyCollection())).thenReturn(List.of("[0.25,0.5]"));
        EmbeddingProvider provider = provider();

        float[] vector = cache.embed(provider, "policy text");

        assertArrayEquals(new float[]{0.25f, 0.5f}, vector);
        verify(provider, never()).embedBatch(anyList());
    }

    @Test
    void computesAndCachesMissingVector() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.multiGet(anyCollection())).thenReturn(Collections.singletonList(null));
        EmbeddingProvider provider = provider();

        float[] vector = cache.embed(provider, "policy text");

        assertArrayEquals(new float[]{0.1f, 0.2f}, vector);
        verify(values).set(anyString(), eq("[0.1,0.2]"), eq(Duration.ofHours(1)));
    }

    private static EmbeddingProvider provider() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        when(provider.modelId()).thenReturn("test-model");
        when(provider.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        return provider;
    }
}
