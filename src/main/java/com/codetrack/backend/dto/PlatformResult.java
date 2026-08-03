package com.codetrack.backend.dto;

public record PlatformResult(
        String platform,
        boolean success,
        String message
) {}
