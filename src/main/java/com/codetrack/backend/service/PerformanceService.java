package com.codetrack.backend.service;

import com.codetrack.backend.dto.PerformanceHistoryResponse;
import com.codetrack.backend.dto.PerformanceResponse;
import com.codetrack.backend.dto.PlatformData;
import com.codetrack.backend.dto.PlatformResult;
import com.codetrack.backend.dto.SyncAllResult;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.PerformanceHistory;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.PerformanceHistoryRepository;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    public static final String LEETCODE = "LEETCODE";
    public static final String CODEFORCES = "CODEFORCES";
    public static final String CODECHEF = "CODECHEF";

    private static final int SCORE_WEIGHT_LEETCODE = 35;
    private static final int SCORE_WEIGHT_CODEFORCES = 30;
    private static final int SCORE_WEIGHT_CODECHEF = 15;
    private static final int SCORE_WEIGHT_CONSISTENCY = 10;
    private static final int SCORE_WEIGHT_PROBLEM_SOLVING = 10;

    private final StudentRepository studentRepository;
    private final CodingProfileRepository codingProfileRepository;
    private final PerformanceRepository performanceRepository;
    private final PerformanceHistoryRepository performanceHistoryRepository;
    private final CodeforcesService codeforcesService;
    private final LeetCodeService leetCodeService;
    private final CodeChefService codeChefService;

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PerformanceResponse getPerformance(UUID studentId) {
        Student student = findStudent(studentId);
        Performance performance = performanceRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No performance data yet for this student — run a sync first"));
        return toResponse(student, performance);
    }

    @Transactional(readOnly = true)
    public List<PerformanceHistoryResponse> getHistory(UUID studentId) {
        Student student = findStudent(studentId);
        return performanceHistoryRepository.findByStudentIdOrderByCapturedAtAsc(studentId).stream()
                .map(h -> new PerformanceHistoryResponse(h.getId(), h.getPlatform(), h.getRating(), h.getProblemsSolved(), h.getCapturedAt()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Syncs
    // ------------------------------------------------------------------

    @Transactional
    public PerformanceResponse syncPlatform(UUID studentId, String platform) {
        Student student = findStudent(studentId);
        CodingProfile profile = codingProfileRepository.findByStudentId(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No coding profile set for this student"));

        String username = usernameFor(profile, platform);
        if (username == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No " + displayName(platform) + " username is set for this student");
        }

        Optional<PlatformData> data = fetch(platform, username);
        if (data.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    displayName(platform) + " user '" + username + "' was not found or the platform is currently unreachable");
        }

        PlatformData pd = data.get();
        Performance performance = performanceRepository.findByStudentId(studentId)
                .orElseGet(() -> Performance.builder().student(student).build());

        apply(performance, pd);
        performance.setLastUpdated(Instant.now());
        performanceRepository.save(performance);

        recordSnapshot(student, pd);
        recalculateAndSave(performance);

        log.info("Synced {} for student {} ({})", displayName(platform), student.getRollNumber(), username);
        return toResponse(student, performance);
    }

    @Transactional
    public SyncAllResult syncAll(UUID studentId) {
        Student student = findStudent(studentId);
        List<PlatformResult> results = new ArrayList<>();
        for (String platform : List.of(LEETCODE, CODEFORCES, CODECHEF)) {
            try {
                syncPlatform(student.getId(), platform);
                results.add(new PlatformResult(platform, true, "Synced successfully"));
            } catch (ApiException ex) {
                results.add(new PlatformResult(platform, false, ex.getMessage()));
            }
        }
        return new SyncAllResult(results);
    }

    // ------------------------------------------------------------------
    // Score engine (Ticket I)
    // LeetCode 35% + Codeforces 30% + CodeChef 15%
    // + Consistency 10% + Problem Solving 10%
    // ------------------------------------------------------------------

    @Transactional
    public Performance recalculateAndSave(Performance performance) {
        BigDecimal leetcode = scoreFor(performance.getLeetcodeRating(), performance.getLeetcodeSolved(),
                performance.getLeetcodeEasy(), performance.getLeetcodeMedium(), performance.getLeetcodeHard());
        BigDecimal codeforces = ratingComponent(performance.getCodeforcesRating());
        BigDecimal codechef = ratingComponent(performance.getCodechefRating());
        BigDecimal consistency = consistencyComponent(performance);
        BigDecimal problemSolving = problemSolvingComponent(performance);

        BigDecimal overall = leetcode.multiply(BigDecimal.valueOf(SCORE_WEIGHT_LEETCODE))
                .add(codeforces.multiply(BigDecimal.valueOf(SCORE_WEIGHT_CODEFORCES)))
                .add(codechef.multiply(BigDecimal.valueOf(SCORE_WEIGHT_CODECHEF)))
                .add(consistency.multiply(BigDecimal.valueOf(SCORE_WEIGHT_CONSISTENCY)))
                .add(problemSolving.multiply(BigDecimal.valueOf(SCORE_WEIGHT_PROBLEM_SOLVING)))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        performance.setConsistencyScore(consistency);
        performance.setOverallScore(overall);
        return performanceRepository.save(performance);
    }

    /**
     * LeetCode component (0–10): weighted by problems solved (max 6 pts for
     * reaching 300 solved, weighted hard/medium/easy) and contest rating (up
     * to 4 pts at a 3000 contest rating).
     */
    BigDecimal scoreFor(Integer rating, Integer solved, Integer easy, Integer medium, Integer hard) {
        double s = solved == null ? 0 : solved;
        double e = easy == null ? 0 : easy;
        double m = medium == null ? 0 : medium;
        double h = hard == null ? 0 : hard;

        double weightedSolved = e + 2.0 * m + 4.0 * h;
        double solvedScore = Math.min(6.0, (weightedSolved / 600.0) * 6.0);
        double ratingScore = (rating == null || rating <= 0) ? 0.0
                : Math.min(4.0, (rating / 3000.0) * 4.0);
        return BigDecimal.valueOf(Math.min(10.0, solvedScore + ratingScore)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratingComponent(Integer rating) {
        if (rating == null || rating <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.min(10.0, (rating / 3500.0) * 10.0)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal consistencyComponent(Performance performance) {
        long snapshotCount = performanceHistoryRepository.countByStudentId(performance.getStudent().getId());
        int activePlatforms = 0;
        if (hasActivity(performance.getLeetcodeRating(), performance.getLeetcodeSolved())) activePlatforms++;
        if (hasActivity(performance.getCodeforcesRating(), null)) activePlatforms++;
        if (hasActivity(performance.getCodechefRating(), null)) activePlatforms++;

        double snapshotFactor = Math.min(1.0, snapshotCount / 6.0);
        double platformFactor = Math.min(1.0, activePlatforms / 3.0);

        double recency = 0.25;
        if (performance.getLastUpdated() != null) {
            long days = Duration.between(performance.getLastUpdated(), Instant.now()).toDays();
            recency = days < 7 ? 1.0 : days < 30 ? 0.5 : 0.25;
        }

        double consistency = 10.0 * (0.5 * snapshotFactor + 0.25 * platformFactor + 0.25 * recency);
        return BigDecimal.valueOf(consistency).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Problem Solving component (0–10): rewards volume of problems solved
     * regardless of contest rating. 300 total problems solved = 10 pts.
     */
    BigDecimal problemSolvingComponent(Performance performance) {
        int totalSolved = 0;
        if (performance.getLeetcodeSolved() != null) {
            totalSolved += performance.getLeetcodeSolved();
        }
        return BigDecimal.valueOf(Math.min(10.0, (totalSolved / 300.0) * 10.0)).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasActivity(Integer rating, Integer solved) {
        return (rating != null && rating > 0) || (solved != null && solved > 0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Optional<PlatformData> fetch(String platform, String username) {
        return switch (platform) {
            case LEETCODE -> leetCodeService.fetch(username);
            case CODEFORCES -> codeforcesService.fetch(username);
            case CODECHEF -> codeChefService.fetch(username);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported platform: " + platform);
        };
    }

    private String usernameFor(CodingProfile profile, String platform) {
        return switch (platform) {
            case LEETCODE -> profile.getLeetcodeUsername();
            case CODEFORCES -> profile.getCodeforcesUsername();
            case CODECHEF -> profile.getCodechefUsername();
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported platform: " + platform);
        };
    }

    private void apply(Performance performance, PlatformData data) {
        switch (data.platform()) {
            case LEETCODE -> {
                performance.setLeetcodeRating(data.rating());
                performance.setLeetcodeSolved(data.problemsSolved());
                performance.setLeetcodeEasy(data.easy());
                performance.setLeetcodeMedium(data.medium());
                performance.setLeetcodeHard(data.hard());
            }
            case CODEFORCES -> performance.setCodeforcesRating(data.rating());
            case CODECHEF -> performance.setCodechefRating(data.rating());
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported platform: " + data.platform());
        }
    }

    private void recordSnapshot(Student student, PlatformData data) {
        PerformanceHistory history = PerformanceHistory.builder()
                .student(student)
                .platform(data.platform())
                .rating(data.rating())
                .problemsSolved(data.problemsSolved())
                .capturedAt(Instant.now())
                .build();
        performanceHistoryRepository.save(history);
    }

    private Student findStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private String displayName(String platform) {
        return switch (platform) {
            case LEETCODE -> "LeetCode";
            case CODEFORCES -> "Codeforces";
            case CODECHEF -> "CodeChef";
            default -> platform;
        };
    }

    private PerformanceResponse toResponse(Student student, Performance performance) {
        return new PerformanceResponse(
                student.getId(),
                student.getRollNumber(),
                student.getName(),
                performance.getOverallScore(),
                performance.getConsistencyScore(),
                performance.getLeetcodeRating(),
                performance.getLeetcodeSolved(),
                performance.getLeetcodeEasy(),
                performance.getLeetcodeMedium(),
                performance.getLeetcodeHard(),
                performance.getCodeforcesRating(),
                performance.getCodechefRating(),
                performance.getLastUpdated()
        );
    }
}
