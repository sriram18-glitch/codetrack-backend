package com.codetrack.backend.controller;

import com.codetrack.backend.dto.AuthResponse;
import com.codetrack.backend.dto.ChangeEmailRequest;
import com.codetrack.backend.dto.ChangePasswordRequest;
import com.codetrack.backend.dto.LoginRequest;
import com.codetrack.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Admin login and token issuance")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate as an admin and receive a JWT access token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the authenticated admin's password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-email")
    @Operation(summary = "Change the authenticated admin's login email")
    public ResponseEntity<AuthResponse> changeEmail(Authentication authentication,
                                                    @Valid @RequestBody ChangeEmailRequest request) {
        return ResponseEntity.ok(authService.changeEmail(authentication.getName(), request));
    }
}
