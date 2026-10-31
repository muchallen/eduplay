package com.eduplay.backend.child.dto;

import com.eduplay.backend.child.domain.Child;
import java.time.Instant;
import java.util.UUID;

public record ChildResponse(
        UUID id,
        String displayName,
        Integer birthYear,
        String avatarId,
        Instant createdAt,
        Instant updatedAt) {

    public static ChildResponse from(Child child) {
        return new ChildResponse(child.getId(), child.getDisplayName(), child.getBirthYear(), child.getAvatarId(),
                child.getCreatedAt(), child.getUpdatedAt());
    }
}
