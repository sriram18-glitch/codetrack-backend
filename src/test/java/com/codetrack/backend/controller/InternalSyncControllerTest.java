package com.codetrack.backend.controller;

import com.codetrack.backend.dto.DailySyncResult;
import com.codetrack.backend.exception.GlobalExceptionHandler;
import com.codetrack.backend.service.DailySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalSyncControllerTest {

    private static final String SECRET = "test-sync-secret";

    @Mock private DailySyncService dailySyncService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalSyncController controller = new InternalSyncController(dailySyncService);
        ReflectionTestUtils.setField(controller, "syncSecret", SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validSecretStartsSync() throws Exception {
        when(dailySyncService.runDailySync(DailySyncService.SOURCE_EXTERNAL))
                .thenReturn(new DailySyncResult(3, 1, 4, false));

        mockMvc.perform(post("/api/internal/sync/daily").header("X-Sync-Secret", SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(3))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.skipped").value(false));

        verify(dailySyncService).runDailySync(DailySyncService.SOURCE_EXTERNAL);
    }

    @Test
    void invalidSecretIsRejected() throws Exception {
        mockMvc.perform(post("/api/internal/sync/daily").header("X-Sync-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dailySyncService);
    }

    @Test
    void missingSecretIsRejected() throws Exception {
        mockMvc.perform(post("/api/internal/sync/daily"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dailySyncService);
    }

    @Test
    void rejectsWhenSecretNotConfigured() throws Exception {
        InternalSyncController unconfigured = new InternalSyncController(dailySyncService);
        ReflectionTestUtils.setField(unconfigured, "syncSecret", "");
        MockMvc unconfiguredMvc = MockMvcBuilders.standaloneSetup(unconfigured)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        unconfiguredMvc.perform(post("/api/internal/sync/daily").header("X-Sync-Secret", SECRET))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dailySyncService);
    }
}
