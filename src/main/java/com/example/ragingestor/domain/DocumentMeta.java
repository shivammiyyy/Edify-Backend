package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_meta")
@Getter
@Setter
@NoArgsConstructor
public class DocumentMeta {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private UUID uploadedBy;

    /** Null means the document lives at the workspace root, like Drive's "My Drive". */
    private UUID folderId;

    /** Minimum role needed to have this document's chunks show up in RAG search results. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role minRoleRequired = Role.READ;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStatus status = IngestionStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    public enum IngestionStatus { PENDING, CHUNKING, EMBEDDING, READY, FAILED, DELETED }
}
