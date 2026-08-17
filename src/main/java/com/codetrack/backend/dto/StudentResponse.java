package com.codetrack.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record StudentResponse(
        UUID id,
        String rollNumber,
        String name,
        String email,
        String branch,
        Integer year,
        String section,
        String phone,
        String githubProfileUrl,
        String linkedinProfileUrl,
        Instant createdAt
) {}