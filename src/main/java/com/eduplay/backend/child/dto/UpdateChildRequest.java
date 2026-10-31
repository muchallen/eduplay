package com.eduplay.backend.child.dto;

import com.eduplay.backend.grade.GradeCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: omitted (null) fields are left unchanged. */
public record UpdateChildRequest(
        @Size(min = 1, max = 40) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String displayName,
        Integer birthYear,
        @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "may contain letters, digits, '-' and '_'")
        String avatarId,
        @Schema(description = "Moves the child to another grade. Results already recorded keep the grade they "
                + "were played in.", example = "ecd_b")
        @Pattern(regexp = GradeCatalog.PATTERN, message = GradeCatalog.MESSAGE) String gradeId) {
}
