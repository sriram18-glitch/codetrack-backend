package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.repository.CodingProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private AutoSyncService autoSyncService;

    @Test
    void syncsEveryStudentThatHasACodingProfile() {
        Student s1 = Student.builder().id(UUID.randomUUID()).rollNumber("R1").name("One").build();
        Student s2 = Student.builder().id(UUID.randomUUID()).rollNumber("R2").name("Two").build();
        CodingProfile p1 = CodingProfile.builder().student(s1).build();
        CodingProfile p2 = CodingProfile.builder().student(s2).build();
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(p1, p2));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(2, 2, 0));
        when(autoSyncService.syncStudentBestEffort(s2.getId())).thenReturn(new StudentSyncSummary(1, 0, 1));

        new SyncScheduler(codingProfileRepository, autoSyncService).automaticDailySync();

        InOrder order = inOrder(autoSyncService);
        order.verify(autoSyncService).syncStudentBestEffort(s1.getId());
        order.verify(autoSyncService).syncStudentBestEffort(s2.getId());
    }

    @Test
    void continuesWithNextStudentWhenOneSyncThrows() {
        Student s1 = Student.builder().id(UUID.randomUUID()).rollNumber("R1").name("One").build();
        Student s2 = Student.builder().id(UUID.randomUUID()).rollNumber("R2").name("Two").build();
        CodingProfile p1 = CodingProfile.builder().student(s1).build();
        CodingProfile p2 = CodingProfile.builder().student(s2).build();
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(p1, p2));
        doThrow(new RuntimeException("boom")).when(autoSyncService).syncStudentBestEffort(s1.getId());
        when(autoSyncService.syncStudentBestEffort(s2.getId())).thenReturn(new StudentSyncSummary(1, 1, 0));

        new SyncScheduler(codingProfileRepository, autoSyncService).automaticDailySync();

        verify(autoSyncService).syncStudentBestEffort(s1.getId());
        verify(autoSyncService).syncStudentBestEffort(s2.getId());
    }

    @Test
    void skipsProfileWithoutStudent() {
        Student s1 = Student.builder().id(UUID.randomUUID()).rollNumber("R1").name("One").build();
        CodingProfile p1 = CodingProfile.builder().student(s1).build();
        CodingProfile orphan = CodingProfile.builder().student(null).build();
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(p1, orphan));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(1, 1, 0));

        new SyncScheduler(codingProfileRepository, autoSyncService).automaticDailySync();

        verify(autoSyncService).syncStudentBestEffort(s1.getId());
    }

    @Test
    void stillProcessesStudentsWithoutConfiguredHandles() {
        Student s1 = Student.builder().id(UUID.randomUUID()).rollNumber("R1").name("One").build();
        CodingProfile p1 = CodingProfile.builder().student(s1).build();
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(p1));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(0, 0, 0));

        new SyncScheduler(codingProfileRepository, autoSyncService).automaticDailySync();

        verify(autoSyncService).syncStudentBestEffort(s1.getId());
    }
}
