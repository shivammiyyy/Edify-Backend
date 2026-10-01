package com.example.ragingestor.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import java.util.Collection;
import java.util.UUID;

public class AppOidcUserPrincipal extends DefaultOidcUser implements AppPrincipal {

    private final UUID userId;
    private final String email;
    private final Role role;

    public AppOidcUserPrincipal(Collection<? extends GrantedAuthority> authorities,
                                OidcIdToken idToken,
                                OidcUserInfo userInfo,
                                String nameAttributeKey,
                                AppUserPrincipal appUser) {
        super(authorities, idToken, userInfo, nameAttributeKey);
        this.userId = appUser.getUserId();
        this.email = appUser.getEmail();
        this.role = appUser.getRole();
    }

    @Override
    public UUID getUserId() {
        return userId;
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public Role getRole() {
        return role;
    }
}
