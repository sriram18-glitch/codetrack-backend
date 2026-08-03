package com.codetrack.backend.security;

import com.codetrack.backend.entity.Admin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256!!";
    private JwtService jwtService;
    private Admin admin;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3_600_000L);
        admin = Admin.builder()
                .id(UUID.randomUUID())
                .email("admin@codetrack.local")
                .passwordHash("hashed")
                .enabled(true)
                .build();
    }

    @Test
    void generatesTokenContainingEmail() {
        String token = jwtService.generateToken(admin);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin@codetrack.local");
    }

    @Test
    void tokenIsValidForTheAdminItWasIssuedTo() {
        String token = jwtService.generateToken(admin);
        assertThat(jwtService.isTokenValid(token, admin)).isTrue();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService shortLived = new JwtService(SECRET, 1L);
        String token = shortLived.generateToken(admin);
        Thread.sleep(20);
        assertThat(shortLived.isTokenValid(token, admin)).isFalse();
    }
}
