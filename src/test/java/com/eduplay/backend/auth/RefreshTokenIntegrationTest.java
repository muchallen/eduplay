package com.eduplay.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RefreshTokenIntegrationTest extends IntegrationTest {

    @Test
    void refreshRotatesTokensAndRevokesThePreviousOne() throws Exception {
        Session session = registerParent();

        JsonNode rotated = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()));
        String newRefresh = rotated.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(session.refreshToken());

        getAuthorized("/api/v1/me", rotated.get("accessToken").asText()).andExpect(status().isOk());
        assertThat(revokedCount(session)).isEqualTo(1);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", newRefresh)).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedTokenRevokesEverySession() throws Exception {
        Session phone = registerParent();
        Session tablet = login(phone.email(), PASSWORD);

        JsonNode rotated = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isOk()));

        // An attacker (or a replay) presents the old token again after the retry grace period.
        clock.advance(Duration.ofSeconds(61));
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", rotated.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", tablet.refreshToken()))
                .andExpect(status().isUnauthorized());
        assertThat(activeCount(phone)).isZero();
    }

    @Test
    void retryWithinGracePeriodReturnsTheSameReplacement() throws Exception {
        Session phone = registerParent();
        Session tablet = login(phone.email(), PASSWORD);

        JsonNode first = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isOk()));
        // The response was lost on a weak connection; the app retries with the old token.
        clock.advance(Duration.ofSeconds(30));
        JsonNode retry = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()));

        String replacement = first.get("refreshToken").asText();
        assertThat(retry.get("refreshToken").asText()).isEqualTo(replacement);
        assertThat(retry.get("refreshTokenExpiresAt").asText()).isEqualTo(first.get("refreshTokenExpiresAt").asText());
        assertThat(activeCount(phone)).isEqualTo(2);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", replacement)).andExpect(status().isOk());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", tablet.refreshToken())).andExpect(status().isOk());
    }

    @Test
    void retryAfterTheReplacementWasUsedIsTreatedAsReuse() throws Exception {
        Session phone = registerParent();
        JsonNode first = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isOk()));
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", first.get("refreshToken").asText()))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
        assertThat(activeCount(phone)).isZero();
    }

    @Test
    void loggedOutTokenGetsNoGracePeriod() throws Exception {
        Session phone = registerParent();
        postJson("/api/v1/auth/logout", Map.of("refreshToken", phone.refreshToken())).andExpect(status().isNoContent());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", phone.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void multipleDevicesHaveIndependentSessions() throws Exception {
        Session phone = registerParent();
        Session tablet = login(phone.email(), PASSWORD);
        assertThat(activeCount(phone)).isEqualTo(2);

        postJson("/api/v1/auth/logout", Map.of("refreshToken", phone.refreshToken())).andExpect(status().isNoContent());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", tablet.refreshToken())).andExpect(status().isOk());
    }

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        Session session = registerParent();
        clock.advance(Duration.ofDays(31));
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void unknownRefreshTokenIsRejected() throws Exception {
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", "made-up-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() throws Exception {
        Session session = registerParent();
        Integer plain = jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where token_hash = ?", Integer.class, session.refreshToken());
        assertThat(plain).isZero();
    }

    @Test
    void logoutAllRevokesEveryDevice() throws Exception {
        Session phone = registerParent();
        login(phone.email(), PASSWORD);
        postJson("/api/v1/auth/logout-all", Map.of(), phone.accessToken()).andExpect(status().isNoContent());
        assertThat(activeCount(phone)).isZero();
    }

    private int activeCount(Session session) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is null",
                Integer.class, session.userId());
    }

    private int revokedCount(Session session) {
        return jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where user_id = ? and revoked_at is not null",
                Integer.class, session.userId());
    }
}
