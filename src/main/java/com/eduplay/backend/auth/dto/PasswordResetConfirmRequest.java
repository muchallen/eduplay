package com.eduplay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank @Size(min = 8, max = 128, message = "must be between 8 and 128 characters") String newPassword) {
}
