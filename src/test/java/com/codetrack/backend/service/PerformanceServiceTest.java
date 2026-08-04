package com.codetrack.backend.service;

import com.codetrack.backend.dto.PerformanceResponse;
import com.codetrack.backend.dto.PlatformData;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private PerformanceRepository performanceRepository;
    @Mock private PerformanceHistoryRepository performanceHistoryRepository;
    @Mock private CodeforcesService codeforcesService;
    @Mock private LeetCodeService leetCodeService;
    @Mock private CodeChefService codeChefService;

    private PerformanceService performanceService;

    private Student student;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        performanceService = new PerformanceService(
                studentRepository, codingProfileRepository, performanceRepository,
                performanceHistoryRepository, codeforcesService, leetCodeService, codeChefService);
        studentId = UUID.randomUUID();
        student = Student.builder().id(studentId).rollNumber("21CS001").name("Ada Lovelace").build();
    }

    @Test
    void scoreForReturnsZeroWhenNoActivity() {
        assertThat(performanceService.scoreFor(null, null, null, null, null))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void scoreForRewardsSolvedProblemsAndRating() {
        BigDecimal score = performanceService.scoreFor(2000, 150, 40, 60, 30);
        assertThat(score.doubleValue()).isGreaterThan(3.0).isLessThanOrEqualTo(10.0);
    }

    @Test
    void recalculateAndSaveComputesWeightedOverall() {
        Performance performance = Performance.builder()
                .student(student)
                .leetcodeRating(2000)
                .leetcodeSolved(150)
                .leetcodeEasy(40)
                .leetcodeMedium(60)
                .leetcodeHard(30)
                .codeforcesRating(1500)
                .codechefRating(1400)
                .lastUpdated(Instant.now())
                .build();

        when(performanceHistoryRepository.countByStudentId(studentId)).thenReturn(1L);
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Performance saved = performanceService.recalculateAndSave(performance);

        assertThat(saved.getOverallScore()).isNotNull();
        assertThat(saved.getOverallScore().doubleValue()).isBetween(0.0, 10.0);
        assertThat(saved.getConsistencyScore()).isNotNull();
    }

    @Test
    void unratedCodeforcesWithSolvedProblemsGetsNonZeroScore() {
        Performance performance = Performance.builder()
                .student(student)
                .codeforcesSolved(150)
                .lastUpdated(Instant.now())
                .build();

        when(performanceHistoryRepository.countByStudentId(studentId)).thenReturn(1L);
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Performance saved = performanceService.recalculateAndSave(performance);

        assertThat(saved.getOverallScore().doubleValue()).isGreaterThan(0.0);
    }

    @Test
    void unratedCodeforcesFallbackOutperformsNoActivity() {
        when(performanceHistoryRepository.countByStudentId(studentId)).thenReturn(0L);
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Performance none = Performance.builder().student(student).lastUpdated(Instant.now()).build();
        Performance withSolved = Performance.builder()
                .student(student).codeforcesSolved(150).lastUpdated(Instant.now()).build();

        BigDecimal base = performanceService.recalculateAndSave(none).getOverallScore();
        BigDecimal withSolvedScore = performanceService.recalculateAndSave(withSolved).getOverallScore();

        assertThat(withSolvedScore).isGreaterThan(base);
    }

    @Test
    void problemSolvingComponentCountsAllPlatforms() {
        Performance performance = Performance.builder()
                .student(student)
                .leetcodeSolved(100)
                .codeforcesSolved(50)
                .codechefSolved(50)
                .build();

        assertThat(performanceService.problemSolvingComponent(performance))
                .isEqualByComparingTo(BigDecimal.valueOf(200 / 300.0 * 10.0).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void problemSolvingComponentIsZeroWhenNoSolvedCounts() {
        Performance performance = Performance.builder()
                .student(student)
                .build();

        assertThat(performanceService.problemSolvingComponent(performance))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void syncPlatformThrowsWhenUsernameNotConfigured() {
        CodingProfile profile = CodingProfile.builder().student(student).build();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> performanceService.syncPlatform(studentId, PerformanceService.CODEFORCES))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No Codeforces username");
    }

    @Test
    void syncPlatformSavesCodeforcesRating() {
        CodingProfile profile = CodingProfile.builder()
                .student(student)
                .codeforcesUsername("tourist")
                .build();
        PlatformData data = new PlatformData("CODEFORCES", 1800, 1900, "expert", null, null, null, null, null, null, null);

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));
        when(codeforcesService.fetch("tourist")).thenReturn(Optional.of(data));
        when(performanceRepository.findByStudentId(studentId)).thenReturn(Optional.empty());
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceHistoryRepository.countByStudentId(studentId)).thenReturn(1L);

        PerformanceResponse response = performanceService.syncPlatform(studentId, PerformanceService.CODEFORCES);

        assertThat(response.codeforcesRating()).isEqualTo(1800);
        assertThat(response.lastUpdated()).isNotNull();
    }

    @Test
    void syncPlatformThrowsWhenUserNotFound() {
        CodingProfile profile = CodingProfile.builder()
                .student(student)
                .leetcodeUsername("ghost-user")
                .build();

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));
        when(leetCodeService.fetch("ghost-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> performanceService.syncPlatform(studentId, PerformanceService.LEETCODE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("LeetCode user 'ghost-user' was not found");
    }

    @Test
    void syncAllReportsPerPlatformResults() {
        CodingProfile profile = CodingProfile.builder()
                .student(student)
                .codeforcesUsername("tourist")
                .build();
        PlatformData data = new PlatformData("CODEFORCES", 1800, 1900, "expert", null, null, null, null, null, null, null);

        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));
        when(codeforcesService.fetch("tourist")).thenReturn(Optional.of(data));
        when(performanceRepository.findByStudentId(studentId)).thenReturn(Optional.empty());
        when(performanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(performanceHistoryRepository.countByStudentId(studentId)).thenReturn(1L);

        SyncAllResult result = performanceService.syncAll(studentId);

        assertThat(result.results()).hasSize(3);
        assertThat(result.results().stream().filter(r -> r.platform().equals("CODEFORCES") && r.success()).count()).isEqualTo(1);
        assertThat(result.results().stream().filter(r -> !r.success()).count()).isEqualTo(2);
    }
}
