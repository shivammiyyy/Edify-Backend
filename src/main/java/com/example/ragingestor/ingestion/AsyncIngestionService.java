package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.*;
import com.example.ragingestor.security.Role;
import com.example.ragingestor.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AsyncIngestionService {

    private static final Pattern SAFE_TAG = Pattern.compile("[a-zA-Z0-9 _-]{1,64}");
    private final DocumentMetaRepository documents;
    private final DocumentVersionRepository versions;
    private final IngestionJobRepository jobs;
    private final ObjectStorageService storage;

    public AsyncIngestionService(DocumentMetaRepository documents, DocumentVersionRepository versions,
                                 IngestionJobRepository jobs, ObjectStorageService storage) {
        this.documents = documents;
        this.versions = versions;
        this.jobs = jobs;
        this.storage = storage;
    }

    @Transactional
    public Submission prepare(String filename, byte[] original, String contentType, Role minimumRole,
                              UUID actorId, UUID folderId, String department, String documentType,
                              List<String> tags, String correlationId) {
        String contentHash = sha256(original);
        Optional<DocumentMeta> duplicate = versions.findDuplicates(actorId, folderId, contentHash,
                DocumentMeta.IngestionStatus.DELETED, DocumentMeta.IngestionStatus.FAILED).stream().findFirst();
        if (duplicate.isPresent()) {
            DocumentMeta existing = duplicate.get();
            UUID currentJobId = jobs.findFirstByDocumentIdOrderByCreatedAtDesc(existing.getId())
                    .map(IngestionJob::getId).orElse(null);
            return new Submission(existing.getId(), currentJobId, existing.getStatus().name(), true);
        }

        DocumentMeta document = new DocumentMeta();
        document.setFilename(filename);
        document.setUploadedBy(actorId);
        document.setFolderId(folderId);
        document.setMinRoleRequired(minimumRole == null ? Role.READ : minimumRole);
        document.setDepartment(cleanOptional(department));
        document.setDocumentType(cleanOptional(documentType));
        document.setTags(normalizeTags(tags));
        document.setStatus(DocumentMeta.IngestionStatus.UPLOADED);
        document = documents.save(document);

        return queueVersion(document, filename, original, contentType, contentHash, actorId, 1,
                correlationId, true);
    }

    @Transactional
    public Submission prepareUpdate(UUID documentId, String filename, byte[] original, String contentType,
                                    UUID actorId, String correlationId) {
        DocumentMeta document = documents.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("document not found: " + documentId));
        String contentHash = sha256(original);
        Optional<DocumentVersion> existing = versions.findByDocumentIdAndContentHash(documentId, contentHash);
        if (existing.isPresent()) {
            DocumentVersion existingVersion = existing.get();
            if (existingVersion.getStatus() == DocumentVersion.VersionStatus.READY) {
                document.setActiveVersionId(existingVersion.getId());
                document.setFilename(filename);
                document.setStatus(DocumentMeta.IngestionStatus.READY);
                document.setUpdatedAt(Instant.now());
                documents.save(document);
                return new Submission(documentId,
                        jobs.findFirstByDocumentVersionIdOrderByCreatedAtDesc(existingVersion.getId())
                                .map(IngestionJob::getId).orElse(null),
                        "READY", true);
            }
            if (existingVersion.getStatus() == DocumentVersion.VersionStatus.UPLOADED) {
                Optional<IngestionJob> pendingJob = jobs.findFirstByDocumentVersionIdOrderByCreatedAtDesc(
                        existingVersion.getId()).filter(job -> job.getStatus() != IngestionJob.JobStatus.FAILED);
                if (pendingJob.isPresent()) {
                    return new Submission(documentId, pendingJob.get().getId(), "PROCESSING", true);
                }
            }
            existingVersion.setStatus(DocumentVersion.VersionStatus.UPLOADED);
            existingVersion.setContentType(contentType);
            versions.save(existingVersion);
            document.setFilename(filename);
            document.setStatus(DocumentMeta.IngestionStatus.UPLOADED);
            document.setUpdatedAt(Instant.now());
            documents.save(document);
            storage.put(existingVersion.getStorageKey(), original, contentType);
            IngestionJob retry = createJob(documentId, existingVersion.getId(), correlationId);
            return new Submission(documentId, retry.getId(), "PROCESSING", false);
        }
        int nextVersion = versions.findTopByDocumentIdOrderByVersionNumberDesc(documentId)
                .map(DocumentVersion::getVersionNumber).orElse(0) + 1;
        document.setFilename(filename);
        document.setStatus(DocumentMeta.IngestionStatus.UPLOADED);
        document.setUpdatedAt(Instant.now());
        documents.save(document);
        return queueVersion(document, filename, original, contentType, contentHash, actorId, nextVersion,
                correlationId, false);
    }

    @Transactional
    public IngestionInput loadInput(UUID jobId) {
        IngestionJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("ingestion job not found"));
        DocumentMeta document = documents.findById(job.getDocumentId())
                .orElseThrow(() -> new IllegalArgumentException("ingestion document not found"));
        DocumentVersion version = versions.findById(job.getDocumentVersionId())
                .orElseThrow(() -> new IllegalArgumentException("ingestion version not found"));
        return new IngestionInput(job.getId(), document.getId(), version.getId(), document.getFilename(),
                version.getStorageKey(), version.getContentType(), document.getUploadedBy(),
                job.getCorrelationId(), job.getStatus());
    }

    @Transactional
    public void updateJobStatus(UUID jobId, IngestionJob.JobStatus status) {
        IngestionJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("ingestion job not found"));
        if (job.getStatus() == IngestionJob.JobStatus.READY || job.getStatus() == IngestionJob.JobStatus.FAILED) {
            return;
        }
        job.setStatus(status);
        if (job.getStartedAt() == null) {
            job.setStartedAt(Instant.now());
        }
        if (status == IngestionJob.JobStatus.READY) {
            job.setCompletedAt(Instant.now());
            job.setErrorMessage(null);
        }
        jobs.save(job);
    }

    @Transactional
    public void markFailed(UUID jobId, String message) {
        IngestionJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("ingestion job not found"));
        job.setStatus(IngestionJob.JobStatus.FAILED);
        job.setErrorMessage(message == null || message.isBlank()
                ? "Document processing failed" : message.substring(0, Math.min(1000, message.length())));
        job.setCompletedAt(Instant.now());
        if (job.getStartedAt() == null) {
            job.setStartedAt(job.getCreatedAt());
        }
        jobs.save(job);
    }

    @Transactional
    public void markSubmissionFailed(UUID jobId, String message) {
        markFailed(jobId, message);
        IngestionJob job = jobs.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("ingestion job not found"));
        versions.findById(job.getDocumentVersionId()).ifPresent(version -> {
            version.setStatus(DocumentVersion.VersionStatus.FAILED);
            versions.save(version);
        });
        documents.findById(job.getDocumentId()).ifPresent(document -> {
            document.setStatus(DocumentMeta.IngestionStatus.FAILED);
            document.setUpdatedAt(Instant.now());
            documents.save(document);
        });
    }

    public Optional<IngestionJob> findJob(UUID jobId) {
        return jobs.findById(jobId);
    }

    public record Submission(UUID documentId, UUID jobId, String status, boolean duplicate) {}

    public record IngestionInput(UUID jobId, UUID documentId, UUID documentVersionId, String filename,
                                 String storageKey, String contentType, UUID actorId, String correlationId,
                                 IngestionJob.JobStatus status) {}

    private static String storageKey(UUID documentId, UUID versionId) {
        return "documents/" + documentId + "/" + versionId + "/original";
    }

    private Submission queueVersion(DocumentMeta document, String filename, byte[] original, String contentType,
                                    String contentHash, UUID actorId, int versionNumber, String correlationId,
                                    boolean activateImmediately) {
        DocumentVersion version = new DocumentVersion();
        version.setDocumentId(document.getId());
        version.setVersionNumber(versionNumber);
        version.setContentHash(contentHash);
        version.setContentType(contentType);
        version.setStorageKey("pending");
        version.setCreatedBy(actorId);
        version = versions.save(version);
        version.setStorageKey(storageKey(document.getId(), version.getId()));
        versions.save(version);
        document.setFilename(filename);
        if (activateImmediately) {
            document.setActiveVersionId(version.getId());
        }
        documents.save(document);
        storage.put(version.getStorageKey(), original, contentType);

        IngestionJob job = createJob(document.getId(), version.getId(), correlationId);
        return new Submission(document.getId(), job.getId(), "PROCESSING", false);
    }

    private IngestionJob createJob(UUID documentId, UUID versionId, String correlationId) {
        IngestionJob job = new IngestionJob();
        job.setDocumentId(documentId);
        job.setDocumentVersionId(versionId);
        job.setCorrelationId(correlationId);
        job.setStatus(IngestionJob.JobStatus.UPLOADED);
        return jobs.save(job);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        List<String> normalized = tags.stream().map(String::trim).filter(tag -> !tag.isBlank())
                .map(String::toLowerCase).distinct().toList();
        if (normalized.size() > 10 || normalized.stream().anyMatch(tag -> !SAFE_TAG.matcher(tag).matches())) {
            throw new InvalidDocumentException(
                    "At most 10 tags are allowed, using letters, numbers, spaces, underscores, or hyphens");
        }
        return normalized;
    }
}
