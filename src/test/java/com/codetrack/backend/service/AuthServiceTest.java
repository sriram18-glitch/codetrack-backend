package com.codetrack.backend.service;

import com.codetrack.backend.dto.AuthResponse;
import com.codetrack.backend.dto.LoginRequest;
import com.codetrack.backend.entity.Admin;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.AdminRepository;
import com.codetrack.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(adminRepository, authenticationManager, jwtService, passwordEncoder);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        LoginRequest request = new LoginRequest("admin@codetrack.local", "Admin@12345");
        Admin admin = Admin.builder()
                .id(UUID.randomUUID())
                .email("admin@codetrack.local")
                .passwordHash("hashed")
                .fullName("Default Admin")
                .enabled(true)
                .build();

        when(adminRepository.findByEmailIgnoreCase("admin@codetrack.local")).thenReturn(Optional.of(admin));
        when(jwtService.generateToken(admin)).thenReturn("fake-jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(86_400L);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("fake-jwt-token");
        assertThat(response.admin().email()).isEqualTo("admin@codetrack.local");
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void loginPropagatesBadCredentialsException() {
        LoginRequest request = new LoginRequest("admin@codetrack.local", "wrong-password");
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void loginThrowsWhenAuthenticatedAdminCannotBeFound() {
        LoginRequest request = new LoginRequest("ghost@codetrack.local", "Admin@12345");
        when(adminRepository.findByEmailIgnoreCase("ghost@codetrack.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid credentials");
    }
}
