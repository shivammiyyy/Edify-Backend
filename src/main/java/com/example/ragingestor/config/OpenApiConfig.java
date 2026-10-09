package com.example.ragingestor.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Edify API",
                version = "v1",
                description = """
                        REST API for the Edify knowledge workspace.
                        Authentication uses the browser's OAuth2/OIDC-backed HTTP session cookie.
                        Send the value returned by GET /api/csrf in X-CSRF-TOKEN for state-changing requests.
                        Role and resource-level authorization are enforced by the backend.
                        """,
                contact = @Contact(name = "Edify project")
        )
)
@SecurityScheme(
        name = "csrfToken",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.HEADER,
        paramName = "X-CSRF-TOKEN",
        description = "Fetch a token from GET /api/csrf after signing in. The same-origin browser session cookie is sent automatically."
)
public class OpenApiConfig {
}
