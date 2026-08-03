package com.codetrack.backend.dto;

import java.util.UUID;

public record AdminSummary(
        UUID id,
        String email,
        String fullName,
        String collegeName
) {}
