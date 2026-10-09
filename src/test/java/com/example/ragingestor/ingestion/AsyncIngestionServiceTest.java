package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.*;
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

class AsyncIngestionServiceTest {

    private final DocumentMetaRepository documents = mock(DocumentMetaRepository.class);
    private final DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
    private final IngestionJobRepository jobs = mock(IngestionJobRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final AsyncIngestionService service = new AsyncIngestionService(documents, versions, jobs, storage);

    @BeforeEach
    void setUp() {
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
        when(jobs.save(any(IngestionJob.class))).thenAnswer(invocation -> {
            IngestionJob job = invocation.getArgument(0);
            if (job.getId() == null) job.setId(UUID.randomUUID());
            return job;
        });
        when(versions.findDuplicates(any(), any(), anyString(), eq(DocumentMeta.IngestionStatus.DELETED),
                eq(DocumentMeta.IngestionStatus.FAILED))).thenReturn(java.util.List.of());
    }

    @Test
    void persistsOriginalVersionAndJobBeforeItCanBePublished() throws Exception {
        byte[] original = "new policy".getBytes(StandardCharsets.UTF_8);
        UUID actor = UUID.randomUUID();
        String contentHash = sha256(original);

        AsyncIngestionService.Submission submission = service.prepare("policy.txt", original, "text/plain",
                Role.READ, actor, null, "engineering", "policy", List.of("security", "on-call"),
                "correlation-1");

        assertFalse(submission.duplicate());
        assertNotNull(submission.documentId());
        assertNotNull(submission.jobId());
        assertEquals("PROCESSING", submission.status());
        verify(storage).put(startsWith("documents/" + submission.documentId() + "/"), eq(original), eq("text/plain"));
        verify(jobs).save(argThat(job -> job.getDocumentId().equals(submission.documentId())
                && job.getStatus() == IngestionJob.JobStatus.UPLOADED
                && job.getCorrelationId().equals("correlation-1")));
        verify(versions, atLeastOnce()).save(argThat(version -> version.getDocumentId().equals(submission.documentId())
                && version.getVersionNumber() == 1
                && version.getContentHash().equals(contentHash)));
    }

    @Test
    void returnsExistingSubmissionInsteadOfCreatingAnotherForDuplicateContent() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID existingDocumentId = UUID.randomUUID();
        UUID existingJobId = UUID.randomUUID();
        DocumentMeta existing = new DocumentMeta();
        existing.setId(existingDocumentId);
        existing.setStatus(DocumentMeta.IngestionStatus.PARSING);
        IngestionJob job = new IngestionJob();
        job.setId(existingJobId);
        when(versions.findDuplicates(eq(actor), isNull(), eq(sha256("same".getBytes(StandardCharsets.UTF_8))),
                eq(DocumentMeta.IngestionStatus.DELETED), eq(DocumentMeta.IngestionStatus.FAILED)))
                .thenReturn(java.util.List.of(existing));
        when(jobs.findFirstByDocumentIdOrderByCreatedAtDesc(existingDocumentId)).thenReturn(Optional.of(job));

        AsyncIngestionService.Submission result = service.prepare("copy.txt", "same".getBytes(StandardCharsets.UTF_8),
                "text/plain", Role.READ, actor, null, null, null, List.of(), "correlation-2");

        assertTrue(result.duplicate());
        assertEquals(existingDocumentId, result.documentId());
        assertEquals(existingJobId, result.jobId());
        assertEquals("PARSING", result.status());
        verifyNoInteractions(storage);
        verify(documents, never()).save(any(DocumentMeta.class));
    }

    @Test
    void rejectsUnsafeMetadataTags() {
        assertThrows(InvalidDocumentException.class, () -> service.prepare(
                "policy.txt", "content".getBytes(StandardCharsets.UTF_8), "text/plain",
                Role.READ, UUID.randomUUID(), null, null, null, List.of("security,admin"), "correlation-3"));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
