package com.codetrack.backend.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AdminSummary admin
) {
    public static AuthResponse of(String token, long expiresInSeconds, AdminSummary admin) {
        return new AuthResponse(token, "Bearer", expiresInSeconds, admin);
    }
}
