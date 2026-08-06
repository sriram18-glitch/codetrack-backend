package com.codetrack.backend.service;

import com.codetrack.backend.dto.AnalyticsSummaryResponse;
import com.codetrack.backend.dto.AtRiskStudent;
import com.codetrack.backend.dto.BranchAnalytics;
import com.codetrack.backend.dto.LeaderboardEntry;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import com.codetrack.backend.util.PerformanceSort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final BigDecimal AT_RISK_THRESHOLD = new BigDecimal("4.00");
    private static final long INACTIVE_AFTER_DAYS = 30;

    private final StudentRepository studentRepository;
    private final PerformanceRepository performanceRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        List<Performance> performances = performanceRepository.findAll();
        long totalStudents = studentRepository.count();

        List<Performance> scored = performances.stream()
                .filter(p -> p.getOverallScore() != null)
                .toList();

        BigDecimal avgOverall = scored.isEmpty()
                ? BigDecimal.ZERO
                : scored.stream()
                        .map(Performance::getOverallScore)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);

        BigDecimal avgConsistency = scored.isEmpty()
                ? BigDecimal.ZERO
                : scored.stream()
                        .map(Performance::getConsistencyScore)
                        .filter(c -> c != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(
                                scored.stream().map(Performance::getConsistencyScore).filter(c -> c != null).count()),
                                2, RoundingMode.HALF_UP);

        LeaderboardEntry top = leaderboard().stream().findFirst().orElse(null);

        return new AnalyticsSummaryResponse(
                totalStudents,
                scored.size(),
                atRiskStudents().size(),
                avgOverall,
                avgConsistency,
                top
        );
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard() {
        List<LeaderboardEntry> entries = performanceRepository.findAll().stream()
                .filter(p -> p.getOverallScore() != null)
                .sorted(PerformanceSort.performanceOrder())
                .map(p -> toLeaderboardEntry(p, 0))
                .toList();

        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ranked.add(new LeaderboardEntry(
                    i + 1,
                    entries.get(i).studentId(),
                    entries.get(i).rollNumber(),
                    entries.get(i).name(),
                    entries.get(i).branch(),
                    entries.get(i).year(),
                    entries.get(i).section(),
                    entries.get(i).overallScore(),
                    entries.get(i).consistencyScore(),
                    entries.get(i).leetcodeSolved(),
                    entries.get(i).codeforcesRating(),
                    entries.get(i).codechefRating(),
                    entries.get(i).totalSolved()
            ));
        }
        return ranked;
    }

    @Transactional(readOnly = true)
    public List<AtRiskStudent> atRiskStudents() {
        List<Student> students = studentRepository.findAll();
        Map<UUID, Performance> byStudent = performanceRepository.findAll().stream()
                .collect(Collectors.toMap(p -> p.getStudent().getId(), Function.identity()));

        return students.stream()
                .map(student -> assess(student, byStudent.get(student.getId())))
                .filter(entry -> entry != null && entry.reason() != null)
                .sorted(Comparator
                        .comparing(AtRiskStudent::overallScore,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BranchAnalytics> byBranch() {
        Map<String, List<Performance>> grouped = performanceRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStudent().getBranch() == null ? "Unassigned" : p.getStudent().getBranch(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<Performance> list = entry.getValue();
                    List<Performance> scored = list.stream()
                            .filter(p -> p.getOverallScore() != null)
                            .toList();
                    BigDecimal avgOverall = scored.isEmpty()
                            ? BigDecimal.ZERO
                            : scored.stream().map(Performance::getOverallScore)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);
                    BigDecimal avgConsistency = scored.isEmpty()
                            ? BigDecimal.ZERO
                            : scored.stream().map(Performance::getConsistencyScore)
                                    .filter(c -> c != null)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .divide(BigDecimal.valueOf(scored.size()), 2, RoundingMode.HALF_UP);
                    return new BranchAnalytics(entry.getKey(), list.size(), avgOverall, avgConsistency);
                })
                .sorted(Comparator.comparing(BranchAnalytics::averageOverallScore).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> topSolvers() {
        return performanceRepository.findAll().stream()
                .filter(p -> p.getLeetcodeSolved() != null && p.getLeetcodeSolved() > 0)
                .map(p -> toLeaderboardEntry(p, 0))
                .sorted(Comparator.comparing(LeaderboardEntry::leetcodeSolved,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(10)
                .toList();
    }

    private AtRiskStudent assess(Student student, Performance performance) {
        if (performance == null || performance.getOverallScore() == null) {
            return new AtRiskStudent(student.getId(), student.getRollNumber(), student.getName(),
                    student.getBranch(), null, null, "Never synced — no performance data yet");
        }

        if (performance.getOverallScore().compareTo(AT_RISK_THRESHOLD) < 0) {
            return new AtRiskStudent(student.getId(), student.getRollNumber(), student.getName(),
                    student.getBranch(), performance.getOverallScore(), performance.getLastUpdated(),
                    "Low overall score (" + performance.getOverallScore() + " / 10)");
        }

        if (performance.getLastUpdated() != null
                && Duration.between(performance.getLastUpdated(), Instant.now()).toDays() > INACTIVE_AFTER_DAYS) {
            return new AtRiskStudent(student.getId(), student.getRollNumber(), student.getName(),
                    student.getBranch(), performance.getOverallScore(), performance.getLastUpdated(),
                    "Inactive — no sync in over " + INACTIVE_AFTER_DAYS + " days");
        }

        return null;
    }

    private LeaderboardEntry toLeaderboardEntry(Performance p, int rank) {
        return new LeaderboardEntry(
                rank,
                p.getStudent().getId(),
                p.getStudent().getRollNumber(),
                p.getStudent().getName(),
                p.getStudent().getBranch(),
                p.getStudent().getYear(),
                p.getStudent().getSection(),
                p.getOverallScore(),
                p.getConsistencyScore(),
                p.getLeetcodeSolved(),
                p.getCodeforcesRating(),
                p.getCodechefRating(),
                PerformanceSort.totalSolved(p)
        );
    }
}
