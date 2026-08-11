package com.codetrack.backend.controller;

import com.codetrack.backend.dto.DailySyncResult;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.service.DailySyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Internal endpoints for external schedulers. These are outside the normal
 * JWT flow and are instead protected by a shared secret supplied through the
 * {@code X-Sync-Secret} request header. The secret is read only from the
 * {@code CODETRACK_SYNC_SECRET} environment variable and is never logged,
 * stored, or returned to clients.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
@Tag(name = "Internal", description = "Secret-guarded endpoints for external cron triggers")
public class InternalSyncController {

    public static final String SYNC_SECRET_HEADER = "X-Sync-Secret";

    private final DailySyncService dailySyncService;

    @Value("${codetrack.sync.secret:}")
    private String syncSecret;

    @PostMapping("/sync/daily")
    @Operation(summary = "Trigger the daily synchronization from an external cron. " +
            "Requires the X-Sync-Secret header. Returns success/failed/total counts.")
    public ResponseEntity<DailySyncResult> triggerDailySync(
            @RequestHeader(value = SYNC_SECRET_HEADER, required = false) String providedSecret) {
        if (!isValidSecret(providedSecret)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or missing " + SYNC_SECRET_HEADER);
        }
        DailySyncResult result = dailySyncService.runDailySync(DailySyncService.SOURCE_EXTERNAL);
        return ResponseEntity.ok(result);
    }

    private boolean isValidSecret(String providedSecret) {
        if (syncSecret == null || syncSecret.isBlank() || providedSecret == null || providedSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                syncSecret.getBytes(StandardCharsets.UTF_8));
    }
}
