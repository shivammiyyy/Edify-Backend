package com.example.ragingestor.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version", uniqueConstraints = {
        @UniqueConstraint(name = "uq_document_version_number", columnNames = {"document_id", "version_number"}),
        @UniqueConstraint(name = "uq_document_version_hash", columnNames = {"document_id", "content_hash"})
})
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 1024)
    private String storageKey;

    @Column(nullable = false, length = 255)
    private String contentType = "application/octet-stream";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionStatus status = VersionStatus.UPLOADED;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    public enum VersionStatus { UPLOADED, READY, FAILED }
}
