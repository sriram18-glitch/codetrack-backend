package com.codetrack.backend.service;

import com.codetrack.backend.dto.DailySyncResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

    @Mock private DailySyncService dailySyncService;

    @Test
    void delegatesToSharedDailySyncService() {
        when(dailySyncService.runDailySync(DailySyncService.SOURCE_AUTOMATIC))
                .thenReturn(new DailySyncResult(2, 0, 2, false));

        new SyncScheduler(dailySyncService).automaticDailySync();

        verify(dailySyncService).runDailySync(DailySyncService.SOURCE_AUTOMATIC);
    }

    @Test
    void toleratesSkippedResultWhenAnotherRunIsInProgress() {
        when(dailySyncService.runDailySync(DailySyncService.SOURCE_AUTOMATIC))
                .thenReturn(DailySyncResult.skippedRun());

        new SyncScheduler(dailySyncService).automaticDailySync();

        verify(dailySyncService).runDailySync(DailySyncService.SOURCE_AUTOMATIC);
    }
}
