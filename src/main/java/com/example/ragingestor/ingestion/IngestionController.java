package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.DocumentMetaRepository;
import com.example.ragingestor.ingestion.dto.IngestionDtos.*;
import com.example.ragingestor.security.AppPrincipal;
import com.example.ragingestor.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;

/**
 * All of these routes are gated at hasRole("WRITE") in SecurityConfig, which (through
 * RoleHierarchy) also lets CUSTODIAN and ADMIN through, and keeps plain READ users out --
 * matching "chunking/marking/CRUD... only WRITE, CUSTODIAN and ADMIN can use".
 */
@RestController
@RequestMapping("/api/documents")
public class IngestionController {

    private final IngestionGateway ingestionGateway;
    private final DocumentMetaRepository documentMetaRepository;
    private final DocumentIngestionService documentIngestionService;

    public IngestionController(IngestionGateway ingestionGateway, DocumentMetaRepository documentMetaRepository,
                                DocumentIngestionService documentIngestionService) {
        this.ingestionGateway = ingestionGateway;
        this.documentMetaRepository = documentMetaRepository;
        this.documentIngestionService = documentIngestionService;
    }

    public record CreateRequest(@NotBlank String filename, @NotBlank String content, Role minRoleRequired, UUID folderId) {}
    public record UpdateRequest(@NotBlank String content) {}
    public record MoveRequest(UUID folderId) {}
    public record DocumentView(UUID id, String filename, Role minRoleRequired,
                               DocumentMeta.IngestionStatus status, java.time.Instant createdAt,
                               java.time.Instant updatedAt, UUID folderId) {
        static DocumentView of(DocumentMeta document) {
            return new DocumentView(document.getId(), document.getFilename(), document.getMinRoleRequired(),
                    document.getStatus(), document.getCreatedAt(), document.getUpdatedAt(), document.getFolderId());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('READ')")
    public List<DocumentView> list(@AuthenticationPrincipal AppPrincipal principal) {
        return documentMetaRepository.findAll().stream()
                .filter(document -> principal.getRole().atLeast(document.getMinRoleRequired()))
                .map(DocumentView::of)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('WRITE')")
    public IngestionResult create(@Valid @RequestBody CreateRequest req, @AuthenticationPrincipal AppPrincipal principal) {
        return ingestionGateway.create(new CreateDocumentCommand(
                req.filename(), req.content(), req.minRoleRequired(), principal.getUserId(), req.folderId()));
    }

    @PutMapping("/{documentId}")
    @PreAuthorize("hasRole('WRITE')")
    public IngestionResult update(@PathVariable UUID documentId, @Valid @RequestBody UpdateRequest req,
                                   @AuthenticationPrincipal AppPrincipal principal) {
        return ingestionGateway.update(new UpdateDocumentCommand(documentId, req.content(), principal.getUserId()));
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('WRITE')")
    public IngestionResult delete(@PathVariable UUID documentId, @AuthenticationPrincipal AppPrincipal principal) {
        return ingestionGateway.delete(new DeleteDocumentCommand(documentId, principal.getUserId()));
    }

    @PutMapping("/{documentId}/move")
    @PreAuthorize("hasRole('WRITE')")
    public DocumentView move(@PathVariable UUID documentId, @RequestBody MoveRequest req) {
        return DocumentView.of(documentIngestionService.move(documentId, req.folderId()));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasRole('WRITE')")
    public DocumentMeta get(@PathVariable UUID documentId) {
        return documentMetaRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
