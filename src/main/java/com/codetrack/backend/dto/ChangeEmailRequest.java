package com.codetrack.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ChangeEmailRequest(
        @NotBlank String currentPassword,
        @NotBlank @Email(message = "Invalid email format") String newEmail
) {}
