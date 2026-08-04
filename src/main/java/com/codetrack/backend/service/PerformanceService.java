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
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        BigDecimal codeforces = platformComponent(performance.getCodeforcesRating(), performance.getCodeforcesSolved());
        BigDecimal codechef = platformComponent(performance.getCodechefRating(), performance.getCodechefSolved());
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

    /**
     * Platform component (0–10) for Codeforces/CodeChef. Rated users keep the
     * rating-based formula; unrated but active users (solved problems, no
     * contest rating yet) fall back to a solved-based score instead of 0.
     */
    private BigDecimal platformComponent(Integer rating, Integer solved) {
        if (rating != null && rating > 0) {
            return ratingComponent(rating);
        }
        if (solved != null && solved > 0) {
            return solvedComponent(solved);
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal solvedComponent(Integer solved) {
        return BigDecimal.valueOf(Math.min(10.0, (solved / 300.0) * 10.0)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Consistency component (0–10): reflects actual coding practice, not
     * account connectivity or sync frequency. Students with no solved
     * problems score 0. The base volume score follows a progressive curve
     * (a handful of solves rewards very little; sustained volume approaches
     * the cap), and recency plus the span of snapshots across weeks scale it
     * up further — but only proportionally to how much real activity exists.
     */
    BigDecimal consistencyComponent(Performance performance) {
        int totalSolved = totalSolved(performance);
        if (totalSolved <= 0) {
            return BigDecimal.ZERO;
        }
        double volume = volumeCurve(totalSolved);
        double activityRatio = Math.min(1.0, volume / 3.5);
        double recency = recencyFactor(performance.getLastUpdated());
        double span = spanFactor(performance.getStudent().getId());
        double consistency = Math.min(10.0, volume + 3.0 * (0.5 * recency + 0.5 * span) * activityRatio);
        return BigDecimal.valueOf(consistency).setScale(2, RoundingMode.HALF_UP);
    }

    private int totalSolved(Performance performance) {
        int total = 0;
        if (performance.getLeetcodeSolved() != null) {
            total += performance.getLeetcodeSolved();
        }
        if (performance.getCodeforcesSolved() != null) {
            total += performance.getCodeforcesSolved();
        }
        if (performance.getCodechefSolved() != null) {
            total += performance.getCodechefSolved();
        }
        return total;
    }

    /**
     * Progressive volume curve (0–7): few solves reward little, the mid
     * range climbs, and it saturates near the top. Breakpoints:
     * (0,0) (5,0.5) (25,2) (100,5) (300,7).
     */
    private double volumeCurve(int solved) {
        double[] xs = {0, 5, 25, 100, 300};
        double[] ys = {0, 0.5, 2.0, 5.0, 7.0};
        if (solved <= xs[0]) {
            return ys[0];
        }
        if (solved >= xs[xs.length - 1]) {
            return ys[ys.length - 1];
        }
        for (int i = 1; i < xs.length; i++) {
            if (solved <= xs[i]) {
                double t = (solved - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[ys.length - 1];
    }

    private double recencyFactor(Instant lastUpdated) {
        if (lastUpdated == null) {
            return 0.0;
        }
        long days = Duration.between(lastUpdated, Instant.now()).toDays();
        if (days < 7) {
            return 1.0;
        }
        if (days < 30) {
            return 0.6;
        }
        if (days < 90) {
            return 0.3;
        }
        return 0.0;
    }

    /**
     * Rewards solving across calendar weeks: 8 distinct weeks of snapshots
     * (roughly two months of sustained activity) reach full span credit.
     */
    private double spanFactor(UUID studentId) {
        List<PerformanceHistory> history = performanceHistoryRepository.findByStudentIdOrderByCapturedAtAsc(studentId);
        if (history == null || history.isEmpty()) {
            return 0.0;
        }
        Set<Integer> weeks = new HashSet<>();
        for (PerformanceHistory h : history) {
            var captured = h.getCapturedAt().atZone(ZoneOffset.UTC);
            weeks.add(captured.get(WeekFields.ISO.weekBasedYear()) * 100
                    + captured.get(WeekFields.ISO.weekOfWeekBasedYear()));
        }
        return Math.min(1.0, weeks.size() / 8.0);
    }

    /**
     * Problem Solving component (0–10): rewards total volume of problems
     * solved across all supported platforms, regardless of contest rating.
     * 300 total problems solved = 10 pts.
     */
    BigDecimal problemSolvingComponent(Performance performance) {
        int totalSolved = totalSolved(performance);
        return BigDecimal.valueOf(Math.min(10.0, (totalSolved / 300.0) * 10.0)).setScale(2, RoundingMode.HALF_UP);
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
            case CODEFORCES -> {
                performance.setCodeforcesRating(data.rating());
                performance.setCodeforcesSolved(data.problemsSolved());
                performance.setCodeforcesMaxRating(data.maxRating());
                performance.setCodeforcesRank(data.rank());
                performance.setCodeforcesContestCount(data.contestCount());
            }
            case CODECHEF -> {
                performance.setCodechefRating(data.rating());
                performance.setCodechefSolved(data.problemsSolved());
                performance.setCodechefStars(data.stars());
                performance.setCodechefGlobalRank(data.globalRanking());
            }
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
                performance.getCodeforcesSolved(),
                performance.getCodeforcesMaxRating(),
                performance.getCodeforcesRank(),
                performance.getCodeforcesContestCount(),
                performance.getCodechefRating(),
                performance.getCodechefSolved(),
                performance.getCodechefStars(),
                performance.getCodechefGlobalRank(),
                performance.getLastUpdated()
        );
    }
}
