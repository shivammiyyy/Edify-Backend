package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for one chunk. The actual embedding vector is written/read straight through
 * JDBC (see ChunkVectorRepository) because mapping pgvector's `vector` column type into
 * Hibernate cleanly needs an extra type-descriptor dependency; keeping it out of the JPA
 * entity avoids that coupling while the similarity search still runs natively in Postgres.
 */
@Entity
@Table(name = "document_chunk")
@Getter
@Setter
@NoArgsConstructor
public class DocumentChunk {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(name = "version_id")
    private UUID versionId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role minRoleRequired = Role.READ;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted = false;
}
