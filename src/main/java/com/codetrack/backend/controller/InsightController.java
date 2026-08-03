package com.codetrack.backend.controller;

import com.codetrack.backend.dto.InsightResponse;
import com.codetrack.backend.service.InsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/students/{studentId}/insights")
@RequiredArgsConstructor
@Tag(name = "Insights", description = "AI-generated coaching insights per student (admin only)")
public class InsightController {

    private final InsightService insightService;

    @GetMapping
    @Operation(summary = "Generate coaching insights for a student (Gemini if configured, else rule-based)")
    public ResponseEntity<InsightResponse> generate(@PathVariable UUID studentId) {
        return ResponseEntity.ok(insightService.generate(studentId));
    }
}
