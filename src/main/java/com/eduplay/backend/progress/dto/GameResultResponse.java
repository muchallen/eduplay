package com.eduplay.backend.progress.dto;

import com.eduplay.backend.progress.domain.GameResult;
import java.time.Instant;
import java.util.UUID;

public record GameResultResponse(
        UUID id,
        String gradeId,
        String sectionId,
        String topicId,
        String lessonId,
        int gameIndex,
        String gameType,
        int score,
        int attempts,
        int mistakes,
        int durationSeconds,
        boolean completed,
        Instant playedAt,
        Instant receivedAt) {

    public static GameResultResponse from(GameResult result) {
        return new GameResultResponse(result.getId(), result.getGradeId(), result.getSectionId(), result.getTopicId(),
                result.getLessonId(), result.getGameIndex(), result.getGameType(), result.getScore(),
                result.getAttempts(), result.getMistakes(), result.getDurationSeconds(), result.isCompleted(),
                result.getPlayedAt(), result.getReceivedAt());
    }
}
