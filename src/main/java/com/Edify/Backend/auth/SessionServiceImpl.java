package com.Edify.Backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class SessionServiceImpl implements SessionService {

    private final HttpServletRequest request;

    public SessionServiceImpl(HttpServletRequest request) {
        this.request = request;
    }


    @Override
    public String getSessionId() {
        if(request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        return session != null ? session.getId() : null;
    }

    @Override
    public void invalidateSession() {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

    }

    @Override
    public boolean isSessionActive() {

        return request.getSession(false) != null;

    }
}
