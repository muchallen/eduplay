package com.eduplay.backend.classroom.dto;

import com.eduplay.backend.classroom.domain.Classroom;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ClassroomResponse(
        UUID id,
        String name,
        String gradeId,
        @Schema(description = "Code parents enter to link their child to this class", example = "K7QM4XPA")
        String joinCode,
        long childCount,
        Instant createdAt,
        Instant updatedAt) {

    public static ClassroomResponse from(Classroom classroom, long childCount) {
        return new ClassroomResponse(classroom.getId(), classroom.getName(), classroom.getGradeId(),
                classroom.getJoinCode(), childCount, classroom.getCreatedAt(), classroom.getUpdatedAt());
    }
}
