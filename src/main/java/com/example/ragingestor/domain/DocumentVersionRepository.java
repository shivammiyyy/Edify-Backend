package com.example.ragingestor.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findByDocumentIdAndContentHash(UUID documentId, String contentHash);

    Optional<DocumentVersion> findTopByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    @Query("""
            select d from DocumentMeta d
            where d.uploadedBy = :uploadedBy
              and ((:folderId is null and d.folderId is null) or d.folderId = :folderId)
              and d.status <> :deletedStatus
              and d.status <> :failedStatus
              and exists (
                  select v.id from DocumentVersion v
                  where v.documentId = d.id and v.contentHash = :contentHash
              )
            """)
    List<DocumentMeta> findDuplicates(@Param("uploadedBy") UUID uploadedBy,
                                      @Param("folderId") UUID folderId,
                                      @Param("contentHash") String contentHash,
                                      @Param("deletedStatus") DocumentMeta.IngestionStatus deletedStatus,
                                      @Param("failedStatus") DocumentMeta.IngestionStatus failedStatus);
}
