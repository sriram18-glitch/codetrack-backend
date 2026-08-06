package com.codetrack.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStudentRequest(
        @NotBlank @Size(max = 30) String rollNumber,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 100) String branch,
        Integer year,
        @Size(max = 10) String section,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "\\d{10}", message = "Phone number must be exactly 10 digits")
        @Size(max = 20) String phone,
        @Size(max = 100) String leetcodeUsername,
        @Size(max = 100) String codeforcesUsername,
        @Size(max = 100) String codechefUsername
) {}
