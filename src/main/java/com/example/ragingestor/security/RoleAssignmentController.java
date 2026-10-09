package com.example.ragingestor.security;

import com.example.ragingestor.domain.AppUser;
import com.example.ragingestor.domain.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Access management", description = "Custodian and administrator user role management")
public class RoleAssignmentController {

    private final AppUserRepository appUserRepository;

    public RoleAssignmentController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public record RoleChangeRequest(@NotNull Role role) {}
    public record UserView(UUID id, String email, String displayName, Role role, boolean systemAdmin) {
        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(), u.isSystemAdmin());
        }
    }

    // hasRole("CUSTODIAN") at the SecurityConfig layer already keeps WRITE/READ out entirely.
    @GetMapping
    @PreAuthorize("hasRole('CUSTODIAN')")
    @Operation(summary = "List registered users")
    public List<UserView> listUsers() {
        return appUserRepository.findAll().stream().map(UserView::of).toList();
    }

    /**
     * ADMIN can set any role (READ/WRITE/CUSTODIAN/ADMIN).
     * CUSTODIAN can ONLY grant/revoke WRITE (per spec: "CUSTODIAN can give access for Write").
     * Nobody, including ADMIN, can edit the hardcoded systemAdmin row through this endpoint.
     */
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('CUSTODIAN')")
    @Operation(summary = "Change a user's application role")
    @SecurityRequirement(name = "csrfToken")
    public UserView changeRole(@PathVariable UUID userId,
                                @Valid @RequestBody RoleChangeRequest request,
                                java.security.Principal principal) {
        AppUser target = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found"));

        if (target.isSystemAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "the hardcoded admin's role cannot be changed");
        }

        boolean callerIsAdmin = isAdmin(principal);
        if (!callerIsAdmin) {
            // Caller is CUSTODIAN (guaranteed by @PreAuthorize above) but not ADMIN.
            if (request.role() != Role.WRITE && request.role() != Role.READ) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "custodians may only grant or revoke WRITE access");
            }
            if (target.getRole() == Role.CUSTODIAN || target.getRole() == Role.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "custodians cannot modify CUSTODIAN or ADMIN users");
            }
        }

        target.setRole(request.role());
        return UserView.of(appUserRepository.save(target));
    }

    private boolean isAdmin(java.security.Principal principal) {
        if (principal instanceof org.springframework.security.authentication.AbstractAuthenticationToken token) {
            return token.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}
