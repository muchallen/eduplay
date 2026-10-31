package com.eduplay.backend.child.dto;

import com.eduplay.backend.child.domain.Child;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record ChildResponse(
        UUID id,
        String displayName,
        Integer birthYear,
        String avatarId,
        @Schema(description = "Current grade, e.g. ecd_a or ecd_b") String gradeId,
        @Schema(description = "OWNER: the caller created the profile and may edit or delete it. "
                + "TEACHER: the caller teaches a class the child is linked to and may read, report on and sync it.")
        Access access,
        Instant createdAt,
        Instant updatedAt) {

    public enum Access {
        OWNER,
        TEACHER
    }

    public static ChildResponse from(Child child, UUID viewerId) {
        return new ChildResponse(child.getId(), child.getDisplayName(), child.getBirthYear(), child.getAvatarId(),
                child.getGradeId(), child.getParentId().equals(viewerId) ? Access.OWNER : Access.TEACHER,
                child.getCreatedAt(), child.getUpdatedAt());
    }
}
