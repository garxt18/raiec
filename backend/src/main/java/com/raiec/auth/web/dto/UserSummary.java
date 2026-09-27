package com.raiec.auth.web.dto;

import com.raiec.auth.entity.AppUser;

import java.time.Instant;

/**
 * An account as the administration screen sees it.
 *
 * <p>Deliberately has no password field of any kind, not even the hash. A DTO that can
 * carry a credential eventually does, and the hash is of no use to any screen — the only
 * question an administrator asks of it is whether the account works, which {@code enabled}
 * already answers.
 */
public record UserSummary(
        Long id,
        String username,
        String displayName,
        String role,
        boolean enabled,
        Instant createdAt
) {
    public static UserSummary from(AppUser u) {
        return new UserSummary(
                u.getId(),
                u.getUsername(),
                u.getDisplayName(),
                u.getRole() != null ? u.getRole().name() : null,
                u.isEnabled(),
                u.getCreatedAt());
    }
}
