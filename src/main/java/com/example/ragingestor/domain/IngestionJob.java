package com.example.ragingestor.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_job")
@Getter
@Setter
@NoArgsConstructor
public class IngestionJob {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private UUID documentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.UPLOADED;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 128)
    private String correlationId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant startedAt;
    private Instant completedAt;

    public enum JobStatus { UPLOADED, PARSING, CHUNKING, EMBEDDING, INDEXING, READY, FAILED }
}
