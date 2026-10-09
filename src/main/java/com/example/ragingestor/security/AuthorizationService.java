package com.example.ragingestor.security;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.Folder;
import com.example.ragingestor.domain.FolderRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthorizationService {

    private final FolderRepository folderRepository;

    public AuthorizationService(FolderRepository folderRepository) {
        this.folderRepository = folderRepository;
    }

    public boolean canReadFolder(UUID folderId, Role role) {
        return hasFolderAccess(folderId, role);
    }

    public boolean canWriteFolder(UUID folderId, Role role) {
        return role.atLeast(Role.WRITE) && hasFolderAccess(folderId, role);
    }

    public boolean canReadDocument(DocumentMeta document, Role role) {
        return document.getStatus() != DocumentMeta.IngestionStatus.DELETED
                && role.atLeast(document.getMinRoleRequired())
                && hasFolderAccess(document.getFolderId(), role);
    }

    public boolean canWriteDocument(DocumentMeta document, Role role) {
        return role.atLeast(Role.WRITE) && canReadDocument(document, role);
    }

    public boolean canDeleteDocument(DocumentMeta document, Role role) {
        return canWriteDocument(document, role);
    }

    public void requireReadFolder(UUID folderId, Role role) {
        if (!canReadFolder(folderId, role)) {
            throw new AccessDeniedException("You do not have access to this folder");
        }
    }

    public void requireWriteFolder(UUID folderId, Role role) {
        if (!canWriteFolder(folderId, role)) {
            throw new AccessDeniedException("You do not have permission to change this folder");
        }
    }

    public void requireReadDocument(DocumentMeta document, Role role) {
        if (!canReadDocument(document, role)) {
            throw new AccessDeniedException("You do not have access to this document");
        }
    }

    public void requireWriteDocument(DocumentMeta document, Role role) {
        if (!canWriteDocument(document, role)) {
            throw new AccessDeniedException("You do not have permission to change this document");
        }
    }

    public void requireDeleteDocument(DocumentMeta document, Role role) {
        if (!canDeleteDocument(document, role)) {
            throw new AccessDeniedException("You do not have permission to delete this document");
        }
    }

    private boolean hasFolderAccess(UUID folderId, Role role) {
        Set<UUID> visited = new HashSet<>();
        UUID currentId = folderId;
        while (currentId != null) {
            if (!visited.add(currentId)) {
                return false;
            }
            Folder folder = folderRepository.findById(currentId).orElse(null);
            if (folder == null || folder.isDeleted() || !role.atLeast(folder.getMinRoleRequired())) {
                return false;
            }
            currentId = folder.getParentId();
        }
        return true;
    }
}
