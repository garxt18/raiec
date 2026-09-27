package com.raiec.common.web;

import com.raiec.auth.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security.
 *
 * <ul>
 *   <li>{@code /api/auth/**} — public (login).</li>
 *   <li>{@code /api/health} — public. The hosting platform's health check treats any
 *       non-2xx as unhealthy, so this cannot require a token.</li>
 *   <li>{@code /api/reference/import/**} — ADMIN only (manage rate books).</li>
 *   <li>{@code POST /api/tenders/*&#47;approve|reject|send-to-review} — OFFICER or ADMIN.</li>
 *   <li>everything else under {@code /api/**} — any authenticated user.</li>
 * </ul>
 *
 * Unauthenticated requests get a 401 (no redirect), so the SPA/static frontend can react.
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/health", "/error").permitAll()
                        .requestMatchers("/api/reference/import/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Anyone signed in may READ the thresholds - the officer needs to see
                        // what a verdict was measured against - but only an admin may change
                        // them, since they apply department-wide.
                        .requestMatchers(HttpMethod.PUT, "/api/settings/**").hasRole("ADMIN")
                        // A hand-entered rate becomes a benchmark for every future tender,
                        // so it carries the same weight as one earned through an approval.
                        .requestMatchers(HttpMethod.POST, "/api/lar").hasRole("ADMIN")
                        // The decisions themselves. A FILER is deliberately excluded: the
                        // department that files an estimate must not be the one that passes
                        // it, which is the separation the whole vetting step exists for.
                        .requestMatchers(HttpMethod.POST,
                                "/api/tenders/*/approve",
                                "/api/tenders/*/reject",
                                "/api/tenders/*/send-to-review",
                                "/api/tenders/*/request-info").hasAnyRole("OFFICER", "ADMIN")
                        // Removing a tender destroys its audit trail along with it, so it
                        // sits with the other department-wide powers rather than with the
                        // everyday ones.
                        .requestMatchers(HttpMethod.DELETE, "/api/tenders/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(401, "Unauthorized")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
