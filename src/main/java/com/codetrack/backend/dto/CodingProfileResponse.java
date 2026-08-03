package com.codetrack.backend.dto;

import java.util.UUID;

public record CodingProfileResponse(
        UUID id,
        UUID studentId,
        String leetcodeUsername,
        String codeforcesUsername,
        String codechefUsername
) {}