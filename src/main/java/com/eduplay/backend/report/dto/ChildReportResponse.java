package com.eduplay.backend.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Parent dashboard for one child. */
public record ChildReportResponse(
        UUID childId,
        String displayName,
        Instant generatedAt,
        @Schema(description = "Start of the reporting window, or absent for all-time figures") Instant periodStart,
        Totals totals,
        List<TopicPerformance> topics,
        List<TopicSummary> strongTopics,
        List<TopicSummary> topicsNeedingImprovement,
        RecentActivity recentActivity) {

    public record Totals(
            long lessonsCompleted,
            long gamesPlayed,
            long gamesCompleted,
            long totalTimeSeconds,
            Double averageScore) {
    }

    public record TopicSummary(String sectionId, String topicId, Double averageScore, long gamesPlayed) {
    }

    public record RecentActivity(Instant lastPlayedAt, int activeDaysLast7, int activeDaysLast30,
                                 List<RecentGame> recentGames) {
    }

    public record RecentGame(
            String sectionId,
            String topicId,
            String lessonId,
            int gameIndex,
            String gameType,
            int score,
            int mistakes,
            int durationSeconds,
            boolean completed,
            Instant playedAt) {
    }
}
