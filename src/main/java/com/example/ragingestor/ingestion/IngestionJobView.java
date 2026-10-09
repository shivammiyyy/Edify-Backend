package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.IngestionJob;

import java.time.Instant;
import java.util.UUID;

public record IngestionJobView(UUID jobId, UUID documentId, UUID documentVersionId,
                               IngestionJob.JobStatus status, String errorMessage,
                               Instant createdAt, Instant startedAt, Instant completedAt) {
    public static IngestionJobView of(IngestionJob job) {
        return new IngestionJobView(job.getId(), job.getDocumentId(), job.getDocumentVersionId(),
                job.getStatus(), job.getErrorMessage(), job.getCreatedAt(), job.getStartedAt(),
                job.getCompletedAt());
    }
}
