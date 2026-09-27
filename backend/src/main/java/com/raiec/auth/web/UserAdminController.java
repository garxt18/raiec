package com.raiec.auth.web;

import com.raiec.auth.entity.Role;
import com.raiec.auth.service.UserAdminService;
import com.raiec.auth.web.dto.UserSummary;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Account administration. Restricted to ADMIN by the {@code /api/admin/**} rule in
 * {@link com.raiec.common.web.SecurityConfig}.
 *
 * <p>Passwords arrive here and go no further than the encoder; nothing this controller
 * returns contains one.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    public record NewUserRequest(String username, String password, String role, String displayName) {
    }

    public record RoleRequest(String role) {
    }

    public record EnabledRequest(Boolean enabled) {
    }

    public record PasswordRequest(String password) {
    }

    @GetMapping
    public List<UserSummary> list() {
        return service.list();
    }

    /** The roles the interface may offer, so the list cannot drift from the enum. */
    @GetMapping("/roles")
    public List<String> roles() {
        return java.util.Arrays.stream(Role.values()).map(Enum::name).toList();
    }

    @PostMapping
    public UserSummary create(@RequestBody NewUserRequest req) {
        return service.create(req.username(), req.password(), parseRole(req.role()), req.displayName());
    }

    @PutMapping("/{id}/role")
    public UserSummary setRole(@PathVariable Long id, @RequestBody RoleRequest req, Authentication auth) {
        return service.setRole(id, parseRole(req.role()), auth != null ? auth.getName() : null);
    }

    @PutMapping("/{id}/enabled")
    public UserSummary setEnabled(@PathVariable Long id, @RequestBody EnabledRequest req, Authentication auth) {
        if (req == null || req.enabled() == null) {
            throw new IllegalArgumentException("Specify whether the account should be enabled.");
        }
        return service.setEnabled(id, req.enabled(), auth != null ? auth.getName() : null);
    }

    @PutMapping("/{id}/password")
    public UserSummary resetPassword(@PathVariable Long id, @RequestBody PasswordRequest req) {
        return service.resetPassword(id, req == null ? null : req.password());
    }

    private static Role parseRole(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("A role is required.");
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a role. Use FILER, OFFICER or ADMIN.");
        }
    }
}
