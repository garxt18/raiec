package com.raiec.common.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS for the static frontend, which is served from a different origin than the API
 * (a local dev server during development, the hosted frontend domain in production).
 *
 * <p>Origins come from {@code raiec.cors.allowed-origins} so a deployment can be locked to the
 * real frontend domain without a code change. The default stays permissive for local development;
 * set {@code RAIEC_CORS_ORIGINS} in any deployed environment, e.g.
 * {@code RAIEC_CORS_ORIGINS=https://raiec.vercel.app}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOriginPatterns;

    public WebConfig(@Value("${raiec.cors.allowed-origins:*}") String allowedOrigins) {
        this.allowedOriginPatterns = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
