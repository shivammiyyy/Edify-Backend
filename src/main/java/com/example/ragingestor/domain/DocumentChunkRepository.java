package com.example.ragingestor.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
    List<DocumentChunk> findByDocumentIdAndDeletedFalse(UUID documentId);

    long countByDocumentIdAndDeletedFalse(UUID documentId);

    long countByDocumentIdAndVersionIdAndDeletedFalse(UUID documentId, UUID versionId);

    @Modifying
    @Transactional
    @Query("delete from DocumentChunk c where c.documentId = :documentId and c.versionId = :versionId")
    void deleteByDocumentIdAndVersionId(@Param("documentId") UUID documentId,
                                        @Param("versionId") UUID versionId);
}
