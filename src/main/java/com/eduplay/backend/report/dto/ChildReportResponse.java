package com.eduplay.backend.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Dashboard for one child, shown to its parent and to teachers of linked classes. */
public record ChildReportResponse(
        UUID childId,
        String displayName,
        Instant generatedAt,
        @Schema(description = "Start of the reporting window, or absent for all-time figures") Instant periodStart,
        @Schema(description = "Grade the report is limited to, or absent for all grades") String gradeId,
        Totals totals,
        @Schema(description = "One entry per (gradeId, sectionId, topicId); the same topic in two grades is "
                + "reported separately")
        List<TopicPerformance> topics,
        List<TopicSummary> strongTopics,
        List<TopicSummary> topicsNeedingImprovement,
        RecentActivity recentActivity) {

    public record Totals(
            long lessonsCompleted,
            long gamesPlayed,
            long gamesCompleted,
            long totalTimeSeconds,
            @Schema(description = "Average percent correct (0-100)") Double averageScore) {
    }

    public record TopicSummary(String gradeId, String sectionId, String topicId, Double averageScore, long gamesPlayed) {
    }

    public record RecentActivity(Instant lastPlayedAt, int activeDaysLast7, int activeDaysLast30,
                                 List<RecentGame> recentGames) {
    }

    public record RecentGame(
            String gradeId,
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
