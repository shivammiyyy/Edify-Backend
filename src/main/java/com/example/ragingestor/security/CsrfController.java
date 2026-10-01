package com.example.ragingestor.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {

    public record CsrfView(String headerName, String token) {}

    @GetMapping("/api/csrf")
    public CsrfView csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token == null) {
            throw new IllegalStateException("CSRF token was not initialized");
        }
        return new CsrfView(token.getHeaderName(), token.getToken());
    }
}
