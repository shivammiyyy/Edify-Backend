package com.Edify.Backend.auth.controllers;


import com.Edify.Backend.auth.dto.AuthStatusResponse;
import com.Edify.Backend.auth.dto.AuthUserResponse;
import com.Edify.Backend.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me() {

        return ResponseEntity.ok(
                authService.getCurrentUser()
        );

    }

    @GetMapping("/status")
    public ResponseEntity<AuthStatusResponse> status() {

        return ResponseEntity.ok(
                new AuthStatusResponse(
                        authService.isAuthenticated()
                )
        );

    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {

        authService.logout();

        return ResponseEntity.ok().build();

    }

}
