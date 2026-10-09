package com.example.ragingestor.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RedisRateLimitService {

    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], 60); end; "
                    + "return count", Long.class);

    private final StringRedisTemplate redis;
    private final int documentsPerMinute;
    private final int messagesPerMinute;

    public RedisRateLimitService(StringRedisTemplate redis,
                                 @Value("${app.rate-limit.documents-per-minute:10}") int documentsPerMinute,
                                 @Value("${app.rate-limit.messages-per-minute:20}") int messagesPerMinute) {
        this.redis = redis;
        this.documentsPerMinute = documentsPerMinute;
        this.messagesPerMinute = messagesPerMinute;
    }

    public void checkDocumentUpload(UUID userId) {
        check("documents", userId, documentsPerMinute);
    }

    public void checkConversationMessage(UUID userId) {
        check("messages", userId, messagesPerMinute);
    }

    private void check(String operation, UUID userId, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "edify:rate:" + operation + ":" + userId + ":" + minute;
        Long count = redis.execute(INCREMENT_WINDOW, List.of(key));
        if (count == null) {
            throw new IllegalStateException("Redis rate limiter returned no counter value");
        }
        if (count > limit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded; try again shortly");
        }
    }
}
