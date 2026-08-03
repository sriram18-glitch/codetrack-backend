package com.codetrack.backend.dto;

import jakarta.validation.constraints.Size;

public record CodingProfileRequest(
        @Size(max = 100) String leetcodeUsername,
        @Size(max = 100) String codeforcesUsername,
        @Size(max = 100) String codechefUsername
) {}