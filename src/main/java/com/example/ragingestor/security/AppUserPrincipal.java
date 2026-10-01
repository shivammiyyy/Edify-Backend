package com.example.ragingestor.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The principal placed in the SecurityContext after a successful OAuth2 login.
 * Wraps the raw OAuth2 attributes but authorizes strictly off our own DB role,
 * never off anything the identity provider claims.
 */
public class AppUserPrincipal extends DefaultOAuth2User implements AppPrincipal {

    private final UUID userId;
    private final String email;
    private final Role role;

    public AppUserPrincipal(UUID userId, String email, Role role, Map<String, Object> attributes, String nameAttributeKey) {
        super(authorities(role), attributes, nameAttributeKey);
        this.userId = userId;
        this.email = email;
        this.role = role;
    }

    private static Collection<GrantedAuthority> authorities(Role role) {
        // Single authority; RoleHierarchy (see SecurityConfig) expands READ/WRITE/CUSTODIAN/ADMIN
        // into the correct "at least this privileged" set at authorization time.
        return java.util.List.of(new SimpleGrantedAuthority(role.authority()));
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }
}
