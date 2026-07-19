package com.Edify.Backend.document.entity;


import com.Edify.Backend.Domain.DocumentStatus;
import com.Edify.Backend.User.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "document",
        indexes = {
                @Index(name = "idx_document_owner", columnList = "owner_id"),

                @Index(name = "idx_document_status", columnList = "status"),

                @Index(name = "idx_document_title", columnList = "title")
        }
)
public class Document {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_filename",nullable = false, length = 255)
    private String storedFileName;

    @Column(name = "storage_path", nullable = false, length = 255)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "owner_id",
            nullable = false
    )
    private User  owner;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @UpdateTimestamp
    @Column(name="updated_at")
    private LocalDateTime updatedAt;

}
