package com.codetrack.backend.service;

import com.codetrack.backend.dto.AnalyticsSummaryResponse;
import com.codetrack.backend.dto.AtRiskStudent;
import com.codetrack.backend.dto.LeaderboardEntry;
import com.codetrack.backend.entity.Performance;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.PerformanceRepository;
import com.codetrack.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private PerformanceRepository performanceRepository;

    private AnalyticsService analyticsService;

    private Student top;
    private Student mid;
    private Student idle;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(studentRepository, performanceRepository);
        top = Student.builder().id(UUID.randomUUID()).rollNumber("1").name("Top").branch("CSE").year(3).build();
        mid = Student.builder().id(UUID.randomUUID()).rollNumber("2").name("Mid").branch("CSE").year(3).build();
        idle = Student.builder().id(UUID.randomUUID()).rollNumber("3").name("Idle").branch("ECE").year(2).build();
    }

    @Test
    void leaderboardOrdersByOverallScoreDescending() {
        Performance pTop = performance(top, new BigDecimal("8.50"));
        Performance pMid = performance(mid, new BigDecimal("5.00"));

        when(performanceRepository.findAll()).thenReturn(List.of(pMid, pTop));

        List<LeaderboardEntry> leaderboard = analyticsService.leaderboard();

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).rank()).isEqualTo(1);
        assertThat(leaderboard.get(0).name()).isEqualTo("Top");
        assertThat(leaderboard.get(1).rank()).isEqualTo(2);
        assertThat(leaderboard.get(1).name()).isEqualTo("Mid");
    }

    @Test
    void leaderboardBreaksTiesByTotalSolvedThenRollNumber() {
        Performance a = performance(top, new BigDecimal("8.00"));
        a.setLeetcodeSolved(20);
        a.setCodeforcesSolved(10);
        Performance b = performance(mid, new BigDecimal("8.00"));
        b.setLeetcodeSolved(40);
        Performance c = performance(idle, new BigDecimal("8.00"));
        c.setLeetcodeSolved(40);

        when(performanceRepository.findAll()).thenReturn(List.of(b, a, c));

        List<LeaderboardEntry> leaderboard = analyticsService.leaderboard();

        // Equal scores: 40 solved (b, c) ranks above 30 solved (a); b/c tie-break by roll asc.
        assertThat(leaderboard).hasSize(3);
        assertThat(leaderboard.get(0).rollNumber()).isEqualTo("2");
        assertThat(leaderboard.get(0).totalSolved()).isEqualTo(40);
        assertThat(leaderboard.get(1).rollNumber()).isEqualTo("3");
        assertThat(leaderboard.get(1).totalSolved()).isEqualTo(40);
        assertThat(leaderboard.get(2).rollNumber()).isEqualTo("1");
        assertThat(leaderboard.get(2).totalSolved()).isEqualTo(30);
    }

    @Test
    void atRiskFlagsNeverSyncedAndLowScores() {
        Performance pTop = performance(top, new BigDecimal("8.50"));
        Performance pLow = performance(mid, new BigDecimal("2.10"));

        when(studentRepository.findAll()).thenReturn(List.of(top, mid, idle));
        when(performanceRepository.findAll()).thenReturn(List.of(pTop, pLow));

        List<AtRiskStudent> atRisk = analyticsService.atRiskStudents();

        assertThat(atRisk).hasSize(2);
        assertThat(atRisk).anyMatch(a -> a.reason().contains("Never synced"));
        assertThat(atRisk).anyMatch(a -> a.reason().contains("Low overall score"));
        assertThat(atRisk).noneMatch(a -> a.rollNumber().equals("1"));
    }

    @Test
    void atRiskFlagsInactiveStudents() {
        Performance pOld = performance(top, new BigDecimal("6.00"));
        pOld.setLastUpdated(Instant.now().minus(60, ChronoUnit.DAYS));

        when(studentRepository.findAll()).thenReturn(List.of(top));
        when(performanceRepository.findAll()).thenReturn(List.of(pOld));

        List<AtRiskStudent> atRisk = analyticsService.atRiskStudents();

        assertThat(atRisk).hasSize(1);
        assertThat(atRisk.get(0).reason()).contains("Inactive");
    }

    @Test
    void summaryComputesAverages() {
        Performance pTop = performance(top, new BigDecimal("8.50"));
        pTop.setConsistencyScore(new BigDecimal("7.00"));
        Performance pMid = performance(mid, new BigDecimal("5.50"));
        pMid.setConsistencyScore(new BigDecimal("6.00"));

        when(studentRepository.count()).thenReturn(2L);
        when(performanceRepository.findAll()).thenReturn(List.of(pTop, pMid));

        AnalyticsSummaryResponse summary = analyticsService.summary();

        assertThat(summary.totalStudents()).isEqualTo(2L);
        assertThat(summary.studentsWithPerformance()).isEqualTo(2L);
        assertThat(summary.averageOverallScore().doubleValue()).isEqualByComparingTo(7.0);
        assertThat(summary.topPerformer()).isNotNull();
        assertThat(summary.topPerformer().name()).isEqualTo("Top");
    }

    private Performance performance(Student student, BigDecimal score) {
        return Performance.builder()
                .student(student)
                .overallScore(score)
                .lastUpdated(Instant.now())
                .build();
    }
}
