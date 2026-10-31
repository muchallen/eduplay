package com.eduplay.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.infrastructure.DataRetentionJob;
import com.eduplay.backend.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class AccountIntegrationTest extends IntegrationTest {

    @Autowired
    private DataRetentionJob dataRetentionJob;

    @Test
    void profileCanBeUpdatedPartially() throws Exception {
        Session parent = registerParent();
        mockMvc.perform(authorized(patch("/api/v1/me"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fullName", "Chipo Moyo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Chipo Moyo"))
                .andExpect(jsonPath("$.phone").value("+263 77 123 4567"));
        mockMvc.perform(authorized(patch("/api/v1/me"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("phone", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist());
        mockMvc.perform(authorized(patch("/api/v1/me"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("phone", "call me"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exportContainsAccountChildrenAndProgress() throws Exception {
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "Kuda");
        postJson("/api/v1/children/" + childId + "/sync", Map.of("gameResults",
                List.of(Map.ofEntries(
                        Map.entry("id", UUID.randomUUID().toString()), Map.entry("gradeId", "ecd_a"),
                        Map.entry("sectionId", "number"), Map.entry("topicId", "counting"), Map.entry("lessonId", "level1"),
                        Map.entry("gameIndex", 0), Map.entry("gameType", "MatchingGame"), Map.entry("score", 5),
                        Map.entry("attempts", 1), Map.entry("mistakes", 0), Map.entry("durationSeconds", 10),
                        Map.entry("completed", true), Map.entry("playedAt", clock.instant().minusSeconds(60).toString())))),
                parent.accessToken()).andExpect(status().isOk());

        getAuthorized("/api/v1/me/export", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value(parent.email()))
                .andExpect(jsonPath("$.consents[0].policyVersion").value(POLICY_VERSION))
                .andExpect(jsonPath("$.subscription.status").value("TRIAL"))
                .andExpect(jsonPath("$.children[0].profile.displayName").value("Kuda"))
                .andExpect(jsonPath("$.children[0].gameResults.length()").value(1));
    }

    @Test
    void deletingAccountRequiresPasswordAndSignsOutEverywhere() throws Exception {
        Session parent = registerParent();
        mockMvc.perform(authorized(delete("/api/v1/me"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(authorized(delete("/api/v1/me"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.permanentDeletionAfter").isNotEmpty());

        postJson("/api/v1/auth/login", Map.of("email", parent.email(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", parent.refreshToken()))
                .andExpect(status().isUnauthorized());
        getAuthorized("/api/v1/me", parent.accessToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void deletedAccountsAreHardDeletedAfterRetentionPeriod() throws Exception {
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "Tino");
        postJson("/api/v1/children/" + childId + "/sync", Map.of("lessonProgress", List.of(Map.of(
                "gradeId", "ecd_a", "sectionId", "number", "topicId", "counting", "lessonId", "level1",
                "currentGameIndex", 1, "completed", false))), parent.accessToken()).andExpect(status().isOk());
        mockMvc.perform(authorized(delete("/api/v1/me"), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("password", PASSWORD))))
                .andExpect(status().isAccepted());

        // Email can be reused straight away by a new registration.
        registerParent(parent.email());

        clock.advance(Duration.ofDays(29));
        dataRetentionJob.purge();
        assertThat(count("select count(*) from users where id = ?", parent.userId())).isEqualTo(1);

        clock.advance(Duration.ofDays(2));
        dataRetentionJob.purge();
        assertThat(count("select count(*) from users where id = ?", parent.userId())).isZero();
        assertThat(count("select count(*) from children where id = ?", childId)).isZero();
        assertThat(count("select count(*) from lesson_progress where child_id = ?", childId)).isZero();
        assertThat(count("select count(*) from consent_records where user_id = ?", parent.userId())).isZero();
    }

    @Test
    void deletedChildrenArePurgedAfterRetentionPeriod() throws Exception {
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "Temp");
        mockMvc.perform(authorized(delete("/api/v1/children/" + childId), parent.accessToken()))
                .andExpect(status().isNoContent());
        clock.advance(Duration.ofDays(31));
        dataRetentionJob.purge();
        assertThat(count("select count(*) from children where id = ?", childId)).isZero();
    }

    @Test
    void consentToNewPolicyIsRecorded() throws Exception {
        Session parent = registerParent();
        postJson("/api/v1/me/consent", Map.of("policyVersion", POLICY_VERSION), parent.accessToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyVersion").value(POLICY_VERSION));
        assertThat(count("select count(*) from consent_records where user_id = ?", parent.userId())).isEqualTo(2);
    }

    private int count(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arg);
    }
}
