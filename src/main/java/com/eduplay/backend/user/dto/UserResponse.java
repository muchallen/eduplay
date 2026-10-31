package com.eduplay.backend.user.dto;

import com.eduplay.backend.user.domain.Role;
import com.eduplay.backend.user.domain.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Role role,
        boolean emailVerified,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getRole(),
                user.isEmailVerified(), user.getCreatedAt());
    }
}
