package com.example.ragingestor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {
    List<IngestionJob> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    java.util.Optional<IngestionJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    java.util.Optional<IngestionJob> findFirstByDocumentVersionIdOrderByCreatedAtDesc(UUID documentVersionId);
}
