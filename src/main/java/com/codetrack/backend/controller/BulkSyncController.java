package com.codetrack.backend.controller;

import com.codetrack.backend.dto.SyncStatus;
import com.codetrack.backend.service.BulkSyncService;
import com.codetrack.backend.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin bulk synchronization. Every endpoint requires a valid admin JWT via
 * the normal security filter chain. Work is started asynchronously and the
 * endpoints return 202 Accepted immediately; progress is available from the
 * status endpoint. A duplicate trigger while a job is running is rejected
 * with 409.
 */
@RestController
@RequestMapping("/api/admin/sync")
@RequiredArgsConstructor
@Tag(name = "Admin Sync", description = "Bulk synchronization for all students (admin only)")
public class BulkSyncController {

    private final BulkSyncService bulkSyncService;

    @PostMapping("/all")
    @Operation(summary = "Sync every platform for all students with configured handles")
    public ResponseEntity<SyncStatus> syncAll() {
        return start(BulkSyncService.ALL);
    }

    @PostMapping("/leetcode")
    @Operation(summary = "Sync LeetCode for all students with a LeetCode handle")
    public ResponseEntity<SyncStatus> syncLeetCode() {
        return start(PerformanceService.LEETCODE);
    }

    @PostMapping("/codeforces")
    @Operation(summary = "Sync Codeforces for all students with a Codeforces handle")
    public ResponseEntity<SyncStatus> syncCodeforces() {
        return start(PerformanceService.CODEFORCES);
    }

    @PostMapping("/codechef")
    @Operation(summary = "Sync CodeChef for all students with a CodeChef handle")
    public ResponseEntity<SyncStatus> syncCodeChef() {
        return start(PerformanceService.CODECHEF);
    }

    @GetMapping("/status")
    @Operation(summary = "Current bulk synchronization progress")
    public ResponseEntity<SyncStatus> status() {
        return ResponseEntity.ok(bulkSyncService.status());
    }

    private ResponseEntity<SyncStatus> start(String platform) {
        SyncStatus started = bulkSyncService.submit(platform, BulkSyncService.SOURCE_MANUAL);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(started);
    }
}