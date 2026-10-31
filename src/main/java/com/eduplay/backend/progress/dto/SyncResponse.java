package com.eduplay.backend.progress.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncResponse(
        UUID childId,
        @Schema(description = "Server clock (UTC); use it to correct for device clock drift")
        Instant serverTime,
        @Schema(description = "Game results stored by this request")
        int acceptedGameResults,
        @Schema(description = "Game results ignored because their id was already stored")
        int duplicateGameResults,
        @Schema(description = "Complete merged lesson progress for the child; replace the local copy with it")
        List<LessonProgressResponse> lessonProgress) {
}
