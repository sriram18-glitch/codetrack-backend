package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.dto.SyncStatus;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single reusable engine for synchronizing all students in the background. It
 * is triggered from the admin UI, the {@link SyncScheduler} (Spring
 * {@code @Scheduled}), and the external cron-trigger endpoint — all three use
 * this one implementation and the existing {@link PerformanceService} /
 * {@link AutoSyncService} logic; no platform fetching or scoring is duplicated.
 *
 * <p>Work never runs inside the HTTP request: it is handed to a background
 * thread and the endpoint returns immediately. Processing is strictly
 * sequential with a small configurable delay between students to respect
 * LeetCode/Codeforces/CodeChef rate limits and to avoid saturating Render or
 * the database. One failing student never stops the rest. A duplicate trigger
 * is rejected while a run is in progress.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulkSyncService {

    public static final String ALL = "ALL";
    public static final String SOURCE_AUTOMATIC = "Automatic";
    public static final String SOURCE_EXTERNAL = "External";
    public static final String SOURCE_MANUAL = "Manual";

    private final CodingProfileRepository codingProfileRepository;
    private final AutoSyncService autoSyncService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "bulk-sync-worker");
        t.setDaemon(true);
        return t;
    });
    private volatile SyncStatus status = SyncStatus.idle();

    @Value("${codetrack.sync.request-delay-ms:1000}")
    private long requestDelayMs;

    /**
     * Starts a bulk synchronization for {@code platform} (ALL or a specific
     * platform). Returns immediately with the RUNNING status. Throws a 409
     * {@link ApiException} if a synchronization is already in progress.
     */
    public SyncStatus submit(String platform, String source) {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException(HttpStatus.CONFLICT, "A synchronization is already running.");
        }
        status = new SyncStatus(SyncStatus.RUNNING, platform, 0, 0, 0, 0, 0, Instant.now(), null, null);
        log.info("{} daily sync triggered — platform: {}", source, platform);
        executor.execute(() -> run(platform));
        return status;
    }

    /** Current synchronization progress snapshot. */
    public SyncStatus status() {
        return status;
    }

    /** Whether a bulk synchronization is currently executing. */
    public boolean isRunning() {
        return running.get();
    }

    private void run(String platform) {
        try {
            List<CodingProfile> profiles = codingProfileRepository.findAllWithStudent();
            int total = profiles.size();
            status = new SyncStatus(SyncStatus.RUNNING, platform, total, 0, 0, 0, 0,
                    status.startedAt(), null, null);
            log.info("Synchronizing {} students...", total);

            int processed = 0;
            int synced = 0;
            int skipped = 0;
            int failed = 0;
            for (CodingProfile profile : profiles) {
                Student student = profile.getStudent();
                if (student == null) {
                    continue;
                }
                StudentSyncSummary summary;
                try {
                    summary = syncStudent(platform, student);
                } catch (Exception ex) {
                    log.warn("Bulk sync failed for student {} ({}): {}",
                            student.getName(), student.getRollNumber(), ex.getMessage());
                    summary = new StudentSyncSummary(1, 0, 1);
                }
                processed++;
                if (summary.attempted() == 0) {
                    skipped++;
                } else if (summary.failed() > 0) {
                    failed++;
                } else {
                    synced++;
                }
                status = new SyncStatus(SyncStatus.RUNNING, platform, total,
                        processed, synced, skipped, failed, status.startedAt(), null, null);
                pauseBetweenStudents();
            }

            status = new SyncStatus(SyncStatus.COMPLETED, platform, total,
                    processed, synced, skipped, failed, status.startedAt(), Instant.now(), null);
            log.info("Completed — Success: {}, Failed: {}, Skipped: {}", synced, failed, skipped);
        } catch (Throwable ex) {
            log.error("Bulk sync failed unexpectedly", ex);
            status = new SyncStatus(SyncStatus.FAILED, status.platform(), status.totalStudents(),
                    status.processed(), status.synced(), status.skipped(), status.failed(),
                    status.startedAt(), Instant.now(), ex.getMessage());
        } finally {
            running.set(false);
        }
    }

    private StudentSyncSummary syncStudent(String platform, Student student) {
        if (ALL.equals(platform)) {
            return autoSyncService.syncStudentBestEffort(student.getId());
        }
        return autoSyncService.syncPlatformBestEffort(student.getId(), platform);
    }

    private void pauseBetweenStudents() {
        if (requestDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
