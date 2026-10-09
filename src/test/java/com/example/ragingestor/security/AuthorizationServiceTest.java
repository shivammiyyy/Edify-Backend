package com.example.ragingestor.security;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.Folder;
import com.example.ragingestor.domain.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizationServiceTest {

    private final FolderRepository folders = mock(FolderRepository.class);
    private final AuthorizationService authorization = new AuthorizationService(folders);
    private final UUID parentId = UUID.randomUUID();
    private final UUID childId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Folder parent = folder(parentId, null, Role.CUSTODIAN, false);
        Folder child = folder(childId, parentId, Role.READ, false);
        Map<UUID, Folder> tree = Map.of(parentId, parent, childId, child);
        when(folders.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(tree.get(invocation.getArgument(0))));
    }

    @Test
    void folderVisibilityIncludesEveryAncestor() {
        assertFalse(authorization.canReadFolder(childId, Role.READ));
        assertTrue(authorization.canReadFolder(childId, Role.CUSTODIAN));
    }

    @Test
    void documentVisibilityIncludesItsFolderAncestors() {
        DocumentMeta document = document(childId, Role.READ, DocumentMeta.IngestionStatus.READY);

        assertFalse(authorization.canReadDocument(document, Role.WRITE));
        assertTrue(authorization.canReadDocument(document, Role.CUSTODIAN));
    }

    @Test
    void writesRequireWriteRoleAndVisibleNonDeletedDocument() {
        DocumentMeta document = document(null, Role.READ, DocumentMeta.IngestionStatus.READY);
        DocumentMeta deleted = document(null, Role.READ, DocumentMeta.IngestionStatus.DELETED);

        assertFalse(authorization.canWriteDocument(document, Role.READ));
        assertTrue(authorization.canWriteDocument(document, Role.WRITE));
        assertFalse(authorization.canDeleteDocument(deleted, Role.ADMIN));
    }

    private static Folder folder(UUID id, UUID parentId, Role minimumRole, boolean deleted) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setParentId(parentId);
        folder.setMinRoleRequired(minimumRole);
        folder.setDeleted(deleted);
        return folder;
    }

    private static DocumentMeta document(UUID folderId, Role minimumRole, DocumentMeta.IngestionStatus status) {
        DocumentMeta document = new DocumentMeta();
        document.setFolderId(folderId);
        document.setMinRoleRequired(minimumRole);
        document.setStatus(status);
        return document;
    }
}
