package com.eduplay.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128, message = "must be between 8 and 128 characters") String password,
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone,
        @Schema(description = "Privacy policy version the parent accepted, e.g. 2026-01")
        @NotBlank @Size(max = 32) String policyVersion,
        @Schema(description = "Parent confirms consent to processing their children's learning data")
        @AssertTrue(message = "parental consent must be given") boolean parentalConsent,
        @Size(max = 100) String deviceLabel) {
}
