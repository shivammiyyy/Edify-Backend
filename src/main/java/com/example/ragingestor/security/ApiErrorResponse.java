package com.example.ragingestor.security;

import java.time.Instant;

public record ApiErrorResponse(Instant timestamp, int status, String code, String message, String correlationId) {
    public static ApiErrorResponse of(int status, String code, String message) {
        return new ApiErrorResponse(Instant.now(), status, code, message,
                org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
