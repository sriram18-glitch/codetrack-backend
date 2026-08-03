package com.codetrack.backend.controller;

import com.codetrack.backend.dto.CodingProfileRequest;
import com.codetrack.backend.dto.CodingProfileResponse;
import com.codetrack.backend.service.CodingProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/students/{studentId}/coding-profile")
@RequiredArgsConstructor
@Tag(name = "Coding Profiles", description = "Manage a student's platform usernames (admin only)")
public class CodingProfileController {

    private final CodingProfileService codingProfileService;

    @PutMapping
    @Operation(summary = "Create or update a student's coding profile usernames")
    public ResponseEntity<CodingProfileResponse> upsertProfile(
            @PathVariable UUID studentId,
            @Valid @RequestBody CodingProfileRequest request
    ) {
        return ResponseEntity.ok(codingProfileService.upsertProfile(studentId, request));
    }

    @GetMapping
    @Operation(summary = "Get a student's coding profile usernames")
    public ResponseEntity<CodingProfileResponse> getProfile(@PathVariable UUID studentId) {
        return ResponseEntity.ok(codingProfileService.getProfile(studentId));
    }
}