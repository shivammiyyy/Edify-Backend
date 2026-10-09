package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.IngestionJob;
import com.example.ragingestor.ingestion.AsyncIngestionService.IngestionInput;
import com.example.ragingestor.rag.EmbeddingProvider;
import com.example.ragingestor.rag.EmbeddingCache;
import com.example.ragingestor.observability.EdifyMetrics;
import com.example.ragingestor.security.CorrelationIdFilter;
import com.example.ragingestor.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class IngestionProcessor {

    private static final Logger log = LoggerFactory.getLogger(IngestionProcessor.class);

    private final AsyncIngestionService ingestionService;
    private final DocumentIngestionService documentIngestionService;
    private final ObjectStorageService objectStorageService;
    private final DocumentContentExtractor contentExtractor;
    private final ChunkingService chunkingService;
    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingCache embeddingCache;
    private final DocumentIngestionEventPublisher eventPublisher;
    private final EdifyMetrics metrics;

    public IngestionProcessor(AsyncIngestionService ingestionService,
                              DocumentIngestionService documentIngestionService,
                              ObjectStorageService objectStorageService,
                              DocumentContentExtractor contentExtractor,
                              ChunkingService chunkingService,
                              EmbeddingProvider embeddingProvider,
                              EmbeddingCache embeddingCache,
                              DocumentIngestionEventPublisher eventPublisher,
                              EdifyMetrics metrics) {
        this.ingestionService = ingestionService;
        this.documentIngestionService = documentIngestionService;
        this.objectStorageService = objectStorageService;
        this.contentExtractor = contentExtractor;
        this.chunkingService = chunkingService;
        this.embeddingProvider = embeddingProvider;
        this.embeddingCache = embeddingCache;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
    }

    public void process(DocumentIngestionEvent event) {
        metrics.time("edify.document.processing.duration", () -> processMeasured(event),
                failed -> failed ? "failure" : "success");
    }

    private boolean processMeasured(DocumentIngestionEvent event) {
        IngestionInput input = ingestionService.loadInput(event.jobId());
        String correlationId = input.correlationId();
        if (correlationId != null) {
            MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        }
        try {
            if (input.status() == IngestionJob.JobStatus.READY) {
                eventPublisher.publishProcessed(eventPublisher.createEvent(input));
                return false;
            }
            if (input.status() == IngestionJob.JobStatus.FAILED) {
                eventPublisher.publishFailed(eventPublisher.createEvent(input));
                return true;
            }
            try {
                processPending(input);
            } catch (RuntimeException failure) {
                metrics.increment("edify.document.processing.failures");
                log.error("Document ingestion failed for job {}", input.jobId(), failure);
                ingestionService.markFailed(input.jobId(), "Document processing failed");
                documentIngestionService.markFailedVersion(input.documentId(), input.documentVersionId());
                eventPublisher.publishFailed(eventPublisher.createEvent(input));
                return true;
            }
            eventPublisher.publishProcessed(eventPublisher.createEvent(input));
            return false;
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }

    private void processPending(IngestionInput input) {
        setStage(input, IngestionJob.JobStatus.PARSING, DocumentMeta.IngestionStatus.PARSING);
        byte[] original = objectStorageService.get(input.storageKey());
        String content = contentExtractor.extract(input.filename(), original);

        setStage(input, IngestionJob.JobStatus.CHUNKING, DocumentMeta.IngestionStatus.CHUNKING);
        List<String> chunks = chunkingService.chunk(content);
        if (chunks.isEmpty()) {
            throw new InvalidDocumentException("The uploaded document contains no text to index");
        }

        setStage(input, IngestionJob.JobStatus.EMBEDDING, DocumentMeta.IngestionStatus.EMBEDDING);
        List<float[]> vectors = metrics.time("edify.embedding.duration",
                () -> embeddingCache.embedBatch(embeddingProvider, chunks));
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("embedding provider returned a different number of vectors than chunks");
        }

        setStage(input, IngestionJob.JobStatus.INDEXING, DocumentMeta.IngestionStatus.INDEXING);
        documentIngestionService.indexPreparedVersion(input.documentId(), input.documentVersionId(),
                input.actorId(), chunks, vectors);
        ingestionService.updateJobStatus(input.jobId(), IngestionJob.JobStatus.READY);
    }

    private void setStage(IngestionInput input, IngestionJob.JobStatus jobStatus,
                          DocumentMeta.IngestionStatus documentStatus) {
        ingestionService.updateJobStatus(input.jobId(), jobStatus);
        documentIngestionService.updateProcessingStatus(input.documentId(), documentStatus);
    }
}
