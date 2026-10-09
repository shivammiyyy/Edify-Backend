package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.DocumentMetaRepository;
import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import com.example.ragingestor.security.AppPrincipal;
import com.example.ragingestor.security.AuthorizationService;
import com.example.ragingestor.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.time.Instant;
import java.util.UUID;
import java.util.List;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.slf4j.MDC;
import com.example.ragingestor.security.CorrelationIdFilter;
import com.example.ragingestor.security.RedisRateLimitService;

/**
 * All of these routes are gated at hasRole("WRITE") in SecurityConfig, which (through
 * RoleHierarchy) also lets CUSTODIAN and ADMIN through, and keeps plain READ users out --
 * matching "chunking/marking/CRUD... only WRITE, CUSTODIAN and ADMIN can use".
 */
@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Authorized document browsing, uploads, versions, downloads, and deletion")
public class IngestionController {

    private final IngestionGateway ingestionGateway;
    private final DocumentMetaRepository documentMetaRepository;
    private final DocumentIngestionService documentIngestionService;
    private final AuthorizationService authorizationService;
    private final AsyncIngestionService asyncIngestionService;
    private final DocumentIngestionEventPublisher eventPublisher;
    private final RedisRateLimitService rateLimitService;
    private final long maxUploadBytes;

    public IngestionController(IngestionGateway ingestionGateway, DocumentMetaRepository documentMetaRepository,
                                DocumentIngestionService documentIngestionService,
                                AuthorizationService authorizationService,
                                AsyncIngestionService asyncIngestionService,
                                DocumentIngestionEventPublisher eventPublisher,
                                RedisRateLimitService rateLimitService,
                                @Value("${app.documents.max-upload-bytes:10485760}") long maxUploadBytes) {
        this.ingestionGateway = ingestionGateway;
        this.documentMetaRepository = documentMetaRepository;
        this.documentIngestionService = documentIngestionService;
        this.authorizationService = authorizationService;
        this.asyncIngestionService = asyncIngestionService;
        this.eventPublisher = eventPublisher;
        this.rateLimitService = rateLimitService;
        this.maxUploadBytes = maxUploadBytes;
    }

    public record CreateRequest(@NotBlank String filename, @NotBlank String content, Role minRoleRequired,
                                UUID folderId, @Size(max = 120) String department,
                                @Size(max = 80) String documentType,
                                @Size(max = 10) List<@Pattern(regexp = "[A-Za-z0-9 _-]{1,64}") String> tags) {}
    public record UpdateRequest(@NotBlank String content) {}
    public record MoveRequest(UUID folderId) {}
    public record DocumentView(UUID id, String filename, Role minRoleRequired,
                               DocumentMeta.IngestionStatus status, java.time.Instant createdAt,
                               java.time.Instant updatedAt, UUID folderId, UUID activeVersionId) {
        static DocumentView of(DocumentMeta document) {
            return new DocumentView(document.getId(), document.getFilename(), document.getMinRoleRequired(),
                    document.getStatus(), document.getCreatedAt(), document.getUpdatedAt(), document.getFolderId(),
                    document.getActiveVersionId());
        }
    }
    public record VersionView(UUID id, int versionNumber, String contentHash, String status,
                              Instant createdAt, UUID createdBy, boolean active) {}
    public record SubmissionView(UUID documentId, UUID jobId, String status, int chunkCount, String message) {}

