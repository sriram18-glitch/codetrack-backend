package com.codetrack.backend.controller;

import com.codetrack.backend.dto.RegisterRequest;
import com.codetrack.backend.dto.RegisterResponse;
import com.codetrack.backend.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/register")
@RequiredArgsConstructor
@Tag(name = "Registration", description = "Public student self-registration (no authentication)")
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    @Operation(summary = "Self-register a student with optional validated coding usernames")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrationService.register(request));
    }

    @GetMapping("/validate")
    @Operation(summary = "Check whether a platform username exists (live validation)")
    public ResponseEntity<Map<String, Boolean>> validate(
            @RequestParam String platform,
            @RequestParam String username) {
        boolean valid = registrationService.validateUsername(platform, username);
        return ResponseEntity.ok(Map.of("valid", valid));
    }
}
