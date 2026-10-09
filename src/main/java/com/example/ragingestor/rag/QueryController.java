package com.example.ragingestor.rag;

import com.example.ragingestor.security.AppPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/query")
@Tag(name = "RAG search", description = "Synchronous authorization-aware retrieval and grounded answers")
public class QueryController {

    private final RagOrchestrator ragOrchestrator;

    public QueryController(RagOrchestrator ragOrchestrator) {
        this.ragOrchestrator = ragOrchestrator;
    }

    public record QueryRequest(@NotBlank @Size(max = 4000) String question,
                               @Size(max = 120) String department,
                               @Size(max = 80) String documentType,
                               @Size(max = 10) List<@Pattern(regexp = "[A-Za-z0-9 _-]{1,64}") String> tags,
                               UUID ownerId) {}

    @PostMapping
    @PreAuthorize("hasRole('READ')")
    @Operation(summary = "Search accessible documents and return an answer with source citations")
    @SecurityRequirement(name = "csrfToken")
    public RagOrchestrator.RagAnswer query(@Valid @RequestBody QueryRequest request,
                                            @AuthenticationPrincipal AppPrincipal principal) {
        return ragOrchestrator.answer(request.question(), principal.getRole(),
                new RetrievalFilters(request.department(), request.documentType(), request.tags(), request.ownerId()));
    }
}
