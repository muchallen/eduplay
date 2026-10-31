package com.eduplay.backend.report;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportIntegrationTest extends IntegrationTest {

    @Test
    void reportSummarisesPerformanceByTopic() throws Exception {
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "Farai");
        Instant now = clock.instant();

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(result("counting", 9, 60, now.minus(Duration.ofHours(i + 1))));
            results.add(result("addition", 3, 120, now.minus(Duration.ofHours(i + 1))));
        }
        results.add(result("shapes", 10, 30, now.minus(Duration.ofDays(40))));
        Map<String, Object> lesson = new LinkedHashMap<>();
        lesson.put("sectionId", "number");
        lesson.put("topicId", "counting");
        lesson.put("lessonId", "level1");
        lesson.put("currentGameIndex", 5);
        lesson.put("completed", true);
        lesson.put("updatedAt", now.minus(Duration.ofHours(1)).toString());

        postJson("/api/v1/children/" + childId + "/sync",
                Map.of("lessonProgress", List.of(lesson), "gameResults", results), parent.accessToken())
                .andExpect(status().isOk());

        getAuthorized("/api/v1/children/" + childId + "/report", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Farai"))
                .andExpect(jsonPath("$.totals.gamesPlayed").value(7))
                .andExpect(jsonPath("$.totals.lessonsCompleted").value(1))
                .andExpect(jsonPath("$.totals.totalTimeSeconds").value(570))
                .andExpect(jsonPath("$.totals.averageScore").value(6.57))
                .andExpect(jsonPath("$.topics.length()").value(3))
                .andExpect(jsonPath("$.strongTopics.length()").value(1))
                .andExpect(jsonPath("$.strongTopics[0].topicId").value("counting"))
                .andExpect(jsonPath("$.topicsNeedingImprovement.length()").value(1))
                .andExpect(jsonPath("$.topicsNeedingImprovement[0].topicId").value("addition"))
                .andExpect(jsonPath("$.recentActivity.recentGames.length()").value(7))
                .andExpect(jsonPath("$.recentActivity.activeDaysLast30").isNumber());

        getAuthorized("/api/v1/children/" + childId + "/report?days=7", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.gamesPlayed").value(6))
                .andExpect(jsonPath("$.periodStart").isNotEmpty());
    }

    @Test
    void reportForChildWithoutActivityIsEmpty() throws Exception {
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "New");
        getAuthorized("/api/v1/children/" + childId + "/report", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.gamesPlayed").value(0))
                .andExpect(jsonPath("$.totals.averageScore").doesNotExist())
                .andExpect(jsonPath("$.topics.length()").value(0));
    }

    private static Map<String, Object> result(String topic, int score, int duration, Instant playedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("sectionId", "number");
        item.put("topicId", topic);
        item.put("lessonId", "level1");
        item.put("gameIndex", 0);
        item.put("gameType", "MatchingGame");
        item.put("score", score);
        item.put("attempts", 1);
        item.put("mistakes", 10 - score);
        item.put("durationSeconds", duration);
        item.put("completed", true);
        item.put("playedAt", playedAt.toString());
        return item;
    }
}
