package com.codetrack.backend.controller;

import com.codetrack.backend.dto.CsvImportResult;
import com.codetrack.backend.exception.ApiException;
import com.codetrack.backend.service.CsvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
@Tag(name = "Students", description = "Bulk CSV import/export (admin only)")
public class CsvController {

    private final CsvService csvService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-add students from a CSV file (columns: rollNumber,name,email,branch,year,section,phone)")
    public ResponseEntity<CsvImportResult> importStudents(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file is empty");
        }
        try {
            return ResponseEntity.ok(csvService.importStudents(file.getInputStream()));
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not read uploaded CSV file");
        }
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export all students (with performance columns) as CSV")
    public ResponseEntity<String> exportCsv() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename("students.csv").build());
        return ResponseEntity.ok().headers(headers).body(csvService.exportCsv());
    }
}
