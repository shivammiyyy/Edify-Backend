package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    private String displayName;

    /** OAuth2 provider that authenticated this user, e.g. "google". */
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.READ;

    /** True only for the single hardcoded bootstrap admin row; prevents demotion via the API. */
    @Column(nullable = false)
    private boolean systemAdmin = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    public AppUser(String email, String displayName, String provider, Role role) {
        this.email = email;
        this.displayName = displayName;
        this.provider = provider;
        this.role = role;
    }
}
