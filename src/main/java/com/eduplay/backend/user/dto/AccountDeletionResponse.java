package com.eduplay.backend.user.dto;

import java.time.Instant;

public record AccountDeletionResponse(String message, Instant deletedAt, Instant permanentDeletionAfter) {
}
