package com.example.ragingestor.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final CustomOAuth2UserService appUserService;

    public CustomOidcUserService(CustomOAuth2UserService appUserService) {
        this.appUserService = appUserService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);
        String nameAttributeKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();
        if (nameAttributeKey == null || nameAttributeKey.isBlank()) {
            nameAttributeKey = "sub";
        }
        AppUserPrincipal appUser = appUserService.provisionUser(
                oidcUser,
                userRequest.getClientRegistration().getRegistrationId(),
                nameAttributeKey);

        return new AppOidcUserPrincipal(
                appUser.getAuthorities(),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                nameAttributeKey,
                appUser);
    }
}
