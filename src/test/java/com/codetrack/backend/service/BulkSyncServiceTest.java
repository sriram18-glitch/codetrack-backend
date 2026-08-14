package com.codetrack.backend.service;

import com.codetrack.backend.dto.StudentSyncSummary;
import com.codetrack.backend.dto.SyncStatus;
import com.codetrack.backend.entity.CodingProfile;
import com.codetrack.backend.entity.Student;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.repository.CodingProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkSyncServiceTest {

    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private AutoSyncService autoSyncService;

    private BulkSyncService service() {
        BulkSyncService s = new BulkSyncService(codingProfileRepository, autoSyncService);
        ReflectionTestUtils.setField(s, "requestDelayMs", 0L);
        return s;
    }

    private static Student student(String roll, String name) {
        return Student.builder().id(UUID.randomUUID()).rollNumber(roll).name(name).build();
    }

    private static CodingProfile profile(Student st) {
        return CodingProfile.builder().student(st).build();
    }

    private static void awaitCompletion(BulkSyncService s) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline && SyncStatus.RUNNING.equals(s.status().status())) {
            Thread.sleep(10);
        }
        assertNotEquals(SyncStatus.RUNNING, s.status().status(), "bulk sync did not complete in time");
    }

    @Test
    void submitStartsRunAsynchronouslyAndReportsCompleted() throws Exception {
        Student s1 = student("R1", "One");
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(profile(s1)));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(3, 3, 0));

        BulkSyncService s = service();
        SyncStatus started = s.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL);

        assertEquals(SyncStatus.RUNNING, started.status());
        awaitCompletion(s);

        SyncStatus done = s.status();
        assertEquals(SyncStatus.COMPLETED, done.status());
        assertEquals(1, done.totalStudents());
        assertEquals(1, done.synced());
        assertEquals(0, done.failed());
        assertEquals(0, done.skipped());
    }

    @Test
    void duplicateSubmitWhileRunningIsRejected() throws Exception {
        Student s1 = student("R1", "One");
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(profile(s1)));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new StudentSyncSummary(1, 1, 0);
        });

        BulkSyncService s = service();
        s.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL);
        assertTrue(entered.await(5, TimeUnit.SECONDS), "worker should have entered the sync");

        assertThrows(ApiException.class, () -> s.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL));

        release.countDown();
        awaitCompletion(s);
        assertEquals(SyncStatus.COMPLETED, s.status().status());
    }

    @Test
    void countsSyncedFailedAndSkippedAcrossStudents() throws Exception {
        Student a = student("R1", "A");
        Student b = student("R2", "B");
        Student c = student("R3", "C");
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(profile(a), profile(b), profile(c)));
        when(autoSyncService.syncStudentBestEffort(a.getId())).thenReturn(new StudentSyncSummary(3, 3, 0));
        when(autoSyncService.syncStudentBestEffort(b.getId())).thenReturn(new StudentSyncSummary(2, 1, 1));
        when(autoSyncService.syncStudentBestEffort(c.getId())).thenReturn(new StudentSyncSummary(0, 0, 0));

        BulkSyncService s = service();
        s.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL);
        awaitCompletion(s);

        SyncStatus done = s.status();
        assertEquals(3, done.totalStudents());
        assertEquals(3, done.processed());
        assertEquals(1, done.synced());
        assertEquals(1, done.failed());
        assertEquals(1, done.skipped());
        assertEquals(SyncStatus.COMPLETED, done.status());
    }

    @Test
    void singlePlatformSyncsOnlyStudentsWithThatHandle() throws Exception {
        Student a = student("R1", "A");
        Student b = student("R2", "B");
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(profile(a), profile(b)));
        when(autoSyncService.syncPlatformBestEffort(a.getId(), PerformanceService.LEETCODE))
                .thenReturn(new StudentSyncSummary(1, 1, 0));
        when(autoSyncService.syncPlatformBestEffort(b.getId(), PerformanceService.LEETCODE))
                .thenReturn(new StudentSyncSummary(0, 0, 0));

        BulkSyncService s = service();
        s.submit(PerformanceService.LEETCODE, BulkSyncService.SOURCE_MANUAL);
        awaitCompletion(s);

        verify(autoSyncService).syncPlatformBestEffort(a.getId(), PerformanceService.LEETCODE);
        verify(autoSyncService).syncPlatformBestEffort(b.getId(), PerformanceService.LEETCODE);
        assertEquals(1, s.status().synced());
        assertEquals(1, s.status().skipped());
        assertEquals(0, s.status().failed());
    }

    @Test
    void oneStudentExceptionDoesNotStopOthers() throws Exception {
        Student a = student("R1", "A");
        Student b = student("R2", "B");
        when(codingProfileRepository.findAllWithStudent()).thenReturn(List.of(profile(a), profile(b)));
        doThrow(new RuntimeException("boom")).when(autoSyncService).syncStudentBestEffort(a.getId());
        when(autoSyncService.syncStudentBestEffort(b.getId())).thenReturn(new StudentSyncSummary(1, 1, 0));

        BulkSyncService s = service();
        s.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL);
        awaitCompletion(s);

        verify(autoSyncService).syncStudentBestEffort(a.getId());
        verify(autoSyncService).syncStudentBestEffort(b.getId());
        assertEquals(2, s.status().processed());
        assertEquals(1, s.status().failed());
        assertEquals(1, s.status().synced());
        assertEquals(SyncStatus.COMPLETED, s.status().status());
    }
}
