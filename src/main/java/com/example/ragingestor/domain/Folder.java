package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A Drive-style folder. {@code parentId == null} means the folder lives at the workspace
 * root. Folders form a tree via {@code parentId} self-reference; there is no physical
 * filesystem involved, this is purely an organizational grouping for {@link DocumentMeta}.
 */
@Entity
@Table(name = "folder")
@Getter
@Setter
@NoArgsConstructor
public class Folder {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Null for a root-level folder. */
    private UUID parentId;

    @Column(nullable = false)
    private UUID createdBy;

    /** Minimum role needed to see this folder and anything inside it in listings. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role minRoleRequired = Role.READ;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted = false;
}
