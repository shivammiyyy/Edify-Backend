package com.Edify.Backend.security;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.Domain.Role;

import com.Edify.Backend.User.entity.User;
import com.Edify.Backend.User.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request)
            throws OAuth2AuthenticationException {

        OAuth2User oauthUser =
                new DefaultOAuth2UserService().loadUser(request);

        String registrationId =
                request.getClientRegistration().getRegistrationId();

        AuthProvider provider =
                AuthProvider.valueOf(registrationId.toUpperCase());

        String providerId = oauthUser.getName();

        String email = oauthUser.getAttribute("email");

        String name = oauthUser.getAttribute("name");

        String picture = oauthUser.getAttribute("picture");

        Boolean verified =
                oauthUser.getAttribute("email_verified");

        User user = userService.findByEmail(email)
                .orElseGet(() -> {

                    User newUser = User.builder()
                            .email(email)
                            .name(name)
                            .pictureUrl(picture)
                            .provider(provider)
                            .providerUserId(providerId)
                            .emailVerified(Boolean.TRUE.equals(verified))
                            .role(Role.READER)
                            .build();

                    return userService.createUser(newUser);

                });

        user.setName(name);
        user.setPictureUrl(picture);
        user.setLastLogin(LocalDateTime.now());

        userService.updateUser(user);

        return new CustomOAuth2User(user, oauthUser);
    }
}