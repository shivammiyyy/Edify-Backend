package com.Edify.Backend.document.repository;

import com.Edify.Backend.Domain.DocumentStatus;
import com.Edify.Backend.User.entity.User;
import com.Edify.Backend.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findAllByOwner(User owner, Pageable pageable);

    Page<Document> findAllByOwnerAndStatus(User owner, DocumentStatus status, Pageable pageable);

    Page<Document> findByOwnerAndTitleContainingIgnoreCase(User owner, String title, Pageable pageable);

    boolean existsByIdAndOwner(UUID id, User owner);

    List<Document> findAllByStoragePath(String storagePath);
}
