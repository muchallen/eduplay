package com.eduplay.backend.progress.dto;

import com.eduplay.backend.grade.GradeCatalog;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/** A lesson position recorded on the device while offline. */
public record LessonProgressItem(
        @Schema(description = "Grade the lesson belongs to", example = "ecd_a")
        @NotNull @Pattern(regexp = GradeCatalog.PATTERN, message = GradeCatalog.MESSAGE) String gradeId,
        @NotNull @Pattern(regexp = ContentIds.PATTERN, message = ContentIds.MESSAGE) String sectionId,
        @NotNull @Pattern(regexp = ContentIds.PATTERN, message = ContentIds.MESSAGE) String topicId,
        @NotNull @Pattern(regexp = ContentIds.PATTERN, message = ContentIds.MESSAGE) String lessonId,
        @NotNull @Min(0) @Max(1000) Integer currentGameIndex,
        @NotNull Boolean completed,
        Instant updatedAt) {
}
