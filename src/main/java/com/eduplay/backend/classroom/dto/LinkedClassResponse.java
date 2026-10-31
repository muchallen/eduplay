package com.eduplay.backend.classroom.dto;

import com.eduplay.backend.classroom.repository.ClassroomRepository.LinkedClassView;
import java.time.Instant;
import java.util.UUID;

/** A class a child is linked to, as shown to the child's owner (the join code is not included). */
public record LinkedClassResponse(UUID id, String name, String gradeId, String teacherName, Instant linkedAt) {

    public static LinkedClassResponse from(LinkedClassView view) {
        return new LinkedClassResponse(view.getId(), view.getName(), view.getGradeId(), view.getTeacherName(),
                view.getLinkedAt());
    }
}
