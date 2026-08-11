package com.codetrack.backend.service;

import com.codetrack.backend.dto.DailySyncResult;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySyncServiceTest {

    @Mock private CodingProfileRepository codingProfileRepository;
    @Mock private AutoSyncService autoSyncService;

    private DailySyncService service() {
        return new DailySyncService(codingProfileRepository, autoSyncService);
    }

    private Student student(String roll, String name) {
        return Student.builder().id(UUID.randomUUID()).rollNumber(roll).name(name).build();
    }

    @Test
    void syncsEveryStudentWithAProfileAndReturnsCounts() {
        Student s1 = student("R1", "One");
        Student s2 = student("R2", "Two");
        when(codingProfileRepository.findAllWithStudent())
                .thenReturn(List.of(CodingProfile.builder().student(s1).build(),
                        CodingProfile.builder().student(s2).build()));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(2, 2, 0));
        when(autoSyncService.syncStudentBestEffort(s2.getId())).thenReturn(new StudentSyncSummary(1, 1, 0));

        DailySyncResult result = service().runDailySync(DailySyncService.SOURCE_EXTERNAL);

        assertEquals(2, result.success());
        assertEquals(0, result.failed());
        assertEquals(2, result.total());
        assertFalse(result.skipped());

        InOrder order = inOrder(autoSyncService);
        order.verify(autoSyncService).syncStudentBestEffort(s1.getId());
        order.verify(autoSyncService).syncStudentBestEffort(s2.getId());
    }

    @Test
    void continuesWithNextStudentWhenOneSyncThrows() {
        Student s1 = student("R1", "One");
        Student s2 = student("R2", "Two");
        when(codingProfileRepository.findAllWithStudent())
                .thenReturn(List.of(CodingProfile.builder().student(s1).build(),
                        CodingProfile.builder().student(s2).build()));
        doThrow(new RuntimeException("boom")).when(autoSyncService).syncStudentBestEffort(s1.getId());
        when(autoSyncService.syncStudentBestEffort(s2.getId())).thenReturn(new StudentSyncSummary(1, 1, 0));

        DailySyncResult result = service().runDailySync(DailySyncService.SOURCE_EXTERNAL);

        assertEquals(1, result.success());
        assertEquals(1, result.failed());
        assertEquals(2, result.total());
        verify(autoSyncService).syncStudentBestEffort(s1.getId());
        verify(autoSyncService).syncStudentBestEffort(s2.getId());
    }

    @Test
    void countsAStudentWithFailedPlatformsAsFailed() {
        Student s1 = student("R1", "One");
        when(codingProfileRepository.findAllWithStudent())
                .thenReturn(List.of(CodingProfile.builder().student(s1).build()));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(3, 1, 2));

        DailySyncResult result = service().runDailySync(DailySyncService.SOURCE_EXTERNAL);

        assertEquals(0, result.success());
        assertEquals(1, result.failed());
        assertEquals(1, result.total());
    }

    @Test
    void skipsProfileWithoutStudent() {
        Student s1 = student("R1", "One");
        when(codingProfileRepository.findAllWithStudent())
                .thenReturn(List.of(CodingProfile.builder().student(s1).build(),
                        CodingProfile.builder().student(null).build()));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(1, 1, 0));

        DailySyncResult result = service().runDailySync(DailySyncService.SOURCE_EXTERNAL);

        assertEquals(1, result.success());
        verify(autoSyncService).syncStudentBestEffort(s1.getId());
    }

    @Test
    void doesNotCountStudentsWithoutConfiguredHandlesAsFailed() {
        Student s1 = student("R1", "One");
        when(codingProfileRepository.findAllWithStudent())
                .thenReturn(List.of(CodingProfile.builder().student(s1).build()));
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenReturn(new StudentSyncSummary(0, 0, 0));

        DailySyncResult result = service().runDailySync(DailySyncService.SOURCE_EXTERNAL);

        assertEquals(0, result.success());
        assertEquals(0, result.failed());
        assertEquals(1, result.total());
    }

    @Test
    void duplicateConcurrentRunIsSkipped() throws Exception {
        Student s1 = student("R1", "One");
        when(codingProfileRepository.findAllWithStudent())
                .thenReturn(List.of(CodingProfile.builder().student(s1).build()));

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(autoSyncService.syncStudentBestEffort(s1.getId())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new StudentSyncSummary(1, 1, 0);
        });

        DailySyncService syncService = service();
        AtomicReference<DailySyncResult> firstResult = new AtomicReference<>();
        Thread first = new Thread(() -> firstResult.set(syncService.runDailySync(DailySyncService.SOURCE_AUTOMATIC)));
        first.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS), "first sync should be running");

        DailySyncResult second = syncService.runDailySync(DailySyncService.SOURCE_EXTERNAL);
        assertTrue(second.skipped());
        assertEquals(0, second.total());

        release.countDown();
        first.join(5000);
        assertEquals(1, firstResult.get().success());
        assertFalse(firstResult.get().skipped());

        verify(autoSyncService, times(1)).syncStudentBestEffort(s1.getId());
    }
}
