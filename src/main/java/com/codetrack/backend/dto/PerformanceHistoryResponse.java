package com.codetrack.backend.dto;

import java.time.Instant;
import java.util.UUID;

public record PerformanceHistoryResponse(
        UUID id,
        String platform,
        Integer rating,
        Integer problemsSolved,
        Instant capturedAt
) {}
