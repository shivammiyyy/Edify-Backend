package com.example.ragingestor.security;

import com.example.ragingestor.domain.AppUser;
import com.example.ragingestor.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Runs on every OAuth2 login. Responsibilities:
 *  - find-or-create the local AppUser row keyed by email
 *  - the ONE hardcoded admin email always resolves to ADMIN + systemAdmin=true
 *  - every other brand-new user is provisioned with READ only; an admin/custodian
 *    must explicitly upgrade them afterwards via RoleAssignmentController
 *  - role is re-read from the DB on every login, so a role change takes effect
 *    the next time the user signs in (or immediately, since we look it up fresh
 *    right here rather than trusting anything cached from a prior session)
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final AppUserRepository appUserRepository;
    private final String hardcodedAdminEmail;

    public CustomOAuth2UserService(AppUserRepository appUserRepository,
                                    @Value("${app.security.admin-email}") String hardcodedAdminEmail) {
        this.appUserRepository = appUserRepository;
        this.hardcodedAdminEmail = hardcodedAdminEmail.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (nameAttributeKey == null || nameAttributeKey.isBlank()) {
            nameAttributeKey = "sub";
        }
        return provisionUser(oauthUser, userRequest.getClientRegistration().getRegistrationId(), nameAttributeKey);
    }

    @Transactional
    public AppUserPrincipal provisionUser(OAuth2User oauthUser, String provider, String nameAttributeKey) {
        String oauthEmail = oauthUser.getAttribute("email");
        if (oauthEmail == null || oauthEmail.isBlank()) {
            throw new OAuth2AuthenticationException("OAuth2 provider did not return an email claim");
        }
        String email = oauthEmail.toLowerCase(java.util.Locale.ROOT);
        if (email.equals(hardcodedAdminEmail)
                && !Boolean.TRUE.equals(oauthUser.getAttribute("email_verified"))) {
            throw new OAuth2AuthenticationException("The configured admin email must be verified by the OAuth2 provider");
        }
        String displayName = oauthUser.getAttribute("name") != null ? oauthUser.getAttribute("name") : email;
        AppUser user = appUserRepository.findByEmail(email)
                .orElseGet(() -> new AppUser(email, displayName, provider, Role.READ));

        if (email.equals(hardcodedAdminEmail)) {
            // The single hardcoded admin: always ADMIN, never demotable through this app.
            user.setRole(Role.ADMIN);
            user.setSystemAdmin(true);
        }

        user.setDisplayName(displayName);
        user.setLastLoginAt(Instant.now());
        user = appUserRepository.save(user);

        return new AppUserPrincipal(user.getId(), user.getEmail(), user.getRole(),
                oauthUser.getAttributes(), nameAttributeKey);
    }
}
