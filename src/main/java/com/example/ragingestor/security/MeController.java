package com.example.ragingestor.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    public record Me(String email, Role role) {}

    @GetMapping("/api/me")
    public Me me(@AuthenticationPrincipal AppPrincipal principal) {
        return new Me(principal.getEmail(), principal.getRole());
    }
}
