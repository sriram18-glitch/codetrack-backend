package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.CodingProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Background daily synchronization. Every day at 09:00 (server local time)
 * every student that has a coding profile is re-synced. A single student's
 * failure is logged and processing continues with the next student, so the
 * scheduled run always completes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncScheduler {

    private final CodingProfileRepository codingProfileRepository;
    private final AutoSyncService autoSyncService;

    @Scheduled(cron = "${codetrack.sync.cron:0 0 9 * * *}")
    public void automaticDailySync() {
        log.info("Automatic Daily Sync Started");
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
                log.warn("Automatic sync failed for student {} ({}): {}",
                        student.getName(), student.getRollNumber(), ex.getMessage());
            }
        }

        log.info("Automatic Daily Sync Completed — Success: {}, Failed: {}", success, failed);
    }
}
