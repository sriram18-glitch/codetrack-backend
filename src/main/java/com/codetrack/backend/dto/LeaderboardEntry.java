package com.codetrack.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardEntry(
        int rank,
        UUID studentId,
        String rollNumber,
        String name,
        String branch,
        Integer year,
        String section,
        BigDecimal overallScore,
        BigDecimal consistencyScore,
        Integer leetcodeSolved,
        Integer codeforcesRating,
        Integer codechefRating,
        Integer totalSolved
) {}
