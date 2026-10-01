package com.example.ragingestor.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findByParentIdAndDeletedFalse(UUID parentId);

    List<Folder> findByParentIdIsNullAndDeletedFalse();

    boolean existsByParentIdAndDeletedFalse(UUID parentId);
}
