package com.codetrack.backend.dto;

import java.time.Instant;

/**
 * Mutable snapshot of the current bulk synchronization run. Immutable records
 * are swapped atomically by {@link com.codetrack.backend.service.BulkSyncService}
 * so readers always observe a consistent state.
 */
public record SyncStatus(
        String status,
        String platform,
        int totalStudents,
        int processed,
        int synced,
        int skipped,
        int failed,
        Instant startedAt,
        Instant completedAt,
        String message
) {
    public static final String IDLE = "IDLE";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    public static SyncStatus idle() {
        return new SyncStatus(IDLE, null, 0, 0, 0, 0, 0, null, null, null);
    }
}
