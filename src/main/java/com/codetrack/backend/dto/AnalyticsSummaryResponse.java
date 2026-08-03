package com.codetrack.backend.dto;

import java.math.BigDecimal;

public record AnalyticsSummaryResponse(
        long totalStudents,
        long studentsWithPerformance,
        long atRiskCount,
        BigDecimal averageOverallScore,
        BigDecimal averageConsistency,
        LeaderboardEntry topPerformer
) {}
