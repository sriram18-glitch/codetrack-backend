package com.codetrack.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AtRiskStudent(
        UUID studentId,
        String rollNumber,
        String name,
        String branch,
        BigDecimal overallScore,
        Instant lastSynced,
        String reason
) {}
