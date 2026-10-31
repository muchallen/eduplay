package com.eduplay.backend.progress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;

class SyncIntegrationTest extends IntegrationTest {

    private Session parent;
    private UUID childId;

    @BeforeEach
    void setUp() throws Exception {
        parent = registerParent();
        childId = createChild(parent.accessToken(), "Nyasha");
    }

    @Test
    void syncStoresProgressAndResultsAndReturnsMergedState() throws Exception {
        Instant playedAt = clock.instant().minus(Duration.ofMinutes(5));
        Map<String, Object> body = Map.of(
                "lessonProgress", List.of(progress("number", "counting", "level1", 3, false, playedAt)),
                "gameResults", List.of(result(UUID.randomUUID(), "counting", 8, playedAt),
                        result(UUID.randomUUID(), "counting", 9, playedAt)));

        sync(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.childId").value(childId.toString()))
                .andExpect(jsonPath("$.serverTime").isNotEmpty())
                .andExpect(jsonPath("$.acceptedGameResults").value(2))
                .andExpect(jsonPath("$.duplicateGameResults").value(0))
                .andExpect(jsonPath("$.lessonProgress.length()").value(1))
                .andExpect(jsonPath("$.lessonProgress[0].lessonId").value("level1"))
                .andExpect(jsonPath("$.lessonProgress[0].currentGameIndex").value(3))
                .andExpect(jsonPath("$.lessonProgress[0].completed").value(false));

        getAuthorized("/api/v1/children/" + childId + "/progress", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonProgress[0].currentGameIndex").value(3));
        getAuthorized("/api/v1/children/" + childId + "/game-results?size=1", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void duplicateGameResultsAreIgnoredAndRetriesAreSafe() throws Exception {
        UUID id = UUID.randomUUID();
        Instant playedAt = clock.instant().minus(Duration.ofMinutes(1));
        Map<String, Object> body = Map.of(
                "lessonProgress", List.of(progress("number", "counting", "level1", 2, false, playedAt)),
                "gameResults", List.of(result(id, "counting", 7, playedAt)));

        sync(body).andExpect(jsonPath("$.acceptedGameResults").value(1));
        sync(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedGameResults").value(0))
                .andExpect(jsonPath("$.duplicateGameResults").value(1))
                .andExpect(jsonPath("$.lessonProgress[0].currentGameIndex").value(2));

        // Same id twice inside one batch.
        UUID another = UUID.randomUUID();
        sync(Map.of("gameResults", List.of(result(another, "counting", 5, playedAt),
                result(another, "counting", 5, playedAt))))
                .andExpect(jsonPath("$.acceptedGameResults").value(1))
                .andExpect(jsonPath("$.duplicateGameResults").value(1));

        assertThat(resultCount()).isEqualTo(2);
    }

    @Test
    void duplicateIdBelongingToAnotherChildIsIgnoredWithoutLeakingData() throws Exception {
        UUID sharedId = UUID.randomUUID();
        Instant playedAt = clock.instant().minus(Duration.ofMinutes(1));
        sync(Map.of("gameResults", List.of(result(sharedId, "counting", 3, playedAt))));

        Session otherParent = registerParent();
        UUID otherChild = createChild(otherParent.accessToken(), "Other");
        postJson("/api/v1/children/" + otherChild + "/sync",
                Map.of("gameResults", List.of(result(sharedId, "counting", 10, playedAt))), otherParent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateGameResults").value(1));

        Integer score = jdbcTemplate.queryForObject("select score from game_results where id = ?", Integer.class, sharedId);
        assertThat(score).isEqualTo(3);
    }

    @Test
    void higherGameIndexWinsWhenBothIncomplete() throws Exception {
        Instant t = clock.instant().minus(Duration.ofHours(1));
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level1", 4, false, t))));
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level1", 2, false, t.plusSeconds(60)))))
                .andExpect(jsonPath("$.lessonProgress[0].currentGameIndex").value(4))
                .andExpect(jsonPath("$.lessonProgress[0].completed").value(false));
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level1", 5, false, t))))
                .andExpect(jsonPath("$.lessonProgress[0].currentGameIndex").value(5));
    }

    @Test
    void completedLessonAlwaysTakesPrecedence() throws Exception {
        Instant t = clock.instant().minus(Duration.ofHours(1));
        JsonNode first = read(sync(Map.of("lessonProgress",
                List.of(progress("number", "counting", "level1", 5, true, t)))));
        String completedAt = first.at("/lessonProgress/0/completedAt").asText();
        assertThat(completedAt).isNotEmpty();

        // A second device that was offline still has the lesson in progress at a higher index.
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level1", 7, false, t.plusSeconds(600)))))
                .andExpect(jsonPath("$.lessonProgress[0].completed").value(true))
                .andExpect(jsonPath("$.lessonProgress[0].currentGameIndex").value(5))
                .andExpect(jsonPath("$.lessonProgress[0].completedAt").value(completedAt));

        // Incomplete on the server, completed on the device: completion wins even with a lower index.
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level2", 6, false, t))));
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level2", 4, true, t.plusSeconds(60)))))
                .andExpect(jsonPath("$.lessonProgress[1].lessonId").value("level2"))
                .andExpect(jsonPath("$.lessonProgress[1].completed").value(true))
                .andExpect(jsonPath("$.lessonProgress[1].currentGameIndex").value(4));
    }

    @Test
    void futureTimestampsFromDriftingDeviceClocksAreClamped() throws Exception {
        Instant future = clock.instant().plus(Duration.ofDays(3));
        UUID id = UUID.randomUUID();
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level1", 1, false, future)),
                "gameResults", List.of(result(id, "counting", 4, future))))
                .andExpect(status().isOk());
        Instant stored = jdbcTemplate.queryForObject("select played_at from game_results where id = ?",
                java.sql.Timestamp.class, id).toInstant();
        assertThat(stored).isBeforeOrEqualTo(clock.instant());
    }

    @Test
    void sameLessonInTwoGradesIsTrackedSeparately() throws Exception {
        Instant t = clock.instant().minus(Duration.ofHours(1));
        Map<String, Object> gradeB = progress("number", "counting", "level1", 1, false, t);
        gradeB.put("gradeId", "ecd_b");
        sync(Map.of("lessonProgress", List.of(progress("number", "counting", "level1", 5, true, t), gradeB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonProgress.length()").value(2))
                .andExpect(jsonPath("$.lessonProgress[0].gradeId").value("ecd_a"))
                .andExpect(jsonPath("$.lessonProgress[0].completed").value(true))
                .andExpect(jsonPath("$.lessonProgress[1].gradeId").value("ecd_b"))
                .andExpect(jsonPath("$.lessonProgress[1].currentGameIndex").value(1))
                .andExpect(jsonPath("$.lessonProgress[1].completed").value(false));
    }

    @Test
    void gameResultsKeepTheGradeTheyWerePlayedIn() throws Exception {
        UUID id = UUID.randomUUID();
        Map<String, Object> played = result(id, "counting", 70, clock.instant().minusSeconds(60));
        played.put("gradeId", "ecd_b");
        sync(Map.of("gameResults", List.of(played))).andExpect(status().isOk());
        getAuthorized("/api/v1/children/" + childId + "/game-results", parent.accessToken())
                .andExpect(jsonPath("$.items[0].gradeId").value("ecd_b"));
    }

    @Test
    void gradeAndPercentScoreAreValidated() throws Exception {
        Instant t = clock.instant().minus(Duration.ofMinutes(1));
        Map<String, Object> noGrade = result(UUID.randomUUID(), "counting", 50, t);
        noGrade.remove("gradeId");
        sync(Map.of("gameResults", List.of(noGrade)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gameResults[0].gradeId"));

        Map<String, Object> unknownGrade = result(UUID.randomUUID(), "counting", 50, t);
        unknownGrade.put("gradeId", "grade_7");
        sync(Map.of("gameResults", List.of(unknownGrade)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRADE"));

        sync(Map.of("gameResults", List.of(result(UUID.randomUUID(), "counting", 101, t))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gameResults[0].score"));
        assertThat(resultCount()).isZero();
    }

    @Test
    void syncIsAcceptedAfterTheSubscriptionHasEnded() throws Exception {
        jdbcTemplate.update("update subscriptions set status = 'EXPIRED', ends_at = now() - interval '1 day' "
                + "where user_id = ?", parent.userId());
        sync(Map.of("gameResults", List.of(result(UUID.randomUUID(), "counting", 80, clock.instant().minusSeconds(5)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedGameResults").value(1));
    }

    @Test
    void batchesLargerThan500ResultsAreRejected() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Instant t = clock.instant().minus(Duration.ofMinutes(1));
        for (int i = 0; i < 501; i++) {
            results.add(result(UUID.randomUUID(), "counting", 1, t));
        }
        sync(Map.of("gameResults", results))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(resultCount()).isZero();
    }

    @Test
    void accepts500ResultsInOneRequest() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        Instant t = clock.instant().minus(Duration.ofMinutes(1));
        for (int i = 0; i < 500; i++) {
            results.add(result(UUID.randomUUID(), "counting", i % 101, t.minusSeconds(i)));
        }
        sync(Map.of("gameResults", results))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedGameResults").value(500));
    }

    @Test
    void invalidItemRejectsWholeBatchAtomically() throws Exception {
        Instant t = clock.instant().minus(Duration.ofMinutes(1));
        Map<String, Object> bad = result(UUID.randomUUID(), "counting", -1, t);
        sync(Map.of("gameResults", List.of(result(UUID.randomUUID(), "counting", 3, t), bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gameResults[1].score"));
        assertThat(resultCount()).isZero();
    }

    @Test
    void gzipCompressedBodiesAreAccepted() throws Exception {
        Instant t = clock.instant().minus(Duration.ofMinutes(1));
        String payload = json(Map.of("gameResults", List.of(result(UUID.randomUUID(), "counting", 6, t))));
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        mockMvc.perform(authorized(post("/api/v1/children/" + childId + "/sync"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Content-Encoding", "gzip")
                        .content(compressed.toByteArray()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedGameResults").value(1));
    }

    @Test
    void syncRequiresAuthenticationAndAllowsRetryAfterRefresh() throws Exception {
        Instant t = clock.instant().minus(Duration.ofMinutes(1));
        Map<String, Object> body = Map.of("gameResults", List.of(result(UUID.randomUUID(), "counting", 6, t)));
        clock.advance(Duration.ofMinutes(20));
        sync(body)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));

        JsonNode refreshed = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", parent.refreshToken()))
                .andExpect(status().isOk()));
        postJson("/api/v1/children/" + childId + "/sync", body, refreshed.get("accessToken").asText())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedGameResults").value(1));
    }

    @Test
    void gameResultsAreAppendOnlyInTheDatabase() throws Exception {
        UUID id = UUID.randomUUID();
        sync(Map.of("gameResults", List.of(result(id, "counting", 6, clock.instant().minusSeconds(30)))));
        assertThatThrownBy(() -> jdbcTemplate.update("update game_results set score = 100 where id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private org.springframework.test.web.servlet.ResultActions sync(Object body) throws Exception {
        return postJson("/api/v1/children/" + childId + "/sync", body, parent.accessToken());
    }

    private int resultCount() {
        return jdbcTemplate.queryForObject("select count(*) from game_results where child_id = ?",
                Integer.class, childId);
    }

    static Map<String, Object> progress(String section, String topic, String lesson, int index, boolean completed,
                                        Instant updatedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("gradeId", "ecd_a");
        item.put("sectionId", section);
        item.put("topicId", topic);
        item.put("lessonId", lesson);
        item.put("currentGameIndex", index);
        item.put("completed", completed);
        item.put("updatedAt", updatedAt.toString());
        return item;
    }

    static Map<String, Object> result(UUID id, String topic, int score, Instant playedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id.toString());
        item.put("gradeId", "ecd_a");
        item.put("sectionId", "number");
        item.put("topicId", topic);
        item.put("lessonId", "level1");
        item.put("gameIndex", 2);
        item.put("gameType", "MatchingGame");
        item.put("score", score);
        item.put("attempts", 1);
        item.put("mistakes", 2);
        item.put("durationSeconds", 74);
        item.put("completed", true);
        item.put("playedAt", playedAt.toString());
        return item;
    }
}
