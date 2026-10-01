package com.example.ragingestor.rag;

import com.example.ragingestor.security.AppPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final RagOrchestrator ragOrchestrator;

    public QueryController(RagOrchestrator ragOrchestrator) {
        this.ragOrchestrator = ragOrchestrator;
    }

    public record QueryRequest(@NotBlank String question) {}

    @PostMapping
    @PreAuthorize("hasRole('READ')")
    public RagOrchestrator.RagAnswer query(@Valid @RequestBody QueryRequest request,
                                            @AuthenticationPrincipal AppPrincipal principal) {
        return ragOrchestrator.answer(request.question(), principal.getRole());
    }
}
