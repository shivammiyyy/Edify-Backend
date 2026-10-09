package com.example.ragingestor.ingestion;

import com.example.ragingestor.domain.DocumentMeta;
import com.example.ragingestor.domain.Folder;
import com.example.ragingestor.security.AppPrincipal;
import com.example.ragingestor.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

/**
 * Drive-style folder browsing: folders and the documents inside them, navigable via
 * parent/child relationships and rendered as breadcrumbs on the frontend.
 * Reads are open to any authenticated role (role-filtered); writes (create/rename/move/
 * delete) require WRITE and above, matching document ingestion permissions.
 */
@RestController
@RequestMapping("/api/folders")
@Tag(name = "Folders", description = "Role-filtered folder browsing and folder management")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    public record CreateFolderRequest(@NotBlank String name, UUID parentId, Role minRoleRequired) {}
    public record RenameFolderRequest(String name, Role minRoleRequired) {}
    public record MoveFolderRequest(UUID parentId) {}
    public record MoveDocumentRequest(UUID folderId) {}

    public record FolderView(UUID id, String name, UUID parentId, Role minRoleRequired,
                              Instant createdAt, Instant updatedAt) {
        static FolderView of(Folder folder) {
            return new FolderView(folder.getId(), folder.getName(), folder.getParentId(),
                    folder.getMinRoleRequired(), folder.getCreatedAt(), folder.getUpdatedAt());
        }
    }

    public record DocumentSummary(UUID id, String filename, Role minRoleRequired,
                                   DocumentMeta.IngestionStatus status, Instant createdAt, Instant updatedAt) {
        static DocumentSummary of(DocumentMeta document) {
            return new DocumentSummary(document.getId(), document.getFilename(), document.getMinRoleRequired(),
                    document.getStatus(), document.getCreatedAt(), document.getUpdatedAt());
        }
    }

    public record ContentsView(List<FolderView> folders, List<DocumentSummary> documents, List<FolderView> breadcrumbs) {}

    @GetMapping("/contents")
    @PreAuthorize("hasRole('READ')")
    @Operation(summary = "List visible child folders, documents, and breadcrumbs")
    public ContentsView contents(@RequestParam(required = false) UUID folderId,
                                  @AuthenticationPrincipal AppPrincipal principal) {
        FolderService.FolderContents result = folderService.contents(folderId, principal.getRole());
        return new ContentsView(
                result.folders().stream().map(FolderView::of).toList(),
                result.documents().stream().map(DocumentSummary::of).toList(),
                result.breadcrumbs().stream().map(FolderView::of).toList());
    }

    @PostMapping
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Create a folder")
    @SecurityRequirement(name = "csrfToken")
    public FolderView create(@Valid @RequestBody CreateFolderRequest req, @AuthenticationPrincipal AppPrincipal principal) {
        return FolderView.of(folderService.create(req.name(), req.parentId(), req.minRoleRequired(),
                principal.getUserId(), principal.getRole()));
    }

    @PutMapping("/{folderId}")
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Rename a folder or change its minimum role")
    @SecurityRequirement(name = "csrfToken")
    public FolderView rename(@PathVariable UUID folderId, @RequestBody RenameFolderRequest req,
                             @AuthenticationPrincipal AppPrincipal principal) {
        return FolderView.of(folderService.rename(folderId, req.name(), req.minRoleRequired(), principal.getRole()));
    }

    @PutMapping("/{folderId}/move")
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Move a folder")
    @SecurityRequirement(name = "csrfToken")
    public FolderView move(@PathVariable UUID folderId, @RequestBody MoveFolderRequest req,
                           @AuthenticationPrincipal AppPrincipal principal) {
        return FolderView.of(folderService.move(folderId, req.parentId(), principal.getRole()));
    }

    @DeleteMapping("/{folderId}")
    @PreAuthorize("hasRole('WRITE')")
    @Operation(summary = "Delete an empty folder")
    @SecurityRequirement(name = "csrfToken")
    public void delete(@PathVariable UUID folderId, @AuthenticationPrincipal AppPrincipal principal) {
        folderService.delete(folderId, principal.getRole());
    }
}
