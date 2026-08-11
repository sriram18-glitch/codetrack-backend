package com.codetrack.backend.service;

import com.codetrack.backend.dto.DailySyncResult;
import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.CodingProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Full daily synchronization shared by the {@link SyncScheduler} and the
 * external cron-trigger endpoint. Both callers run the exact same logic so
 * there is a single source of truth. The run is guarded so that two triggers
 * (for example the scheduler and an external HTTP call) can never execute a
 * full synchronization at the same time: the second trigger is skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailySyncService {

    public static final String SOURCE_AUTOMATIC = "Automatic";
    public static final String SOURCE_EXTERNAL = "External";

    private final CodingProfileRepository codingProfileRepository;
    private final AutoSyncService autoSyncService;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    /**
     * Synchronizes every student that has a coding profile, one platform at a
     * time through {@link AutoSyncService#syncStudentBestEffort}. A single
     * student's failure is logged and processing continues with the next
     * student, so the run always completes. Returns {@link DailySyncResult#skipped()}
     * when another run is already in progress.
     */
    public DailySyncResult runDailySync(String source) {
        if (!syncRunning.compareAndSet(false, true)) {
            log.info("{} daily sync skipped — a synchronization is already in progress", source);
            return DailySyncResult.skippedRun();
        }
        try {
            log.info("{} daily sync triggered", source);
            List<CodingProfile> profiles = codingProfileRepository.findAllWithStudent();
            log.info("Synchronizing {} students...", profiles.size());

            int success = 0;
            int failed = 0;
            for (CodingProfile profile : profiles) {
                Student student = profile.getStudent();
                if (student == null) {
                    continue;
                }
                try {
                    StudentSyncSummary summary = autoSyncService.syncStudentBestEffort(student.getId());
                    if (summary.attempted() == 0) {
                        log.info("Student: {} — no platform handles configured, skipped", student.getName());
                        continue;
                    }
                    if (summary.failed() > 0) {
                        failed++;
                    } else {
                        success++;
                    }
                } catch (Exception ex) {
                    failed++;
                    log.warn("Daily sync failed for student {} ({}): {}",
                            student.getName(), student.getRollNumber(), ex.getMessage());
                }
            }

            log.info("Completed — Success: {}, Failed: {}", success, failed);
            return new DailySyncResult(success, failed, profiles.size(), false);
        } finally {
            syncRunning.set(false);
        }
    }
}
