package com.raiec.auth.config;

import com.raiec.auth.entity.AppUser;
import com.raiec.auth.entity.Role;
import com.raiec.auth.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds default Admin and Officer accounts on startup (idempotent, per username).
 * Credentials are configurable via {@code raiec.seed.*} properties; defaults are for
 * local/demo use only and should be changed for any real deployment.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    @Value("${raiec.seed.admin-username:admin}")
    private String adminUsername;
    @Value("${raiec.seed.admin-password:admin@123}")
    private String adminPassword;
    @Value("${raiec.seed.officer-username:officer}")
    private String officerUsername;
    @Value("${raiec.seed.officer-password:officer@123}")
    private String officerPassword;

    public DataInitializer(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        seed(adminUsername, adminPassword, Role.ADMIN, "Administrator");
        seed(officerUsername, officerPassword, Role.OFFICER, "Reviewing Officer");
    }

    private void seed(String username, String rawPassword, Role role, String displayName) {
        if (users.findByUsername(username).isEmpty()) {
            users.save(AppUser.builder()
                    .username(username)
                    .passwordHash(encoder.encode(rawPassword))
                    .role(role)
                    .displayName(displayName)
                    .enabled(true)
                    .build());
        }
    }
}
