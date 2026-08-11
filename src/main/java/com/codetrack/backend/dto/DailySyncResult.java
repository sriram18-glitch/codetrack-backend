package com.codetrack.backend.dto;

/**
 * Result of a full daily synchronization run. {@code success} and
 * {@code failed} count students that had at least one configured platform
 * handle; students with no handles are not counted in either. {@code total}
 * is the number of students processed. When {@code skipped} is true the run
 * did not start because another synchronization was already in progress.
 */
public record DailySyncResult(
        int success,
        int failed,
        int total,
        boolean skipped
) {
    public static DailySyncResult skippedRun() {
        return new DailySyncResult(0, 0, 0, true);
    }
}
