package com.eduplay.backend.report.dto;

import java.time.Instant;

public record TopicPerformance(
        String sectionId,
        String topicId,
        long gamesPlayed,
        Double averageScore,
        Double averageMistakes,
        long timeSpentSeconds,
        long lessonsCompleted,
        long lessonsStarted,
        Instant lastPlayedAt,
        Rating rating) {

    public enum Rating {
        STRONG,
        DEVELOPING,
        NEEDS_IMPROVEMENT,
        NOT_ENOUGH_DATA
    }
}
