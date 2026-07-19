package com.Edify.Backend.document.controller;


import com.Edify.Backend.commons.responses.PageResponse;
import com.Edify.Backend.document.dto.DocumentResponse;
import com.Edify.Backend.document.dto.UpdateDocumentRequest;
import com.Edify.Backend.document.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(

            @RequestParam String title,

            @RequestPart MultipartFile file

    ) throws IOException {

        return ResponseEntity.ok(
                documentService.upload(title, file)
        );
    }

    @GetMapping
    public ResponseEntity<PageResponse<DocumentResponse>> getAll(

            @PageableDefault(
                    size = 10,
                    sort = "uploadedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable

    ) {

        return ResponseEntity.ok(
                documentService.getDocuments(pageable)
        );

    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getOne(

            @PathVariable UUID id

    ) {

        return ResponseEntity.ok(
                documentService.getDocument(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> update(

            @PathVariable UUID id,

            @RequestBody
            @Valid
            UpdateDocumentRequest request

    ) throws IOException {

        return ResponseEntity.ok(
                documentService.updateDocument(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(

            @PathVariable UUID id

    ) throws IOException {

        documentService.deleteDocument(id);

        return ResponseEntity.noContent().build();
    }

}