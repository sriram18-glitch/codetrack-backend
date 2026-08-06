package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.CodingProfileRepository;
import com.codetrack.backend.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoSyncServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private PerformanceService performanceService;

    private AutoSyncService autoSyncService;
    private UUID studentId;
    private Student student;

    @BeforeEach
    void setUp() {
        autoSyncService = new AutoSyncService(studentRepository, codingProfileRepository, performanceService);
        studentId = UUID.randomUUID();
        student = Student.builder().id(studentId).rollNumber("21CS001").name("Ada").build();
    }

    @Test
    void syncsOnlyPlatformsWithConfiguredUsername() {
        CodingProfile profile = CodingProfile.builder().student(student).leetcodeUsername("ada_lc").build();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));
        when(performanceService.syncPlatform(studentId, "LEETCODE")).thenReturn(null);

        StudentSyncSummary summary = autoSyncService.syncStudentBestEffort(studentId);

        assertThat(summary.attempted()).isEqualTo(1);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        verify(performanceService).syncPlatform(studentId, "LEETCODE");
        verifyNoMoreInteractions(performanceService);
    }

    @Test
    void continuesAfterPlatformFailure() {
        CodingProfile profile = CodingProfile.builder()
                .student(student)
                .leetcodeUsername("ada_lc")
                .codeforcesUsername("ada_cf")
                .build();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));
        doThrow(new RuntimeException("network down")).when(performanceService).syncPlatform(studentId, "LEETCODE");
        when(performanceService.syncPlatform(studentId, "CODEFORCES")).thenReturn(null);

        StudentSyncSummary result = autoSyncService.syncStudentBestEffort(studentId);

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }

    @Test
    void skipsStudentWithoutCodingProfile() {
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.empty());

        StudentSyncSummary summary = autoSyncService.syncStudentBestEffort(studentId);

        assertThat(summary.attempted()).isZero();
        verifyNoMoreInteractions(performanceService);
    }

    @Test
    void skipsMissingStudent() {
        when(studentRepository.findById(studentId)).thenReturn(Optional.empty());

        StudentSyncSummary summary = autoSyncService.syncStudentBestEffort(studentId);

        assertThat(summary.attempted()).isZero();
        verifyNoMoreInteractions(performanceService);
    }

    @Test
    void neverThrowsWhenSyncPlatformFailsForEveryPlatform() {
        CodingProfile profile = CodingProfile.builder()
                .student(student)
                .leetcodeUsername("ada_lc")
                .codeforcesUsername("ada_cf")
                .codechefUsername("ada_cc")
                .build();
        when(studentRepository.findById(studentId)).thenReturn(Optional.of(student));
        when(codingProfileRepository.findByStudentId(studentId)).thenReturn(Optional.of(profile));
        doThrow(new RuntimeException("boom")).when(performanceService).syncPlatform(eq(studentId), any());

        assertThatCode(() -> autoSyncService.syncStudentBestEffort(studentId)).doesNotThrowAnyException();
        verify(performanceService).syncPlatform(studentId, "LEETCODE");
        verify(performanceService).syncPlatform(studentId, "CODEFORCES");
        verify(performanceService).syncPlatform(studentId, "CODECHEF");
    }
}
