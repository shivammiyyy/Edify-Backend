package com.example.ragingestor.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@Tag(name = "Authentication", description = "Current-user session and CSRF bootstrap endpoints")
public class MeController {

    public record Me(String email, Role role) {}

    @GetMapping("/api/me")
    @Operation(summary = "Get the signed-in user's identity and role")
    public Me me(@AuthenticationPrincipal AppPrincipal principal) {
        return new Me(principal.getEmail(), principal.getRole());
    }
}
