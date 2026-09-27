package com.raiec.auth;

import com.raiec.auth.entity.Role;
import com.raiec.auth.repository.AppUserRepository;
import com.raiec.auth.service.UserAdminService;
import com.raiec.auth.web.dto.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Account administration, and in particular the two ways it could lock everybody out.
 */
@SpringBootTest
@Transactional
class UserAdminServiceTest {

    @Autowired
    private UserAdminService service;
    @Autowired
    private AppUserRepository users;

    private static final String PASSWORD = "a-long-enough-password";

    private UserSummary newFiler(String name) {
        return service.create(name, PASSWORD, Role.FILER, "Test Filer");
    }

    @Test
    void createsAnAccountWithoutEverReturningACredential() {
        UserSummary u = newFiler("filer.one");
        assertEquals("filer.one", u.username());
        assertEquals("FILER", u.role());
        assertTrue(u.enabled());
        // The record has no password field at all; this pins that it stays that way.
        assertFalse(java.util.Arrays.stream(UserSummary.class.getRecordComponents())
                        .anyMatch(c -> c.getName().toLowerCase().contains("password")),
                "a DTO that can carry a credential eventually does");
    }

    @Test
    void storesThePasswordOnlyAsAHash() {
        newFiler("filer.hash");
        String stored = users.findByUsername("filer.hash").orElseThrow().getPasswordHash();
        assertFalse(stored.contains(PASSWORD), "the raw password must not be recoverable");
        assertTrue(stored.startsWith("$2"), "expected a BCrypt hash, got " + stored);
    }

    @Test
    void refusesADuplicateUsername() {
        newFiler("filer.dup");
        var e = assertThrows(IllegalArgumentException.class, () -> newFiler("filer.dup"));
        assertTrue(e.getMessage().contains("already exists"));
    }

    @Test
    void refusesAPasswordShortEnoughToGuess() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.create("filer.weak", "short", Role.FILER, null));
        assertTrue(e.getMessage().contains("at least"));
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() {
        UserSummary admin = service.create("admin.self", PASSWORD, Role.ADMIN, "Self");
        // One click away from being locked out of your own deployment.
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled(admin.id(), false, "admin.self"));
        assertTrue(e.getMessage().contains("cannot"), e.getMessage());
    }

    @Test
    void anAdminCannotDemoteThemselves() {
        UserSummary admin = service.create("admin.demote", PASSWORD, Role.ADMIN, "Self");
        assertThrows(IllegalArgumentException.class,
                () -> service.setRole(admin.id(), Role.FILER, "admin.demote"));
    }

    @Test
    void theLastEnabledAdminCannotBeRemoved() {
        // Disable every other admin first, so the one left really is the last.
        users.findAll().stream()
                .filter(u -> u.getRole() == Role.ADMIN)
                .forEach(u -> { u.setEnabled(false); users.save(u); });
        UserSummary only = service.create("admin.only", PASSWORD, Role.ADMIN, "Only");

        var disable = assertThrows(IllegalArgumentException.class,
                () -> service.setEnabled(only.id(), false, "someone.else"));
        assertTrue(disable.getMessage().toLowerCase().contains("administrator"), disable.getMessage());

        assertThrows(IllegalArgumentException.class,
                () -> service.setRole(only.id(), Role.OFFICER, "someone.else"));
    }

    @Test
    void anotherAdminCanStillBeDisabledWhenOneRemains() {
        service.create("admin.keep", PASSWORD, Role.ADMIN, "Keep");
        UserSummary spare = service.create("admin.spare", PASSWORD, Role.ADMIN, "Spare");
        // The guard must block only the *last* admin, not admins in general.
        assertFalse(service.setEnabled(spare.id(), false, "admin.keep").enabled());
    }

    @Test
    void resettingAPasswordChangesTheStoredHash() {
        UserSummary u = newFiler("filer.reset");
        String before = users.findByUsername("filer.reset").orElseThrow().getPasswordHash();
        service.resetPassword(u.id(), "a-different-long-password");
        String after = users.findByUsername("filer.reset").orElseThrow().getPasswordHash();
        assertFalse(before.equals(after));
    }

    @Test
    void adminsAreListedBeforeOfficersAndFilers() {
        newFiler("zz.filer");
        service.create("aa.officer", PASSWORD, Role.OFFICER, "Officer");
        var roles = service.list().stream().map(UserSummary::role).toList();
        // The list answers "who can decide things", so that end comes first.
        assertEquals("ADMIN", roles.get(0), "expected an admin first, got " + roles);
        assertTrue(roles.indexOf("OFFICER") < roles.lastIndexOf("FILER"));
    }
}
