package com.codetrack.backend.dto;

import java.util.List;

public record SyncAllResult(
        List<PlatformResult> results
) {}
