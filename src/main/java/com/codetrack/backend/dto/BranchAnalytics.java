package com.codetrack.backend.dto;

import java.math.BigDecimal;

public record BranchAnalytics(
        String branch,
        long studentCount,
        BigDecimal averageOverallScore,
        BigDecimal averageConsistency
) {}
