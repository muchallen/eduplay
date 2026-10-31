package com.eduplay.backend.classroom.dto;

import com.eduplay.backend.grade.GradeCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateClassroomRequest(
        @Schema(example = "ECD A Blue") @NotBlank @Size(max = 80) String name,
        @Schema(description = "Optional grade the class is for", example = "ecd_a")
        @Pattern(regexp = GradeCatalog.PATTERN, message = GradeCatalog.MESSAGE) String gradeId) {
}
