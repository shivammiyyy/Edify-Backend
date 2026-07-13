package com.Edify.Backend.auth.service;

import com.Edify.Backend.auth.dto.AuthUserResponse;

public interface AuthService {
    AuthUserResponse getCurrentUser();
    boolean isAuthenticated();
    void logout();
}
