package com.codetrack.backend.controller;

import com.codetrack.backend.dto.PerformanceHistoryResponse;
import com.codetrack.backend.dto.PerformanceResponse;
import com.codetrack.backend.dto.SyncAllResult;
import com.codetrack.backend.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/students/{studentId}/performance")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "Live performance sync, snapshots and scoring (admin only)")
public class PerformanceController {

    private final PerformanceService performanceService;

    @GetMapping
    @Operation(summary = "Get a student's current performance snapshot")
    public ResponseEntity<PerformanceResponse> getPerformance(@PathVariable UUID studentId) {
        return ResponseEntity.ok(performanceService.getPerformance(studentId));
    }

    @GetMapping("/history")
    @Operation(summary = "Get a student's performance history (time series for trend graphs)")
    public ResponseEntity<List<PerformanceHistoryResponse>> getHistory(@PathVariable UUID studentId) {
        return ResponseEntity.ok(performanceService.getHistory(studentId));
    }

    @PostMapping("/sync/leetcode")
    @Operation(summary = "Fetch live LeetCode data and update the performance snapshot")
    public ResponseEntity<PerformanceResponse> syncLeetCode(@PathVariable UUID studentId) {
        return ResponseEntity.ok(performanceService.syncPlatform(studentId, PerformanceService.LEETCODE));
    }

    @PostMapping("/sync/codeforces")
    @Operation(summary = "Fetch live Codeforces data and update the performance snapshot")
    public ResponseEntity<PerformanceResponse> syncCodeforces(@PathVariable UUID studentId) {
        return ResponseEntity.ok(performanceService.syncPlatform(studentId, PerformanceService.CODEFORCES));
    }

    @PostMapping("/sync/codechef")
    @Operation(summary = "Fetch live CodeChef data and update the performance snapshot")
    public ResponseEntity<PerformanceResponse> syncCodeChef(@PathVariable UUID studentId) {
        return ResponseEntity.ok(performanceService.syncPlatform(studentId, PerformanceService.CODECHEF));
    }

    @PostMapping("/sync/all")
    @Operation(summary = "Fetch live data from all configured platforms (each platform reports its own result)")
    public ResponseEntity<SyncAllResult> syncAll(@PathVariable UUID studentId) {
        return ResponseEntity.ok(performanceService.syncAll(studentId));
    }
}
