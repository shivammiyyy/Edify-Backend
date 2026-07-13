package com.Edify.Backend.auth.dto;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.Domain.Role;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class AuthUserResponse {
    private UUID id;

    private String name;

    private String email;

    private String pictureUrl;

    private AuthProvider provider;

    private Role role;

    private boolean emailVerified;
}
