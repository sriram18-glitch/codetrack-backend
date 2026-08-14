package com.codetrack.backend.controller;

import com.codetrack.backend.dto.SyncStatus;
import com.codetrack.backend.exception.GlobalExceptionHandler;
import com.codetrack.backend.service.BulkSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalSyncControllerTest {

    private static final String SECRET = "test-sync-secret";

    @Mock private BulkSyncService bulkSyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalSyncController controller = new InternalSyncController(bulkSyncService);
        ReflectionTestUtils.setField(controller, "syncSecret", SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validSecretStartsBulkSync() throws Exception {
        SyncStatus running = new SyncStatus(SyncStatus.RUNNING, BulkSyncService.ALL,
                0, 0, 0, 0, 0, Instant.now(), null, null);
        when(bulkSyncService.submit(BulkSyncService.ALL, BulkSyncService.SOURCE_EXTERNAL)).thenReturn(running);

        mockMvc.perform(post("/api/internal/sync/daily").header("X-Sync-Secret", SECRET))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.platform").value("ALL"));

        verify(bulkSyncService).submit(BulkSyncService.ALL, BulkSyncService.SOURCE_EXTERNAL);
    }

    @Test
    void invalidSecretIsRejected() throws Exception {
        mockMvc.perform(post("/api/internal/sync/daily").header("X-Sync-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bulkSyncService);
    }

    @Test
    void missingSecretIsRejected() throws Exception {
        mockMvc.perform(post("/api/internal/sync/daily"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bulkSyncService);
    }

    @Test
    void rejectsWhenSecretNotConfigured() throws Exception {
        InternalSyncController unconfigured = new InternalSyncController(bulkSyncService);
        ReflectionTestUtils.setField(unconfigured, "syncSecret", "");
        MockMvc unconfiguredMvc = MockMvcBuilders.standaloneSetup(unconfigured)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        unconfiguredMvc.perform(post("/api/internal/sync/daily").header("X-Sync-Secret", SECRET))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bulkSyncService);
    }
}