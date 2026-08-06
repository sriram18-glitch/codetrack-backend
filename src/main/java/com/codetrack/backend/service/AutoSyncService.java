package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Fault-tolerant automatic synchronization. Reuses the existing
 * {@link PerformanceService} sync implementation exactly as if the admin had
 * clicked Sync: every platform is fetched, stored and scored through
 * {@link PerformanceService#syncPlatform}. A platform that is unreachable or
 * has no configured handle is skipped and logged; it is never allowed to fail
 * the student creation or stop the rest of the sync.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoSyncService {

    private static final List<String> PLATFORMS = List.of(
            PerformanceService.LEETCODE, PerformanceService.CODEFORCES, PerformanceService.CODECHEF);

    private final StudentRepository studentRepository;
    private final CodingProfileRepository codingProfileRepository;
    private final PerformanceService performanceService;

    /**
     * Synchronizes every configured platform for one student. Never throws:
     * per-platform failures are caught, logged and counted. Intended to be
     * called with no surrounding transaction so each platform sync runs in its
     * own transaction and a failed platform cannot roll back the others.
     */
    public StudentSyncSummary syncStudentBestEffort(UUID studentId) {
        Student student = studentRepository.findById(studentId).orElse(null);
        if (student == null) {
            log.warn("Automatic sync skipped — student {} does not exist", studentId);
            return new StudentSyncSummary(0, 0, 0);
        }
        CodingProfile profile = codingProfileRepository.findByStudentId(studentId).orElse(null);
        if (profile == null) {
            log.info("Student: {} — no coding profile, skipped", student.getName());
            return new StudentSyncSummary(0, 0, 0);
        }

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        for (String platform : PLATFORMS) {
            String username = usernameFor(profile, platform);
            if (username == null || username.isBlank()) {
                continue;
            }
            attempted++;
            try {
                performanceService.syncPlatform(studentId, platform);
                succeeded++;
                log.info("Student: {} ({}) — {} ✓", student.getName(), student.getRollNumber(), displayName(platform));
            } catch (Exception ex) {
                failed++;
                log.warn("Student: {} ({}) — {} ✕ ({}). Retry later with manual Sync.",
                        student.getName(), student.getRollNumber(), displayName(platform), ex.getMessage());
            }
        }
        return new StudentSyncSummary(attempted, succeeded, failed);
    }

    private String usernameFor(CodingProfile profile, String platform) {
        return switch (platform) {
            case PerformanceService.LEETCODE -> profile.getLeetcodeUsername();
            case PerformanceService.CODEFORCES -> profile.getCodeforcesUsername();
            case PerformanceService.CODECHEF -> profile.getCodechefUsername();
            default -> null;
        };
    }

    private String displayName(String platform) {
        return switch (platform) {
            case PerformanceService.LEETCODE -> "LeetCode";
            case PerformanceService.CODEFORCES -> "Codeforces";
            case PerformanceService.CODECHEF -> "CodeChef";
            default -> platform;
        };
    }
}
