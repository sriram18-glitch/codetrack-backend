package com.codetrack.backend.controller;

import com.codetrack.backend.dto.AnalyticsSummaryResponse;
import com.codetrack.backend.dto.AtRiskStudent;
import com.codetrack.backend.dto.BranchAnalytics;
import com.codetrack.backend.dto.LeaderboardEntry;
import com.codetrack.backend.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Leaderboards, summaries and at-risk detection (admin only)")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "Dashboard summary — totals, averages, top performer, at-risk count")
    public ResponseEntity<AnalyticsSummaryResponse> summary() {
        return ResponseEntity.ok(analyticsService.summary());
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Top performers ranked by overall readiness score")
    public ResponseEntity<List<LeaderboardEntry>> leaderboard() {
        return ResponseEntity.ok(analyticsService.leaderboard());
    }

    @GetMapping("/top-solvers")
    @Operation(summary = "Students ranked by problems solved on LeetCode")
    public ResponseEntity<List<LeaderboardEntry>> topSolvers() {
        return ResponseEntity.ok(analyticsService.topSolvers());
    }

    @GetMapping("/at-risk")
    @Operation(summary = "At-risk students — low scores, never synced, or inactive")
    public ResponseEntity<List<AtRiskStudent>> atRisk() {
        return ResponseEntity.ok(analyticsService.atRiskStudents());
    }

    @GetMapping("/by-branch")
    @Operation(summary = "Average readiness scores grouped by branch")
    public ResponseEntity<List<BranchAnalytics>> byBranch() {
        return ResponseEntity.ok(analyticsService.byBranch());
    }
}
