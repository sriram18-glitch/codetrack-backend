package com.codetrack.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateStudentRequest(
        @NotBlank @Size(max = 30) String rollNumber,
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 150) String email,
        @Size(max = 100) String branch,
        Integer year,
        @Size(max = 10) String section,
        @Size(max = 20) String phone,
        @Size(max = 100) String leetcodeUsername,
        @Size(max = 100) String codeforcesUsername,
        @Size(max = 100) String codechefUsername
) {}