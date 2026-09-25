package com.pulseops.auth;

import com.pulseops.tenant.Tenant;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String companyName,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "must be 8-72 characters") String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserView(Long id, String email, String fullName, Role role) {
        static UserView of(User user) {
            return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
        }
    }

    public record TenantView(Long id, String name, String slug) {
        static TenantView of(Tenant tenant) {
            return new TenantView(tenant.getId(), tenant.getName(), tenant.getSlug());
        }
    }

    /** {@code apiKey} is only non-null right after registration: it is the one time the raw key is revealed. */
    public record AuthResponse(String token, UserView user, TenantView tenant, String apiKey) {
    }

    public record MeResponse(UserView user, TenantView tenant) {
    }
}
