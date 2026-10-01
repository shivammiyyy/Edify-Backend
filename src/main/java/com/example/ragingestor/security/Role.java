package com.example.ragingestor.security;

/**
 * Application roles, ordered from least to most privileged.
 * Ordinal order is relied on by {@link com.example.ragingestor.rag.VectorSearchService}
 * to do a cheap "does this user's role outrank the chunk's required role" check,
 * so do not reorder these without updating that comparison.
 */
public enum Role {
    READ,
    WRITE,
    CUSTODIAN,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }

    public boolean atLeast(Role other) {
        return this.ordinal() >= other.ordinal();
    }
}
