package com.eduplay.backend.child.dto;

import com.eduplay.backend.grade.GradeCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Only a display name (a nickname is fine), an optional birth year, an avatar and the grade are
 * collected for a child, keeping personal data to the minimum the game needs.
 */
public record CreateChildRequest(
        @NotBlank @Size(max = 40) String displayName,
        @Schema(description = "Four-digit birth year; must be within the last 18 years") Integer birthYear,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '-' and '_'")
        String avatarId,
        @Schema(description = "Grade the child is in: ecd_a or ecd_b", example = "ecd_a")
        @NotNull @Pattern(regexp = GradeCatalog.PATTERN, message = GradeCatalog.MESSAGE) String gradeId) {
}
