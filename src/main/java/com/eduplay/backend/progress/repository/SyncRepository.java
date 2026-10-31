package com.eduplay.backend.progress.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Set-based writes for offline synchronisation. Merging happens inside PostgreSQL with
 * {@code INSERT ... ON CONFLICT}, so concurrent or retried uploads from several devices
 * converge on the same result without read-modify-write races.
 */
@Repository
@RequiredArgsConstructor
public class SyncRepository {

    /**
     * Merge rules:
     * completed wins over incomplete; when both rows agree on completion the higher game index wins;
     * the original completion time is kept; updated_at only moves forward.
     */
    private static final String UPSERT_LESSON_PROGRESS = """
            INSERT INTO lesson_progress
                (id, child_id, section_id, topic_id, lesson_id, current_game_index, completed, completed_at, updated_at)
            VALUES
                (gen_random_uuid(), :childId, :sectionId, :topicId, :lessonId, :currentGameIndex, :completed,
                 :completedAt, :updatedAt)
            ON CONFLICT (child_id, section_id, topic_id, lesson_id) DO UPDATE SET
                current_game_index = CASE
                    WHEN lesson_progress.completed AND NOT EXCLUDED.completed THEN lesson_progress.current_game_index
                    WHEN EXCLUDED.completed AND NOT lesson_progress.completed THEN EXCLUDED.current_game_index
                    ELSE GREATEST(lesson_progress.current_game_index, EXCLUDED.current_game_index)
                END,
                completed = lesson_progress.completed OR EXCLUDED.completed,
                completed_at = CASE
                    WHEN lesson_progress.completed THEN lesson_progress.completed_at
                    ELSE EXCLUDED.completed_at
                END,
                updated_at = GREATEST(lesson_progress.updated_at, EXCLUDED.updated_at)
            """;

    private static final String INSERT_GAME_RESULT = """
            INSERT INTO game_results
                (id, child_id, section_id, topic_id, lesson_id, game_index, game_type, score, attempts, mistakes,
                 duration_seconds, completed, played_at, received_at)
            VALUES
                (:id, :childId, :sectionId, :topicId, :lessonId, :gameIndex, :gameType, :score, :attempts, :mistakes,
                 :durationSeconds, :completed, :playedAt, :receivedAt)
            ON CONFLICT (id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public void upsertLessonProgress(List<LessonProgressRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("childId", row.childId())
                        .addValue("sectionId", row.sectionId())
                        .addValue("topicId", row.topicId())
                        .addValue("lessonId", row.lessonId())
                        .addValue("currentGameIndex", row.currentGameIndex())
                        .addValue("completed", row.completed())
                        .addValue("completedAt", utc(row.completedAt()))
                        .addValue("updatedAt", utc(row.updatedAt())))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate(UPSERT_LESSON_PROGRESS, batch);
    }

    /**
     * Inserts results that are not already stored.
     *
     * @return number of rows actually inserted; the rest were duplicates of earlier uploads
     */
    public int insertGameResults(List<GameResultRow> rows, Instant receivedAt) {
        if (rows.isEmpty()) {
            return 0;
        }
        SqlParameterSource[] batch = rows.stream()
                .map(row -> new MapSqlParameterSource()
                        .addValue("id", row.id())
                        .addValue("childId", row.childId())
                        .addValue("sectionId", row.sectionId())
                        .addValue("topicId", row.topicId())
                        .addValue("lessonId", row.lessonId())
                        .addValue("gameIndex", row.gameIndex())
                        .addValue("gameType", row.gameType())
                        .addValue("score", row.score())
                        .addValue("attempts", row.attempts())
                        .addValue("mistakes", row.mistakes())
                        .addValue("durationSeconds", row.durationSeconds())
                        .addValue("completed", row.completed())
                        .addValue("playedAt", utc(row.playedAt()))
                        .addValue("receivedAt", utc(receivedAt)))
                .toArray(SqlParameterSource[]::new);
        int inserted = 0;
        for (int count : jdbc.batchUpdate(INSERT_GAME_RESULT, batch)) {
            inserted += Math.max(count, 0);
        }
        return inserted;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public record LessonProgressRow(UUID childId, String sectionId, String topicId, String lessonId,
                                    int currentGameIndex, boolean completed, Instant completedAt,
                                    Instant updatedAt) {
    }

    public record GameResultRow(UUID id, UUID childId, String sectionId, String topicId, String lessonId,
                                int gameIndex, String gameType, int score, int attempts, int mistakes,
                                int durationSeconds, boolean completed, Instant playedAt) {
    }
}
