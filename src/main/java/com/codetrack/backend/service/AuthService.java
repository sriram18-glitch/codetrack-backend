package com.codetrack.backend.service;

import com.codetrack.backend.dto.AdminSummary;
import com.codetrack.backend.dto.AuthResponse;
import com.codetrack.backend.dto.ChangeEmailRequest;
import com.codetrack.backend.dto.ChangePasswordRequest;
import com.codetrack.backend.dto.LoginRequest;
import com.codetrack.backend.entity.Admin;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.AdminRepository;
import com.codetrack.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository adminRepository;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException ex) {
            log.warn("Login failed for '{}': bad credentials", request.email());
            throw ex;
        }

        Admin admin = adminRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        log.info("Admin logged in: id={}, email={}", admin.getId(), admin.getEmail());
        String token = jwtService.generateToken(admin);

        AdminSummary summary = new AdminSummary(
                admin.getId(), admin.getEmail(), admin.getFullName(), admin.getCollegeName()
        );
        return AuthResponse.of(token, jwtService.getExpirationSeconds(), summary);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        Admin admin = adminRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admin not found"));

        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        adminRepository.save(admin);
        log.info("Password changed for admin: id={}, email={}", admin.getId(), admin.getEmail());
    }

    @Transactional
    public AuthResponse changeEmail(String email, ChangeEmailRequest request) {
        Admin admin = adminRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Admin not found"));

        if (!passwordEncoder.matches(request.currentPassword(), admin.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        String newEmail = request.newEmail().trim();
        if (adminRepository.findByEmailIgnoreCase(newEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        admin.setEmail(newEmail);
        adminRepository.save(admin);
        log.info("Email changed for admin: id={}, newEmail={}", admin.getId(), newEmail);

        String token = jwtService.generateToken(admin);
        AdminSummary summary = new AdminSummary(
                admin.getId(), admin.getEmail(), admin.getFullName(), admin.getCollegeName()
        );
        return AuthResponse.of(token, jwtService.getExpirationSeconds(), summary);
    }
}
