package com.codetrack.backend.dto;

/**
 * Result of an automatic best-effort sync for a single student. Only platforms
 * that have a configured username count as "attempted"; platforms skipped
 * because no handle is set are not counted as failures.
 */
public record StudentSyncSummary(
        int attempted,
        int succeeded,
        int failed
) {}
