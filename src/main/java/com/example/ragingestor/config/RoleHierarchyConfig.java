package com.example.ragingestor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

@Configuration
public class RoleHierarchyConfig {

    @Bean
    public RoleHierarchy roleHierarchy() {
        // ADMIN can do everything CUSTODIAN can, CUSTODIAN everything WRITE can, WRITE everything READ can.
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_ADMIN > ROLE_CUSTODIAN
                ROLE_CUSTODIAN > ROLE_WRITE
                ROLE_WRITE > ROLE_READ
                """);
    }

    // Makes the hierarchy apply to @PreAuthorize / @PostAuthorize checks too, not just
    // the HttpSecurity authorizeHttpRequests() rules.
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }
}
