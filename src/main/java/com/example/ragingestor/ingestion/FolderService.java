package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.DocumentMetaRepository;
import com.example.ragingestor.domain.Folder;
import com.example.ragingestor.domain.FolderRepository;
import com.example.ragingestor.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Drive-style folder tree management. Folders are a pure organizational layer over
 * {@link DocumentMeta}; deleting/renaming/moving a folder never touches chunks or vectors.
 */
@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final DocumentMetaRepository documentMetaRepository;

    public FolderService(FolderRepository folderRepository, DocumentMetaRepository documentMetaRepository) {
        this.folderRepository = folderRepository;
        this.documentMetaRepository = documentMetaRepository;
    }

    public record FolderContents(List<Folder> folders, List<DocumentMeta> documents, List<Folder> breadcrumbs) {}

    public FolderContents contents(UUID folderId, Role viewerRole) {
        if (folderId != null) {
            folderRepository.findById(folderId)
                    .filter(folder -> !folder.isDeleted())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found"));
        }
        List<Folder> folders = (folderId == null
                ? folderRepository.findByParentIdIsNullAndDeletedFalse()
                : folderRepository.findByParentIdAndDeletedFalse(folderId)).stream()
                .filter(folder -> viewerRole.atLeast(folder.getMinRoleRequired()))
                .toList();
        List<DocumentMeta> documents = (folderId == null
                ? documentMetaRepository.findByFolderIdIsNull()
                : documentMetaRepository.findByFolderId(folderId)).stream()
                .filter(document -> document.getStatus() != DocumentMeta.IngestionStatus.DELETED)
                .filter(document -> viewerRole.atLeast(document.getMinRoleRequired()))
                .toList();
        return new FolderContents(folders, documents, breadcrumbs(folderId));
    }

    private List<Folder> breadcrumbs(UUID folderId) {
        List<Folder> chain = new ArrayList<>();
        UUID current = folderId;
        while (current != null) {
            Folder folder = folderRepository.findById(current)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found"));
            chain.add(0, folder);
            current = folder.getParentId();
        }
        return chain;
    }

    @Transactional
    public Folder create(String name, UUID parentId, Role minRoleRequired, UUID actorUserId) {
        if (parentId != null) {
            folderRepository.findById(parentId)
                    .filter(folder -> !folder.isDeleted())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "parent folder not found"));
        }
        Folder folder = new Folder();
        folder.setName(name);
        folder.setParentId(parentId);
        folder.setCreatedBy(actorUserId);
        folder.setMinRoleRequired(minRoleRequired != null ? minRoleRequired : Role.READ);
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder rename(UUID folderId, String name, Role minRoleRequired) {
        Folder folder = folderRepository.findById(folderId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found"));
        if (name != null && !name.isBlank()) folder.setName(name);
        if (minRoleRequired != null) folder.setMinRoleRequired(minRoleRequired);
        folder.setUpdatedAt(Instant.now());
        return folderRepository.save(folder);
    }

    @Transactional
    public Folder move(UUID folderId, UUID newParentId) {
        Folder folder = folderRepository.findById(folderId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found"));
        if (folderId.equals(newParentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a folder cannot be its own parent");
        }
        if (newParentId != null && isDescendant(newParentId, folderId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot move a folder into its own subfolder");
        }
        folder.setParentId(newParentId);
        folder.setUpdatedAt(Instant.now());
        return folderRepository.save(folder);
    }

    /** True if {@code candidateAncestorId} is {@code folderId} or one of its descendants. */
    private boolean isDescendant(UUID candidateAncestorId, UUID folderId) {
        UUID current = candidateAncestorId;
        while (current != null) {
            if (current.equals(folderId)) return true;
            current = folderRepository.findById(current).map(Folder::getParentId).orElse(null);
        }
        return false;
    }

    @Transactional
    public void delete(UUID folderId) {
        Folder folder = folderRepository.findById(folderId)
                .filter(existing -> !existing.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "folder not found"));
        boolean hasSubfolders = folderRepository.existsByParentIdAndDeletedFalse(folderId);
        boolean hasDocuments = !documentMetaRepository.findByFolderId(folderId).stream()
                .filter(document -> document.getStatus() != DocumentMeta.IngestionStatus.DELETED)
                .toList().isEmpty();
        if (hasSubfolders || hasDocuments) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "folder is not empty");
        }
        folder.setDeleted(true);
        folder.setUpdatedAt(Instant.now());
        folderRepository.save(folder);
    }
}
