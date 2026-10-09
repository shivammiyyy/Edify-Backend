package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.IngestionJob;
import com.example.ragingestor.domain.IngestionJobRepository;
import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.DocumentMetaRepository;
import com.example.ragingestor.security.AppPrincipal;
import com.example.ragingestor.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/ingestion-jobs")
@Tag(name = "Ingestion jobs", description = "Authorized document processing status")
public class IngestionJobController {

    private final IngestionJobRepository jobRepository;
    private final DocumentMetaRepository documentRepository;
    private final AuthorizationService authorizationService;

    public IngestionJobController(IngestionJobRepository jobRepository, DocumentMetaRepository documentRepository,
                                  AuthorizationService authorizationService) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('READ')")
    @Operation(summary = "Get the status of a document ingestion job")
    public IngestionJobView get(@PathVariable UUID jobId,
                                @AuthenticationPrincipal AppPrincipal principal) {
        IngestionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ingestion job not found"));
        DocumentMeta document = documentRepository.findById(job.getDocumentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        authorizationService.requireReadDocument(document, principal.getRole());
        return IngestionJobView.of(job);
    }
}
