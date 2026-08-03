package com.codetrack.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PerformanceResponse(
        UUID studentId,
        String rollNumber,
        String name,
        BigDecimal overallScore,
        BigDecimal consistencyScore,
        Integer leetcodeRating,
        Integer leetcodeSolved,
        Integer leetcodeEasy,
        Integer leetcodeMedium,
        Integer leetcodeHard,
        Integer codeforcesRating,
        Integer codechefRating,
        Instant lastUpdated
) {}
