package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMetaRepository;
import com.example.ragingestor.security.AppPrincipal;
import com.example.ragingestor.security.AuthorizationService;
import com.example.ragingestor.security.Role;
import com.example.ragingestor.security.RedisRateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionControllerTest {

    private final IngestionGateway gateway = mock(IngestionGateway.class);
    private final DocumentMetaRepository documents = mock(DocumentMetaRepository.class);
    private final DocumentIngestionService documentIngestion = mock(DocumentIngestionService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final AsyncIngestionService asyncIngestion = mock(AsyncIngestionService.class);
    private final DocumentIngestionEventPublisher publisher = mock(DocumentIngestionEventPublisher.class);
    private final RedisRateLimitService rateLimitService = mock(RedisRateLimitService.class);
    private final IngestionController controller = new IngestionController(gateway, documents, documentIngestion,
            authorization, asyncIngestion, publisher, rateLimitService, 10_485_760);

    @Test
    void acceptsTheRequestAfterPersistingAndPublishingTheJob() {
        AppPrincipal principal = mock(AppPrincipal.class);
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(principal.getRole()).thenReturn(Role.WRITE);
        when(principal.getUserId()).thenReturn(userId);
        when(asyncIngestion.prepare(eq("notes.txt"), any(), eq("text/plain"), eq(Role.READ), eq(userId),
                isNull(), isNull(), isNull(), isNull(), any())).thenReturn(new AsyncIngestionService.Submission(
                documentId, jobId, "PROCESSING", false));
        when(asyncIngestion.loadInput(jobId)).thenReturn(new AsyncIngestionService.IngestionInput(jobId,
                documentId, UUID.randomUUID(), "notes.txt", "documents/key", "text/plain",
                userId, "correlation-1", com.example.ragingestor.domain.IngestionJob.JobStatus.UPLOADED));

        var response = controller.create(new IngestionController.CreateRequest(
                "notes.txt", "content", Role.READ, null, null, null, null), principal);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(jobId, response.getBody().jobId());
        assertEquals("PROCESSING", response.getBody().status());
        verify(publisher).publishUploaded(any(AsyncIngestionService.IngestionInput.class));
    }

    @Test
    void reportsAnExistingIdenticalUploadWithoutCreatingAnotherJob() {
        AppPrincipal principal = mock(AppPrincipal.class);
        UUID documentId = UUID.randomUUID();
        when(principal.getRole()).thenReturn(Role.WRITE);
        when(principal.getUserId()).thenReturn(UUID.randomUUID());
        when(asyncIngestion.prepare(anyString(), any(), anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AsyncIngestionService.Submission(documentId, null, "READY", true));

        var response = controller.create(new IngestionController.CreateRequest(
                "notes.txt", "content", null, null, null, null, null), principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(documentId, response.getBody().documentId());
        assertNull(response.getBody().jobId());
        verifyNoInteractions(publisher);
    }
}
