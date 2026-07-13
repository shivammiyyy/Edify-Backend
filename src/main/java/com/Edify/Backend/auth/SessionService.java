package com.Edify.Backend.auth;

public interface SessionService {
    String getSessionId();
    void invalidateSession();
    boolean isSessionActive();
}
