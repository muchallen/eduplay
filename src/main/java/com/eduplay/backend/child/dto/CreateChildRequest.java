package com.eduplay.backend.child.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Only a display name (a nickname is fine), an optional birth year and an avatar are collected
 * for a child, keeping personal data to the minimum the game needs.
 */
public record CreateChildRequest(
        @NotBlank @Size(max = 40) String displayName,
        @Schema(description = "Four-digit birth year; must be within the last 18 years") Integer birthYear,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '-' and '_'")
        String avatarId) {
}
