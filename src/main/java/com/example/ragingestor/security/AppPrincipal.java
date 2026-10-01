package com.example.ragingestor.security;

import java.util.UUID;

public interface AppPrincipal {
    UUID getUserId();
    String getEmail();
    Role getRole();
}
