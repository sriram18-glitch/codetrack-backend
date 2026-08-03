package com.codetrack.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InsightResponse(
        UUID studentId,
        String studentName,
        BigDecimal overallScore,
        List<Insight> insights
) {}
