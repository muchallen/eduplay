package com.eduplay.backend.classroom.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinClassRequest(
        @Schema(description = "Join code given by the teacher; case and spaces are ignored", example = "K7QM4XPA")
        @NotBlank @Size(max = 32) String joinCode) {
}
