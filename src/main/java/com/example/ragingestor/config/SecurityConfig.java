package com.example.ragingestor.config;

import com.example.ragingestor.security.CustomOAuth2UserService;
import com.example.ragingestor.security.CustomOidcUserService;
import com.example.ragingestor.security.ApiSecurityErrorHandler;
import com.example.ragingestor.security.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Stateful, session-based auth: Spring Security's servlet session (JSESSIONID cookie),
 * NOT SessionCreationPolicy.STATELESS / JWT. OAuth2 is used purely as the *login*
 * mechanism (delegated authentication to Google/Okta/etc.); once authenticated, a
 * regular HttpSession is what carries the user across requests.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;
    private final String oauthSuccessUrl;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          CustomOidcUserService customOidcUserService,
                          @Value("${app.security.oauth-success-url:/}") String oauthSuccessUrl) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.customOidcUserService = customOidcUserService;
        this.oauthSuccessUrl = oauthSuccessUrl;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        ApiSecurityErrorHandler securityErrorHandler = new ApiSecurityErrorHandler(objectMapper);
        http
            .cors(cors -> {})
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/login/**", "/oauth2/**", "/error", "/actuator/health",
                        "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg", "/icons.svg").permitAll()
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/documents").hasAnyRole("READ", "WRITE", "CUSTODIAN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/documents/**").hasAnyRole("READ", "WRITE", "CUSTODIAN", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/folders/**").hasAnyRole("READ", "WRITE", "CUSTODIAN", "ADMIN")
                // State allowed roles explicitly; URL authorization must not rely on
                // method-security role-hierarchy expansion.
                .requestMatchers("/api/documents/**").hasAnyRole("WRITE", "CUSTODIAN", "ADMIN")
                .requestMatchers("/api/folders/**").hasAnyRole("WRITE", "CUSTODIAN", "ADMIN")
                .requestMatchers("/api/admin/users/**").hasAnyRole("CUSTODIAN", "ADMIN")
                .requestMatchers("/api/ingestion-jobs/**").hasAnyRole("READ", "WRITE", "CUSTODIAN", "ADMIN")
                .requestMatchers("/api/query/**").hasAnyRole("READ", "WRITE", "CUSTODIAN", "ADMIN")
                .requestMatchers("/api/conversations/**").hasAnyRole("READ", "WRITE", "CUSTODIAN", "ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService)
                    .oidcUserService(customOidcUserService))
                .defaultSuccessUrl(oauthSuccessUrl, true)
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(securityErrorHandler)
                .accessDeniedHandler(securityErrorHandler)
            )
            // CSRF stays ON because this is cookie/session based, not a stateless bearer-token API.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.migrateSession())
                .maximumSessions(3)
                .expiredUrl("/login?expired")
            )
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(options -> {})
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
            )
            .addFilterBefore(new CorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Required so maximumSessions() concurrency control is told when a session actually dies.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}
