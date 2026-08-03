package com.codetrack.backend.dto;

import java.util.List;

public record CsvImportResult(
        int imported,
        int failed,
        List<String> errors
) {}
