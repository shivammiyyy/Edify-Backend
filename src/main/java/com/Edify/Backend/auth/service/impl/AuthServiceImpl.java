package com.Edify.Backend.auth.service.impl;

import com.Edify.Backend.User.entity.User;
import com.Edify.Backend.auth.SessionService;
import com.Edify.Backend.auth.dto.AuthUserResponse;
import com.Edify.Backend.auth.service.AuthService;
import com.Edify.Backend.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final CurrentUserService currentUserService;

    private final SessionService sessionService;


    @Override
    public AuthUserResponse getCurrentUser() {

        User user = currentUserService.getCurrentUser();

        if (user == null) {
            return null;
        }

        return AuthUserResponse.builder()

                .id(user.getId())

                .email(user.getEmail())

                .name(user.getName())

                .pictureUrl(user.getPictureUrl())

                .provider(user.getProvider())

                .role(user.getRole())

                .emailVerified(user.isEmailVerified())

                .build();
    }

    @Override
    public boolean isAuthenticated() {

        return currentUserService.isAuthenticated();

    }

    @Override
    public void logout() {

        sessionService.invalidateSession();

    }
}
