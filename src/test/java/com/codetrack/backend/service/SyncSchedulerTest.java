package com.codetrack.backend.service;

import com.codetrack.backend.dto.SyncStatus;
import com.codetrack.backend.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

    @Mock private BulkSyncService bulkSyncService;

    @Test
    void triggersTheSharedAutomaticBulkSync() {
        when(bulkSyncService.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_AUTOMATIC))
                .thenReturn(SyncStatus.idle());

        new SyncScheduler(bulkSyncService).automaticDailySync();

        verify(bulkSyncService).submit(BulkSyncService.ALL, BulkSyncService.SOURCE_AUTOMATIC);
    }

    @Test
    void swallowsRejectionWhenAnotherRunIsInProgress() {
        doThrow(new ApiException(HttpStatus.CONFLICT, "A synchronization is already running."))
                .when(bulkSyncService).submit(any(String.class), eq(BulkSyncService.SOURCE_AUTOMATIC));

        new SyncScheduler(bulkSyncService).automaticDailySync();

        verify(bulkSyncService).submit(BulkSyncService.ALL, BulkSyncService.SOURCE_AUTOMATIC);
    }
}