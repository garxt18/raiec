package com.raiec.auth.service;

import com.raiec.auth.entity.AppUser;
import com.raiec.auth.entity.Role;
import com.raiec.auth.repository.AppUserRepository;
import com.raiec.auth.web.dto.UserSummary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;

/**
 * Creating, disabling and re-roling accounts.
 *
 * <p>Until this existed the only way to add a user was to restart the service with new
 * seed environment variables, and the only way to remove one was to edit the database by
 * hand. Both of those are worse than they sound: the seed only creates an account when the
 * username does not already exist, so changing a seeded password did nothing at all, and
 * an account that could not be removed through the application was usually not removed.
 *
 * <p>Two rules are enforced here rather than left to the caller, because both are the kind
 * of mistake that is only noticed once nobody can sign in: an administrator cannot disable
 * or demote their own account, and the last enabled administrator cannot be removed.
 */
@Service
public class UserAdminService {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public UserAdminService(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Short enough to remember, long enough not to be guessed in an afternoon. */
    private static final int MIN_PASSWORD_LENGTH = 10;

    @Transactional(readOnly = true)
    public List<UserSummary> list() {
        return users.findAll().stream()
                // Administrators first, then alphabetical: the list is read to answer "who
                // can decide things", and that question is about the top of it.
                .sorted(Comparator
                        .comparing((AppUser u) -> u.getRole() == null ? 99 : switch (u.getRole()) {
                            case ADMIN -> 0;
                            case OFFICER -> 1;
                            case FILER -> 2;
                        })
                        .thenComparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(UserSummary::from)
                .toList();
    }

    @Transactional
    public UserSummary create(String username, String rawPassword, Role role, String displayName) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) throw new IllegalArgumentException("A username is required.");
        if (name.length() > 64) throw new IllegalArgumentException("That username is too long (64 characters maximum).");
        if (users.findByUsername(name).isPresent()) {
            throw new IllegalArgumentException("An account named '" + name + "' already exists.");
        }
        requireUsablePassword(rawPassword);
        if (role == null) throw new IllegalArgumentException("A role is required.");

        AppUser saved = users.save(AppUser.builder()
                .username(name)
                .passwordHash(encoder.encode(rawPassword))
                .role(role)
                .displayName(displayName == null || displayName.isBlank() ? name : displayName.trim())
                .enabled(true)
                .build());
        return UserSummary.from(saved);
    }

    @Transactional
    public UserSummary setEnabled(Long id, boolean enabled, String actingUsername) {
        AppUser user = require(id);
        if (!enabled) {
            refuseSelfHarm(user, actingUsername, "disable your own account");
            refuseLastAdmin(user, "Disabling this account would leave no administrator who can sign in.");
        }
        user.setEnabled(enabled);
        return UserSummary.from(users.save(user));
    }

    @Transactional
    public UserSummary setRole(Long id, Role role, String actingUsername) {
        if (role == null) throw new IllegalArgumentException("A role is required.");
        AppUser user = require(id);
        if (role != Role.ADMIN) {
            refuseSelfHarm(user, actingUsername, "change your own role");
            refuseLastAdmin(user, "Changing this account's role would leave no administrator.");
        }
        user.setRole(role);
        return UserSummary.from(users.save(user));
    }

    /**
     * Sets a new password for someone who has lost theirs.
     *
     * <p>There is no "current password" argument because an administrator does not know it
     * and should not need to. That is a real power, which is why it is confined to this
     * role and why the change lands in the audit trail through the caller.
     */
    @Transactional
    public UserSummary resetPassword(Long id, String rawPassword) {
        requireUsablePassword(rawPassword);
        AppUser user = require(id);
        user.setPasswordHash(encoder.encode(rawPassword));
        return UserSummary.from(users.save(user));
    }

    private AppUser require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No account with id " + id + "."));
    }

    private void requireUsablePassword(String raw) {
        if (raw == null || raw.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "The password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    /** Stops an administrator locking themselves out in a single click. */
    private void refuseSelfHarm(AppUser target, String actingUsername, String what) {
        if (actingUsername != null && actingUsername.equals(target.getUsername())) {
            throw new IllegalArgumentException(
                    "You cannot " + what + ". Ask another administrator to do it.");
        }
    }

    private void refuseLastAdmin(AppUser target, String message) {
        if (target.getRole() != Role.ADMIN || !target.isEnabled()) return;
        long remaining = users.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN && u.isEnabled())
                .filter(u -> !u.getId().equals(target.getId()))
                .count();
        if (remaining == 0) throw new IllegalArgumentException(message);
    }
}