    @GetMapping
    @PreAuthorize("hasRole('READ')")
    @Operation(summary = "List documents visible to the current user's role")
    public List<DocumentView> list(@AuthenticationPrincipal AppPrincipal principal) {
        return documentMetaRepository.findAll().stream()
                .filter(document -> authorizationService.canReadDocument(document, principal.getRole()))
                .map(DocumentView::of)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Queue a text document for asynchronous ingestion")
    @SecurityRequirement(name = "csrfToken")
    public ResponseEntity<SubmissionView> create(@Valid @RequestBody CreateRequest req,
                                                  @AuthenticationPrincipal AppPrincipal principal) {
        authorizationService.requireWriteFolder(req.folderId(), principal.getRole());
        rateLimitService.checkDocumentUpload(principal.getUserId());
        return submit(req.filename(), req.content().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "text/plain", req.minRoleRequired(), req.folderId(), req.department(), req.documentType(),
                req.tags(), principal);
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Upload a PDF, TXT, Markdown, or DOCX document")
    @SecurityRequirement(name = "csrfToken")
    public ResponseEntity<SubmissionView> upload(@RequestPart("file") MultipartFile file,
                                                  @RequestParam(required = false) Role minRoleRequired,
                                                  @RequestParam(required = false) UUID folderId,
                                                  @RequestParam(required = false) String department,
                                                  @RequestParam(required = false) String documentType,
                                                  @RequestParam(required = false) List<String> tags,
                                                  @AuthenticationPrincipal AppPrincipal principal) {
        authorizationService.requireWriteFolder(folderId, principal.getRole());
        rateLimitService.checkDocumentUpload(principal.getUserId());
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (filename.isBlank() || filename.contains("..") || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty file with a valid filename is required");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the size limit");
        }
        try {
            return submit(filename, file.getBytes(), contentType(filename), minRoleRequired, folderId,
                    department, documentType, tags, principal);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file", e);
        }
    }

    @PutMapping("/{documentId}")
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Queue a replacement text version")
    @SecurityRequirement(name = "csrfToken")
    public ResponseEntity<SubmissionView> update(@PathVariable UUID documentId, @Valid @RequestBody UpdateRequest req,
                                                 @AuthenticationPrincipal AppPrincipal principal) {
        DocumentMeta document = getDocument(documentId);
        authorizationService.requireWriteDocument(document, principal.getRole());
        rateLimitService.checkDocumentUpload(principal.getUserId());
        String contentType = documentIngestionService.downloadContentType(documentId);
        if (!contentType.equals("text/plain") && !contentType.equals("text/markdown")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use the multipart upload endpoint to replace a binary document");
        }
        byte[] original = req.content().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return submitUpdate(documentId, document.getFilename(), original, contentType, principal);
    }

    @PutMapping(path = "/{documentId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Queue a replacement file version")
    @SecurityRequirement(name = "csrfToken")
    public ResponseEntity<SubmissionView> updateUpload(@PathVariable UUID documentId,
                                                        @RequestPart("file") MultipartFile file,
                                                        @AuthenticationPrincipal AppPrincipal principal) {
        DocumentMeta document = getDocument(documentId);
        authorizationService.requireWriteDocument(document, principal.getRole());
        rateLimitService.checkDocumentUpload(principal.getUserId());
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        if (filename.isBlank() || filename.contains("..") || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty file with a valid filename is required");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file exceeds the size limit");
        }
        try {
            byte[] original = file.getBytes();
            return submitUpdate(documentId, filename, original, contentType(filename), principal);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file", e);
        }
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Delete an authorized document")
    @SecurityRequirement(name = "csrfToken")
    public IngestionResult delete(@PathVariable UUID documentId, @AuthenticationPrincipal AppPrincipal principal) {
        authorizationService.requireDeleteDocument(getDocument(documentId), principal.getRole());
        return ingestionGateway.delete(new DeleteDocumentCommand(documentId, principal.getUserId(),
                MDC.get(CorrelationIdFilter.MDC_KEY)));
    }

    @PutMapping("/{documentId}/move")
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Move a document to an authorized folder")
    @SecurityRequirement(name = "csrfToken")
    public DocumentView move(@PathVariable UUID documentId, @RequestBody MoveRequest req,
                             @AuthenticationPrincipal AppPrincipal principal) {
        authorizationService.requireWriteDocument(getDocument(documentId), principal.getRole());
        authorizationService.requireWriteFolder(req.folderId(), principal.getRole());
        return DocumentView.of(documentIngestionService.move(documentId, req.folderId()));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasRole('READ')")
    public DocumentView get(@PathVariable UUID documentId, @AuthenticationPrincipal AppPrincipal principal) {
        DocumentMeta document = getDocument(documentId);
        authorizationService.requireReadDocument(document, principal.getRole());
        return DocumentView.of(document);
    }

    @GetMapping("/{documentId}/versions")
    @PreAuthorize("hasRole('READ')")
    public List<VersionView> versions(@PathVariable UUID documentId,
                                     @AuthenticationPrincipal AppPrincipal principal) {
        DocumentMeta document = getDocument(documentId);
        authorizationService.requireReadDocument(document, principal.getRole());
        return documentIngestionService.versions(documentId).stream()
                .map(version -> new VersionView(version.getId(), version.getVersionNumber(), version.getContentHash(),
                        version.getStatus().name(), version.getCreatedAt(), version.getCreatedBy(),
                        version.getId().equals(document.getActiveVersionId())))
                .toList();
    }

    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasRole('READ')")
    @Operation(summary = "Download the active original document version")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID documentId,
                                                       @AuthenticationPrincipal AppPrincipal principal) {
        DocumentMeta document = getDocument(documentId);
        authorizationService.requireReadDocument(document, principal.getRole());
        byte[] content = documentIngestionService.download(documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documentIngestionService.downloadContentType(documentId)))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFilename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }

    private DocumentMeta getDocument(UUID documentId) {
        return documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
    }

    private ResponseEntity<SubmissionView> submit(String filename, byte[] original, String contentType,
                                                   Role minimumRole, UUID folderId, String department,
                                                   String documentType, List<String> tags, AppPrincipal principal) {
        AsyncIngestionService.Submission submission = asyncIngestionService.prepare(filename, original,
                contentType, minimumRole, principal.getUserId(), folderId, department, documentType, tags,
                MDC.get(CorrelationIdFilter.MDC_KEY));
        return responseForSubmission(submission);
    }

    private ResponseEntity<SubmissionView> submitUpdate(UUID documentId, String filename, byte[] original,
                                                         String contentType, AppPrincipal principal) {
        AsyncIngestionService.Submission submission = asyncIngestionService.prepareUpdate(documentId, filename,
                original, contentType, principal.getUserId(), MDC.get(CorrelationIdFilter.MDC_KEY));
        return responseForSubmission(submission);
    }

    private ResponseEntity<SubmissionView> responseForSubmission(AsyncIngestionService.Submission submission) {
        if (submission.duplicate()) {
            return ResponseEntity.ok(new SubmissionView(submission.documentId(), submission.jobId(),
                    submission.status(), 0, "identical content already exists"));
        }
        try {
            AsyncIngestionService.IngestionInput input = asyncIngestionService.loadInput(submission.jobId());
            eventPublisher.publishUploaded(input);
        } catch (RuntimeException failure) {
            asyncIngestionService.markSubmissionFailed(submission.jobId(), "Document could not be queued");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The document was saved, but the ingestion job could not be queued", failure);
        }
        return ResponseEntity.accepted().body(new SubmissionView(submission.documentId(), submission.jobId(),
                "PROCESSING", 0, "document queued for ingestion"));
    }

    private String contentType(String filename) {
        String name = filename.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (name.endsWith(".md") || name.endsWith(".markdown")) return "text/markdown";
        if (name.endsWith(".txt")) return "text/plain";
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Supported formats are PDF, TXT, Markdown, and DOCX");
    }
}
