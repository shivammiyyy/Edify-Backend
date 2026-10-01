package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.*;
import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import com.example.ragingestor.rag.EmbeddingService;
import com.example.ragingestor.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private final DocumentMetaRepository documentMetaRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ChunkVectorRepository chunkVectorRepository;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final int embeddingDimension;

    public DocumentIngestionService(DocumentMetaRepository documentMetaRepository,
                                     DocumentChunkRepository documentChunkRepository,
                                     ChunkVectorRepository chunkVectorRepository,
                                     ChunkingService chunkingService,
                                     EmbeddingService embeddingService,
                                     @org.springframework.beans.factory.annotation.Value("${app.embeddings.dimension}") int embeddingDimension) {
        this.documentMetaRepository = documentMetaRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.chunkVectorRepository = chunkVectorRepository;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.embeddingDimension = embeddingDimension;
    }

    @Transactional
    public IngestionResult create(CreateDocumentCommand cmd) {
        DocumentMeta doc = new DocumentMeta();
        doc.setFilename(cmd.filename());
        doc.setUploadedBy(cmd.actorUserId());
        doc.setFolderId(cmd.folderId());
        doc.setMinRoleRequired(cmd.minRoleRequired() != null ? cmd.minRoleRequired() : Role.READ);
        doc.setStatus(DocumentMeta.IngestionStatus.CHUNKING);
        doc = documentMetaRepository.save(doc);

        int count = chunkAndEmbed(doc, cmd.content(), cmd.actorUserId());
        doc.setStatus(DocumentMeta.IngestionStatus.READY);
        doc.setUpdatedAt(Instant.now());
        documentMetaRepository.save(doc);
        return new IngestionResult(doc.getId(), count, "READY", "document ingested");
    }

    @Transactional
    public IngestionResult update(UpdateDocumentCommand cmd) {
        DocumentMeta doc = documentMetaRepository.findById(cmd.documentId())
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + cmd.documentId()));

        // Replace-in-place: soft-delete old chunks + their vectors, then re-chunk fresh content.
        List<DocumentChunk> old = documentChunkRepository.findByDocumentIdAndDeletedFalse(doc.getId());
        for (DocumentChunk chunk : old) {
            chunk.setDeleted(true);
            documentChunkRepository.save(chunk);
            chunkVectorRepository.deleteEmbedding(chunk.getId());
        }

        doc.setStatus(DocumentMeta.IngestionStatus.CHUNKING);
        documentMetaRepository.save(doc);

        int count = chunkAndEmbed(doc, cmd.newContent(), cmd.actorUserId());
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

    private int chunkAndEmbed(DocumentMeta doc, String content, UUID actorUserId) {
        List<String> textChunks = chunkingService.chunk(content);
        List<float[]> embeddings = embeddingService.embedBatch(textChunks);
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
            chunk.setChunkIndex(i);
            chunk.setContent(textChunks.get(i));
            chunk.setMinRoleRequired(doc.getMinRoleRequired());
            chunk.setCreatedBy(actorUserId);
            chunk = documentChunkRepository.save(chunk);
            chunkVectorRepository.upsertEmbedding(chunk.getId(), embeddings.get(i));
        }
        return textChunks.size();
    }
}
