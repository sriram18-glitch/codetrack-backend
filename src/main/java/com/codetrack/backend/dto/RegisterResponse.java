package com.codetrack.backend.dto;

import java.util.UUID;

public record RegisterResponse(
        UUID studentId,
        String rollNumber,
        String name,
        String message
) {}
