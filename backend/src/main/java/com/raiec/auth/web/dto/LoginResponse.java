package com.raiec.auth.web.dto;

public record LoginResponse(String token, String username, String role, String displayName) {
}
