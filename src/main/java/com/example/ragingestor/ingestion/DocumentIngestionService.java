package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.*;
import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import com.example.ragingestor.rag.EmbeddingProvider;
import com.example.ragingestor.rag.EmbeddingCache;
import com.example.ragingestor.security.Role;
import com.example.ragingestor.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private final DocumentMetaRepository documentMetaRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkVectorRepository chunkVectorRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingCache embeddingCache;
    private final DocumentVersionRepository documentVersionRepository;
    private final ObjectStorageService objectStorageService;
    private final int embeddingDimension;

    public DocumentIngestionService(DocumentMetaRepository documentMetaRepository,
                                     DocumentChunkRepository documentChunkRepository,
                                     ChunkVectorRepository chunkVectorRepository,
                                     ChunkingService chunkingService,
                                     EmbeddingProvider embeddingProvider,
                                     EmbeddingCache embeddingCache,
                                     DocumentVersionRepository documentVersionRepository,
                                     ObjectStorageService objectStorageService,
                                     @org.springframework.beans.factory.annotation.Value("${app.embeddings.dimension}") int embeddingDimension) {
        this.documentMetaRepository = documentMetaRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.chunkVectorRepository = chunkVectorRepository;
        this.chunkingService = chunkingService;
        this.embeddingProvider = embeddingProvider;
        this.embeddingCache = embeddingCache;
        this.documentVersionRepository = documentVersionRepository;
        this.objectStorageService = objectStorageService;
        this.embeddingDimension = embeddingDimension;
    }

    @Transactional
    public IngestionResult create(CreateDocumentCommand cmd) {
        byte[] originalFile = cmd.originalFile() == null
                ? cmd.content().getBytes(StandardCharsets.UTF_8)
                : cmd.originalFile();
        String contentHash = sha256(originalFile);
        var duplicate = documentVersionRepository.findDuplicates(cmd.actorUserId(), cmd.folderId(), contentHash,
                DocumentMeta.IngestionStatus.DELETED, DocumentMeta.IngestionStatus.FAILED).stream().findFirst();
        if (duplicate.isPresent()) {
            DocumentMeta existing = duplicate.get();
            long count = existing.getActiveVersionId() == null
                    ? documentChunkRepository.countByDocumentIdAndDeletedFalse(existing.getId())
                    : documentChunkRepository.countByDocumentIdAndVersionIdAndDeletedFalse(
                            existing.getId(), existing.getActiveVersionId());
            return new IngestionResult(existing.getId(),
                    Math.toIntExact(count),
                    existing.getStatus().name(), "identical content already exists");
        }

        DocumentMeta doc = new DocumentMeta();
        doc.setFilename(cmd.filename());
        doc.setUploadedBy(cmd.actorUserId());
        doc.setFolderId(cmd.folderId());
        doc.setMinRoleRequired(cmd.minRoleRequired() != null ? cmd.minRoleRequired() : Role.READ);
        doc.setStatus(DocumentMeta.IngestionStatus.CHUNKING);
        doc = documentMetaRepository.save(doc);

        String contentType = cmd.contentType() == null ? "text/plain" : cmd.contentType();
        DocumentVersion version = createVersion(doc, contentHash, cmd.actorUserId(), 1, contentType);
        String storageKey = storageKey(doc.getId(), version.getId());
        doc.setActiveVersionId(version.getId());
        int count = chunkAndEmbed(doc, version.getId(), cmd.content(), cmd.actorUserId());
        objectStorageService.put(storageKey, originalFile, contentType);
        version.setStorageKey(storageKey);
        version.setStatus(DocumentVersion.VersionStatus.READY);
        documentVersionRepository.save(version);
        doc.setStatus(DocumentMeta.IngestionStatus.READY);
        doc.setUpdatedAt(Instant.now());
        documentMetaRepository.save(doc);
        return new IngestionResult(doc.getId(), count, "READY", "document ingested");
    }

    @Transactional
    public IngestionResult update(UpdateDocumentCommand cmd) {
        DocumentMeta doc = documentMetaRepository.findById(cmd.documentId())
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + cmd.documentId()));

        byte[] originalFile = cmd.originalFile() == null
                ? cmd.newContent().getBytes(StandardCharsets.UTF_8)
                : cmd.originalFile();
        String contentType = cmd.contentType() == null ? "text/plain" : cmd.contentType();
        String contentHash = sha256(originalFile);
        DocumentVersion activeVersion = doc.getActiveVersionId() == null ? null
                : documentVersionRepository.findById(doc.getActiveVersionId()).orElse(null);
        if (activeVersion != null && activeVersion.getContentHash().equals(contentHash)) {
            if (cmd.filename() != null && !cmd.filename().equals(doc.getFilename())) {
                doc.setFilename(cmd.filename());
                doc.setUpdatedAt(Instant.now());
                documentMetaRepository.save(doc);
            }
            return new IngestionResult(doc.getId(),
                    Math.toIntExact(documentChunkRepository.countByDocumentIdAndVersionIdAndDeletedFalse(
                            doc.getId(), activeVersion.getId())),
                    doc.getStatus().name(), "identical content is already active");
        }

        var previousVersion = documentVersionRepository.findByDocumentIdAndContentHash(doc.getId(), contentHash);
        if (previousVersion.isPresent()) {
            doc.setActiveVersionId(previousVersion.get().getId());
            if (cmd.filename() != null) {
                doc.setFilename(cmd.filename());
            }
            doc.setStatus(DocumentMeta.IngestionStatus.READY);
            doc.setUpdatedAt(Instant.now());
            documentMetaRepository.save(doc);
            return new IngestionResult(doc.getId(),
                    Math.toIntExact(documentChunkRepository.countByDocumentIdAndVersionIdAndDeletedFalse(
                            doc.getId(), previousVersion.get().getId())),
                    "READY", "existing document version reactivated");
        }

        int nextVersion = documentVersionRepository.findTopByDocumentIdOrderByVersionNumberDesc(doc.getId())
                .map(DocumentVersion::getVersionNumber).orElse(0) + 1;
        DocumentVersion version = createVersion(doc, contentHash, cmd.actorUserId(), nextVersion, contentType);
        String storageKey = storageKey(doc.getId(), version.getId());

        doc.setStatus(DocumentMeta.IngestionStatus.CHUNKING);
        documentMetaRepository.save(doc);

        int count = chunkAndEmbed(doc, version.getId(), cmd.newContent(), cmd.actorUserId());
        objectStorageService.put(storageKey, originalFile, contentType);
        version.setStorageKey(storageKey);
        version.setStatus(DocumentVersion.VersionStatus.READY);
        documentVersionRepository.save(version);
        doc.setActiveVersionId(version.getId());
        if (cmd.filename() != null) {
            doc.setFilename(cmd.filename());
        }
        doc.setStatus(DocumentMeta.IngestionStatus.READY);
        doc.setUpdatedAt(Instant.now());
        documentMetaRepository.save(doc);
        return new IngestionResult(doc.getId(), count, "READY", "document updated");
    }

    @Transactional
    public IngestionResult delete(DeleteDocumentCommand cmd) {
        DocumentMeta doc = documentMetaRepository.findById(cmd.documentId())
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + cmd.documentId()));

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentIdAndDeletedFalse(doc.getId());
        for (DocumentChunk chunk : chunks) {
            chunk.setDeleted(true);
            documentChunkRepository.save(chunk);
            chunkVectorRepository.deleteEmbedding(chunk.getId());
        }
        doc.setStatus(DocumentMeta.IngestionStatus.DELETED);
        documentMetaRepository.save(doc);
        return new IngestionResult(doc.getId(), 0, "DELETED", "document deleted");
    }

    /** Pure metadata move: no re-chunking/re-embedding involved, so this bypasses the Vert.x
     * ingestion pipeline and just updates the folder pointer directly. */
    @Transactional
    public DocumentMeta move(UUID documentId, UUID folderId) {
        DocumentMeta doc = documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        doc.setFolderId(folderId);
        doc.setUpdatedAt(Instant.now());
        return documentMetaRepository.save(doc);
    }

    public List<DocumentVersion> versions(UUID documentId) {
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    @Transactional
    public void updateProcessingStatus(UUID documentId, DocumentMeta.IngestionStatus status) {
        DocumentMeta document = documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        document.setStatus(status);
        document.setUpdatedAt(Instant.now());
        documentMetaRepository.save(document);
    }

    @Transactional
    public void indexPreparedVersion(UUID documentId, UUID versionId, UUID actorUserId,
                                     List<String> textChunks, List<float[]> embeddings) {
        if (textChunks.size() != embeddings.size()) {
            throw new IllegalStateException("embedding provider returned a different number of vectors than chunks");
        }
        DocumentMeta document = documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new DocumentNotFoundException("document version not found: " + versionId));

        chunkVectorRepository.deleteEmbeddingsForVersion(versionId);
        documentChunkRepository.deleteByDocumentIdAndVersionId(documentId, versionId);
        for (int i = 0; i < textChunks.size(); i++) {
            if (embeddings.get(i).length != embeddingDimension) {
                throw new IllegalStateException("embedding dimension must be " + embeddingDimension
                        + " to match the database schema");
            }
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(documentId);
            chunk.setVersionId(versionId);
            chunk.setChunkIndex(i);
            chunk.setContent(textChunks.get(i));
            chunk.setMinRoleRequired(document.getMinRoleRequired());
            chunk.setCreatedBy(actorUserId);
            chunk = documentChunkRepository.saveAndFlush(chunk);
            chunkVectorRepository.upsertEmbedding(chunk.getId(), embeddings.get(i));
        }

        version.setStatus(DocumentVersion.VersionStatus.READY);
        documentVersionRepository.save(version);
        document.setActiveVersionId(versionId);
        document.setStatus(DocumentMeta.IngestionStatus.READY);
        document.setUpdatedAt(Instant.now());
        documentMetaRepository.save(document);
    }

    @Transactional
    public void markFailedVersion(UUID documentId, UUID versionId) {
        DocumentMeta document = documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        documentVersionRepository.findById(versionId).ifPresent(version -> {
            version.setStatus(DocumentVersion.VersionStatus.FAILED);
            documentVersionRepository.save(version);
        });
        document.setStatus(document.getActiveVersionId() != null
                && !document.getActiveVersionId().equals(versionId)
                ? DocumentMeta.IngestionStatus.READY
                : DocumentMeta.IngestionStatus.FAILED);
        document.setUpdatedAt(Instant.now());
        documentMetaRepository.save(document);
    }

    public byte[] download(UUID documentId) {
        DocumentMeta doc = documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        if (doc.getActiveVersionId() == null) {
            throw new DocumentNotFoundException("document has no stored original");
        }
        DocumentVersion version = documentVersionRepository.findById(doc.getActiveVersionId())
                .orElseThrow(() -> new DocumentNotFoundException("active document version not found"));
        return objectStorageService.get(version.getStorageKey());
    }

    private int chunkAndEmbed(DocumentMeta doc, UUID versionId, String content, UUID actorUserId) {
        List<String> textChunks = chunkingService.chunk(content);
        List<float[]> embeddings = embeddingCache.embedBatch(embeddingProvider, textChunks);
        if (embeddings.size() != textChunks.size()) {
            throw new IllegalStateException("embedding provider returned a different number of vectors than chunks");
        }

        for (int i = 0; i < textChunks.size(); i++) {
            if (embeddings.get(i).length != embeddingDimension) {
                throw new IllegalStateException("embedding dimension must be " + embeddingDimension
                        + " to match the database schema");
            }
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setVersionId(versionId);
            chunk.setChunkIndex(i);
            chunk.setContent(textChunks.get(i));
            chunk.setMinRoleRequired(doc.getMinRoleRequired());
            chunk.setCreatedBy(actorUserId);
            chunk = documentChunkRepository.saveAndFlush(chunk);
            chunkVectorRepository.upsertEmbedding(chunk.getId(), embeddings.get(i));
        }
        return textChunks.size();
    }

    public String downloadContentType(UUID documentId) {
        DocumentMeta doc = documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        if (doc.getActiveVersionId() == null) {
            return "text/plain";
        }
        return documentVersionRepository.findById(doc.getActiveVersionId())
                .map(DocumentVersion::getContentType)
                .orElseThrow(() -> new DocumentNotFoundException("active document version not found"));
    }

    private DocumentVersion createVersion(DocumentMeta doc, String contentHash, UUID actorUserId, int versionNumber,
                                          String contentType) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(doc.getId());
        version.setVersionNumber(versionNumber);
        version.setContentHash(contentHash);
        version.setStorageKey("pending");
        version.setContentType(contentType);
        version.setCreatedBy(actorUserId);
        return documentVersionRepository.save(version);
    }

    private static String storageKey(UUID documentId, UUID versionId) {
        return "documents/" + documentId + "/" + versionId + "/original";
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
