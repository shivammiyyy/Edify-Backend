package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
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

    @Column(name = "active_version_id")
    private UUID activeVersionId;

    @Column(length = 120)
    private String department;

    @Column(name = "document_type", length = 80)
    private String documentType;

    @Convert(converter = TagListConverter.class)
    @Column(name = "tags", nullable = false, length = 2048)
    private List<String> tags = List.of();

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

    public enum IngestionStatus { PENDING, UPLOADED, PARSING, CHUNKING, EMBEDDING, INDEXING, READY, FAILED, DELETED }
}
