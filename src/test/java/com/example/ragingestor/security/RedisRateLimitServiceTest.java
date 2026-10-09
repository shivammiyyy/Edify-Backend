package com.example.ragingestor.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisRateLimitServiceTest {

    @Test
    void rejectsCallsAfterThePerUserWindowLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenReturn(1L, 2L);
        RedisRateLimitService service = new RedisRateLimitService(redis, 1, 1);
        UUID userId = UUID.randomUUID();

        service.checkDocumentUpload(userId);
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.checkDocumentUpload(userId));

        assertEquals(429, exception.getStatusCode().value());
    }
}
