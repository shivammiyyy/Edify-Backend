package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.*;
import com.example.ragingestor.ingestion.dto.IngestionDtos.CreateDocumentCommand;
import com.example.ragingestor.ingestion.dto.IngestionDtos.UpdateDocumentCommand;
import com.example.ragingestor.rag.EmbeddingService;
import com.example.ragingestor.rag.EmbeddingCache;
import com.example.ragingestor.security.Role;
import com.example.ragingestor.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DocumentIngestionServiceTest {

    private final DocumentMetaRepository documents = mock(DocumentMetaRepository.class);
    private final DocumentChunkRepository chunks = mock(DocumentChunkRepository.class);
    private final ChunkVectorRepository vectors = mock(ChunkVectorRepository.class);
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final EmbeddingCache embeddingCache = mock(EmbeddingCache.class);
    private final DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);

    private DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(documents, chunks, vectors, new ChunkingService(),
                embeddings, embeddingCache, versions, storage, 2);
        when(documents.save(any(DocumentMeta.class))).thenAnswer(invocation -> {
            DocumentMeta document = invocation.getArgument(0);
            if (document.getId() == null) document.setId(UUID.randomUUID());
            return document;
        });
        when(versions.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            if (version.getId() == null) version.setId(UUID.randomUUID());
            return version;
        });
        when(chunks.save(any(DocumentChunk.class))).thenAnswer(invocation -> {
            DocumentChunk chunk = invocation.getArgument(0);
            if (chunk.getId() == null) chunk.setId(UUID.randomUUID());
            return chunk;
        });
        when(embeddings.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(embeddingCache.embedBatch(eq(embeddings), anyList())).thenAnswer(invocation ->
                embeddings.embedBatch(invocation.getArgument(1)));
    }

    @Test
    void persistsOriginalAndCreatesAnActiveVersionWithAContentHash() throws Exception {
        byte[] original = "policy text".getBytes(StandardCharsets.UTF_8);
        UUID uploader = UUID.randomUUID();
        when(versions.findDuplicates(eq(uploader), isNull(), anyString(), eq(DocumentMeta.IngestionStatus.DELETED),
                eq(DocumentMeta.IngestionStatus.FAILED)))
                .thenReturn(List.of());

        var result = service.create(new CreateDocumentCommand("policy.txt", "policy text", Role.READ, uploader,
                null, "trace-1", original, "text/plain"));

        assertEquals("READY", result.status());
        assertEquals(1, result.chunkCount());
        verify(storage).put(argThat(key -> key.matches("documents/.+/.+/original")), eq(original), eq("text/plain"));

        var savedVersion = org.mockito.ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versions, atLeastOnce()).save(savedVersion.capture());
        DocumentVersion activeVersion = savedVersion.getAllValues().get(savedVersion.getAllValues().size() - 1);
        assertEquals(1, activeVersion.getVersionNumber());
        assertEquals(sha256(original), activeVersion.getContentHash());
        assertEquals(DocumentVersion.VersionStatus.READY, activeVersion.getStatus());
        assertNotNull(activeVersion.getId());

        var savedChunk = org.mockito.ArgumentCaptor.forClass(DocumentChunk.class);
        verify(chunks).save(savedChunk.capture());
        assertEquals(activeVersion.getId(), savedChunk.getValue().getVersionId());
    }

    @Test
    void skipsProcessingForDuplicateContentInTheSameWorkspaceLocation() {
        UUID uploader = UUID.randomUUID();
        DocumentMeta existing = new DocumentMeta();
        existing.setId(UUID.randomUUID());
        existing.setStatus(DocumentMeta.IngestionStatus.READY);
        when(versions.findDuplicates(eq(uploader), isNull(), anyString(), eq(DocumentMeta.IngestionStatus.DELETED),
                eq(DocumentMeta.IngestionStatus.FAILED)))
                .thenReturn(List.of(existing));
        when(chunks.countByDocumentIdAndDeletedFalse(existing.getId())).thenReturn(2L);

        var result = service.create(new CreateDocumentCommand("policy.txt", "policy text", Role.READ, uploader,
                null, "trace-2", null, "text/plain"));

        assertEquals(existing.getId(), result.documentId());
        assertEquals(2, result.chunkCount());
        assertEquals("identical content already exists", result.message());
        verifyNoInteractions(storage);
        verifyNoInteractions(embeddings);
    }

    @Test
    void skipsReprocessingWhenActiveVersionContentIsUnchanged() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String content = "unchanged text";
        DocumentMeta document = new DocumentMeta();
        document.setId(documentId);
        document.setActiveVersionId(versionId);
        document.setStatus(DocumentMeta.IngestionStatus.READY);
        DocumentVersion active = new DocumentVersion();
        active.setId(versionId);
        active.setContentHash(sha256(content.getBytes(StandardCharsets.UTF_8)));
        when(documents.findById(documentId)).thenReturn(Optional.of(document));
        when(versions.findById(versionId)).thenReturn(Optional.of(active));
        when(chunks.countByDocumentIdAndVersionIdAndDeletedFalse(documentId, versionId)).thenReturn(1L);

        var result = service.update(new UpdateDocumentCommand(documentId, content, actorId, "trace-3",
                "policy.txt", content.getBytes(StandardCharsets.UTF_8), "text/plain"));

        assertEquals("identical content is already active", result.message());
        assertEquals(1, result.chunkCount());
        verifyNoInteractions(storage);
        verifyNoInteractions(embeddings);
    }

    @Test
    void updatesCreateASeparateVersionAndKeepItsChunksTiedToThatVersion() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID oldVersionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        byte[] replacement = "replacement policy".getBytes(StandardCharsets.UTF_8);
        DocumentMeta document = new DocumentMeta();
        document.setId(documentId);
        document.setActiveVersionId(oldVersionId);
        document.setStatus(DocumentMeta.IngestionStatus.READY);
        DocumentVersion oldVersion = new DocumentVersion();
        oldVersion.setId(oldVersionId);
        oldVersion.setVersionNumber(1);
        oldVersion.setContentHash("old-content-hash");
        when(documents.findById(documentId)).thenReturn(Optional.of(document));
        when(versions.findById(oldVersionId)).thenReturn(Optional.of(oldVersion));
        when(versions.findByDocumentIdAndContentHash(documentId, sha256(replacement))).thenReturn(Optional.empty());
        when(versions.findTopByDocumentIdOrderByVersionNumberDesc(documentId)).thenReturn(Optional.of(oldVersion));

        var result = service.update(new UpdateDocumentCommand(documentId, "replacement policy", actorId, "trace-4",
                "policy.txt", replacement, "text/plain"));

        assertEquals("READY", result.status());
        assertEquals(1, result.chunkCount());
        assertNotEquals(oldVersionId, document.getActiveVersionId());
        verify(storage).put(argThat(key -> key.startsWith("documents/" + documentId + "/")),
                eq(replacement), eq("text/plain"));

        var savedChunk = org.mockito.ArgumentCaptor.forClass(DocumentChunk.class);
        verify(chunks).save(savedChunk.capture());
        assertEquals(document.getActiveVersionId(), savedChunk.getValue().getVersionId());
        assertEquals("replacement policy", savedChunk.getValue().getContent());

        var savedVersions = org.mockito.ArgumentCaptor.forClass(DocumentVersion.class);
        verify(versions, atLeastOnce()).save(savedVersions.capture());
        DocumentVersion nextVersion = savedVersions.getValue();
        assertEquals(2, nextVersion.getVersionNumber());
        assertEquals(sha256(replacement), nextVersion.getContentHash());
        assertEquals(DocumentVersion.VersionStatus.READY, nextVersion.getStatus());
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
