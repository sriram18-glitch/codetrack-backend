package com.codetrack.backend.controller;

import com.codetrack.backend.dto.SyncStatus;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.exception.GlobalExceptionHandler;
import com.codetrack.backend.service.BulkSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BulkSyncControllerTest {

    @Mock private BulkSyncService bulkSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BulkSyncController controller = new BulkSyncController(bulkSyncService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void syncAllAcceptsAndReturnsRunningStatus() throws Exception {
        SyncStatus running = new SyncStatus(SyncStatus.RUNNING, BulkSyncService.ALL,
                0, 0, 0, 0, 0, Instant.now(), null, null);
        when(bulkSyncService.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL)).thenReturn(running);

        mockMvc.perform(post("/api/admin/sync/all"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        verify(bulkSyncService).submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL);
    }

    @Test
    void duplicateTriggerIsRejectedWithConflict() throws Exception {
        when(bulkSyncService.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_MANUAL))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "A synchronization is already running."));

        mockMvc.perform(post("/api/admin/sync/all"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A synchronization is already running."));
    }

    @Test
    void statusEndpointReturnsCurrentProgress() throws Exception {
        SyncStatus completed = new SyncStatus(SyncStatus.COMPLETED, BulkSyncService.ALL,
                1000, 1000, 742, 230, 28, Instant.now().minusSeconds(600), Instant.now(), null);
        when(bulkSyncService.status()).thenReturn(completed);

        mockMvc.perform(get("/api/admin/sync/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents").value(1000))
                .andExpect(jsonPath("$.synced").value(742))
                .andExpect(jsonPath("$.failed").value(28))
                .andExpect(jsonPath("$.skipped").value(230));
    }
}