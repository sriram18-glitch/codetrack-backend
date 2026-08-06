package com.codetrack.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Roll number is required")
        @Size(max = 30) String rollNumber,
        @NotBlank(message = "Name is required")
        @Size(max = 150) String name,
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 150) String email,
        @NotBlank(message = "Branch is required")
        @Size(max = 100) String branch,
        @NotNull(message = "Year is required")
        @Min(value = 1, message = "Year must be between 1 and 4")
        @Max(value = 4, message = "Year must be between 1 and 4")
        Integer year,
        @NotBlank(message = "Section is required")
        @Size(max = 10) String section,
        @Size(max = 20) String phone,
        @Size(max = 100) String leetcodeUsername,
        @Size(max = 100) String codeforcesUsername,
        @Size(max = 100) String codechefUsername
) {}
