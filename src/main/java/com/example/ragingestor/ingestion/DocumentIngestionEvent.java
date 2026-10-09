package com.example.ragingestor.ingestion;

import java.time.Instant;
import java.util.UUID;

public record DocumentIngestionEvent(
        UUID eventId,
        UUID documentId,
        UUID documentVersionId,
        UUID jobId,
        UUID tenantId,
        String correlationId,
        Instant timestamp
) {}
