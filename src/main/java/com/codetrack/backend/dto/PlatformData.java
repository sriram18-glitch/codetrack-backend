package com.codetrack.backend.dto;

/**
 * Normalized snapshot of a competitive programming platform profile,
 * returned by the per-platform sync clients.
 */
public record PlatformData(
        String platform,
        Integer rating,
        Integer maxRating,
        String rank,
        Integer problemsSolved,
        Integer easy,
        Integer medium,
        Integer hard,
        Integer globalRanking,
        Integer contestCount,
        String stars
) {}
