package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.IngestionJob;
import com.example.ragingestor.rag.EmbeddingService;
import com.example.ragingestor.rag.EmbeddingCache;
import com.example.ragingestor.observability.EdifyMetrics;
import com.example.ragingestor.storage.ObjectStorageService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IngestionProcessorTest {

    private final AsyncIngestionService ingestion = mock(AsyncIngestionService.class);
    private final DocumentIngestionService documents = mock(DocumentIngestionService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final DocumentContentExtractor extractor = new DocumentContentExtractor();
    private final ChunkingService chunking = new ChunkingService();
    private final EmbeddingService embeddings = mock(EmbeddingService.class);
    private final EmbeddingCache embeddingCache = mock(EmbeddingCache.class);
    private final DocumentIngestionEventPublisher events = mock(DocumentIngestionEventPublisher.class);

    private final IngestionProcessor processor = new IngestionProcessor(ingestion, documents, storage,
            extractor, chunking, embeddings, embeddingCache, events,
            new EdifyMetrics(new SimpleMeterRegistry()));

    @Test
    void movesJobThroughStagesAndIndexesInsideTheVertxWorker() {
        UUID jobId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String text = "This is the uploaded policy.";
        AsyncIngestionService.IngestionInput input = new AsyncIngestionService.IngestionInput(
                jobId, documentId, versionId, "policy.txt", "documents/key", "text/plain",
                actorId, "trace-worker-1", IngestionJob.JobStatus.UPLOADED);
        DocumentIngestionEvent event = new DocumentIngestionEvent(UUID.randomUUID(), documentId, versionId,
                jobId, null, "trace-worker-1", Instant.now());
        when(ingestion.loadInput(jobId)).thenReturn(input);
        when(storage.get("documents/key")).thenReturn(text.getBytes(StandardCharsets.UTF_8));
        when(embeddings.embedBatch(anyList())).thenReturn(List.of(new float[]{0.1f}));
        when(embeddingCache.embedBatch(eq(embeddings), anyList())).thenAnswer(invocation ->
                embeddings.embedBatch(invocation.getArgument(1)));
        when(events.createEvent(input)).thenReturn(event);

        processor.process(event);

        verify(ingestion).updateJobStatus(jobId, IngestionJob.JobStatus.PARSING);
        verify(ingestion).updateJobStatus(jobId, IngestionJob.JobStatus.CHUNKING);
        verify(ingestion).updateJobStatus(jobId, IngestionJob.JobStatus.EMBEDDING);
        verify(ingestion).updateJobStatus(jobId, IngestionJob.JobStatus.INDEXING);
        verify(ingestion).updateJobStatus(jobId, IngestionJob.JobStatus.READY);
        verify(documents).indexPreparedVersion(eq(documentId), eq(versionId), eq(actorId),
                eq(List.of(text)), anyList());
        verify(events).publishProcessed(event);
        verify(events, never()).publishFailed(any());
    }

    @Test
    void recordsProcessingFailureWithoutPublishingAProcessedEvent() {
        UUID jobId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AsyncIngestionService.IngestionInput input = new AsyncIngestionService.IngestionInput(
                jobId, documentId, versionId, "policy.txt", "documents/key", "text/plain",
                UUID.randomUUID(), null, IngestionJob.JobStatus.UPLOADED);
        DocumentIngestionEvent event = new DocumentIngestionEvent(UUID.randomUUID(), documentId, versionId,
                jobId, null, null, Instant.now());
        when(ingestion.loadInput(jobId)).thenReturn(input);
        when(storage.get("documents/key")).thenThrow(new IllegalStateException("storage offline"));
        when(events.createEvent(input)).thenReturn(event);

        processor.process(event);

        verify(ingestion).markFailed(jobId, "Document processing failed");
        verify(documents).markFailedVersion(documentId, versionId);
        verify(events).publishFailed(event);
        verify(events, never()).publishProcessed(any());
    }
}
