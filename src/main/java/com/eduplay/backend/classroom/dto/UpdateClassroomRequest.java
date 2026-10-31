package com.eduplay.backend.classroom.dto;

import com.eduplay.backend.grade.GradeCatalog;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: omitted (null) fields are left unchanged. */
public record UpdateClassroomRequest(
        @Size(min = 1, max = 80) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String name,
        @Pattern(regexp = GradeCatalog.PATTERN, message = GradeCatalog.MESSAGE) String gradeId) {
}
