package com.raiec.common.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liveness endpoint for the hosting platform.
 *
 * <p>Deliberately public and deliberately 200: a platform health check treats any non-2xx
 * as unhealthy and will restart or fail the deploy. Pointing a health check at a secured
 * endpoint therefore fails permanently, because a correct 401 is indistinguishable from a
 * broken service as far as the platform is concerned.
 *
 * <p>It also touches the database, so a green check means the app can actually serve a
 * request rather than merely that the process is alive with a dead connection pool.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("time", Instant.now().toString());
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            body.put("database", "UP");
        } catch (RuntimeException e) {
            // Still 200: the process is serving. The payload reports the degradation so a
            // human reading it can tell the difference, without the platform killing a
            // container that is merely waiting on the database to come back.
            body.put("database", "DOWN");
        }
        return body;
    }
}
