package com.codetrack.backend.controller;

import com.codetrack.backend.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "PDF report generation (admin only)")
public class ReportController {

    private final ReportService reportService;

    @GetMapping(value = "/students/{studentId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a PDF readiness report for a student")
    public ResponseEntity<byte[]> studentReport(@PathVariable UUID studentId) {
        byte[] pdf = reportService.generateStudentReport(studentId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("student-report.pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping(value = "/college/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a PDF college-wide summary report")
    public ResponseEntity<byte[]> collegeReport() {
        byte[] pdf = reportService.generateCollegeReport();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename("college-report.pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a PDF year-wise report (all years or a specific year)")
    public ResponseEntity<byte[]> yearReport(
            @RequestParam(value = "year", required = false, defaultValue = "all") String year) {
        byte[] pdf = reportService.generateYearReport(year);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("year-report-" + year + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @GetMapping(value = "/branches/{branch}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download a PDF report for a specific branch")
    public ResponseEntity<byte[]> branchReport(@PathVariable String branch) {
        byte[] pdf = reportService.generateBranchReport(branch);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("branch-report-" + branch + ".pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}
