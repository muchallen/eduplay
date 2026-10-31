package com.eduplay.backend.report.repository;

import com.eduplay.backend.report.dto.ChildReportResponse.RecentGame;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Aggregate queries for the child dashboard. Every query is scoped to one child id, which the
 * service has already verified the caller may access, and optionally to one grade. Aggregation runs in
 * PostgreSQL (served by the (child_id, played_at) index) rather than loading rows into memory.
 */
@Repository
@RequiredArgsConstructor
public class ReportRepository {

    /** Optional grade filter: a null :gradeId matches every grade. */
    private static final String GRADE_FILTER = "(CAST(:gradeId AS VARCHAR) IS NULL OR grade_id = :gradeId)";

    /** Lower bound used when no reporting window is requested. */
    private static final OffsetDateTime BEGINNING = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private final NamedParameterJdbcTemplate jdbc;

    public GameTotals gameTotals(UUID childId, String gradeId, Instant since) {
        return jdbc.queryForObject("""
                SELECT count(*) AS games_played,
                       count(*) FILTER (WHERE completed) AS games_completed,
                       coalesce(sum(duration_seconds), 0) AS total_seconds,
                       avg(score) AS average_score,
                       max(played_at) AS last_played_at
                FROM game_results
                WHERE child_id = :childId AND played_at >= :since AND """ + GRADE_FILTER, params(childId, gradeId, since),
                (rs, i) -> new GameTotals(
                rs.getLong("games_played"),
                rs.getLong("games_completed"),
                rs.getLong("total_seconds"),
                nullableDouble(rs, "average_score"),
                instant(rs, "last_played_at")));
    }

    public long lessonsCompleted(UUID childId, String gradeId, Instant since) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM lesson_progress
                WHERE child_id = :childId AND completed AND completed_at >= :since AND """ + GRADE_FILTER,
                params(childId, gradeId, since), Long.class);
        return count == null ? 0 : count;
    }

    public List<TopicGameStats> topicGameStats(UUID childId, String gradeId, Instant since) {
        return jdbc.query("""
                SELECT grade_id, section_id, topic_id,
                       count(*) AS games_played,
                       avg(score) AS average_score,
                       avg(mistakes) AS average_mistakes,
                       coalesce(sum(duration_seconds), 0) AS total_seconds,
                       max(played_at) AS last_played_at
                FROM game_results
                WHERE child_id = :childId AND played_at >= :since AND """ + GRADE_FILTER + """

                GROUP BY grade_id, section_id, topic_id
                ORDER BY grade_id, section_id, topic_id
                """, params(childId, gradeId, since), (rs, i) -> new TopicGameStats(
                rs.getString("grade_id"),
                rs.getString("section_id"),
                rs.getString("topic_id"),
                rs.getLong("games_played"),
                nullableDouble(rs, "average_score"),
                nullableDouble(rs, "average_mistakes"),
                rs.getLong("total_seconds"),
                instant(rs, "last_played_at")));
    }

    public List<TopicLessonStats> topicLessonStats(UUID childId, String gradeId) {
        return jdbc.query("""
                SELECT grade_id, section_id, topic_id,
                       count(*) FILTER (WHERE completed) AS lessons_completed,
                       count(*) AS lessons_started
                FROM lesson_progress
                WHERE child_id = :childId AND """ + GRADE_FILTER + """

                GROUP BY grade_id, section_id, topic_id
                """, params(childId, gradeId, null), (rs, i) -> new TopicLessonStats(
                rs.getString("grade_id"),
                rs.getString("section_id"),
                rs.getString("topic_id"),
                rs.getLong("lessons_completed"),
                rs.getLong("lessons_started")));
    }

    public int activeDays(UUID childId, String gradeId, Instant since) {
        Integer days = jdbc.queryForObject("""
                SELECT count(DISTINCT (played_at AT TIME ZONE 'UTC')::date)
                FROM game_results
                WHERE child_id = :childId AND played_at >= :since AND """ + GRADE_FILTER,
                params(childId, gradeId, since), Integer.class);
        return days == null ? 0 : days;
    }

    public List<RecentGame> recentGames(UUID childId, String gradeId, int limit) {
        return jdbc.query("""
                SELECT grade_id, section_id, topic_id, lesson_id, game_index, game_type, score, mistakes,
                       duration_seconds, completed, played_at
                FROM game_results
                WHERE child_id = :childId AND """ + GRADE_FILTER + """

                ORDER BY played_at DESC, id
                LIMIT :limit
                """, params(childId, gradeId, null).addValue("limit", limit), (rs, i) -> new RecentGame(
                rs.getString("grade_id"),
                rs.getString("section_id"),
                rs.getString("topic_id"),
                rs.getString("lesson_id"),
                rs.getInt("game_index"),
                rs.getString("game_type"),
                rs.getInt("score"),
                rs.getInt("mistakes"),
                rs.getInt("duration_seconds"),
                rs.getBoolean("completed"),
                instant(rs, "played_at")));
    }

    private static MapSqlParameterSource params(UUID childId, String gradeId, Instant since) {
        return new MapSqlParameterSource("childId", childId)
                .addValue("gradeId", gradeId, Types.VARCHAR)
                .addValue("since", since == null ? BEGINNING : since.atOffset(ZoneOffset.UTC));
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record GameTotals(long gamesPlayed, long gamesCompleted, long totalSeconds, Double averageScore,
                             Instant lastPlayedAt) {
    }

    public record TopicGameStats(String gradeId, String sectionId, String topicId, long gamesPlayed, Double averageScore,
                                 Double averageMistakes, long totalSeconds, Instant lastPlayedAt) {
    }

    public record TopicLessonStats(String gradeId, String sectionId, String topicId, long lessonsCompleted, long lessonsStarted) {
    }
}
