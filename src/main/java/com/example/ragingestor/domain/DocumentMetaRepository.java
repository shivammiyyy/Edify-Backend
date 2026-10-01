package com.example.ragingestor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentMetaRepository extends JpaRepository<DocumentMeta, UUID> {

    List<DocumentMeta> findByFolderId(UUID folderId);

    List<DocumentMeta> findByFolderIdIsNull();
}
