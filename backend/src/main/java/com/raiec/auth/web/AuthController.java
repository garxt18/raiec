package com.raiec.auth.web;

import com.raiec.auth.entity.AppUser;
import com.raiec.auth.repository.AppUserRepository;
import com.raiec.auth.service.JwtService;
import com.raiec.auth.web.dto.LoginRequest;
import com.raiec.auth.web.dto.LoginResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req == null || req.username() == null || req.password() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and password are required"));
        }
        AppUser user = users.findByUsername(req.username().trim()).orElse(null);
        if (user == null || !user.isEnabled() || !encoder.matches(req.password(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid username or password"));
        }
        String token = jwt.generate(user.getUsername(), user.getRole().name());
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
        return ResponseEntity.ok(new LoginResponse(token, user.getUsername(), user.getRole().name(), displayName));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        String role = auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "")).orElse("");
        return ResponseEntity.ok(Map.of("username", auth.getName(), "role", role));
    }
}
