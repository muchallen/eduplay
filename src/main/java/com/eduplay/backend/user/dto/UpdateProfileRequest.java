package com.eduplay.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: omitted (null) fields are left unchanged. An empty phone clears it. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 120) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String fullName,
        @Schema(description = "Phone number, or an empty string to remove it")
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,20}$", message = "must be a valid phone number") String phone) {
}
