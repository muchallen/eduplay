package com.eduplay.backend.subscription;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.subscription.service.SubscriptionService;
import com.eduplay.backend.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SubscriptionIntegrationTest extends IntegrationTest {

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void newParentStartsOnActiveTrial() throws Exception {
        Session parent = registerParent();
        getAuthorized("/api/v1/subscription", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"))
                .andExpect(jsonPath("$.plan").value("TRIAL"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.daysRemaining").value(14));
    }

    @Test
    void trialExpiresAfterTrialPeriod() throws Exception {
        Session parent = registerParent();
        clock.advance(Duration.ofDays(15));
        JsonTokens tokens = refresh(parent);
        getAuthorized("/api/v1/subscription", tokens.accessToken())
                .andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void scheduledJobExpiresLapsedSubscriptions() throws Exception {
        Session parent = registerParent();
        clock.advance(Duration.ofDays(15));
        subscriptionService.expireLapsedSubscriptions();
        String status = jdbcTemplate.queryForObject("select status from subscriptions where user_id = ?",
                String.class, parent.userId());
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("EXPIRED");
    }

    @Test
    void onlyAdminsCanChangeSubscriptions() throws Exception {
        Session parent = registerParent();
        Session admin = registerParent();
        jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", admin.userId());
        String adminToken = login(admin.email(), PASSWORD).accessToken();

        Instant start = clock.instant();
        Map<String, Object> body = Map.of(
                "plan", "FAMILY_MONTHLY",
                "status", "ACTIVE",
                "startsAt", start.toString(),
                "endsAt", start.plus(Duration.ofDays(30)).toString(),
                "provider", "ECOCASH",
                "providerReference", "TX-12345");
        String url = "/api/v1/admin/users/" + parent.userId() + "/subscription";

        mockMvc.perform(authorized(put(url), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(authorized(put(url), adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.plan").value("FAMILY_MONTHLY"));

        getAuthorized("/api/v1/subscription", parent.accessToken())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.provider").value("ECOCASH"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void adminUpdateValidatesPeriod() throws Exception {
        Session parent = registerParent();
        Session admin = registerParent();
        jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", admin.userId());
        String adminToken = login(admin.email(), PASSWORD).accessToken();
        Instant start = clock.instant();
        mockMvc.perform(authorized(put("/api/v1/admin/users/" + parent.userId() + "/subscription"), adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("plan", "X", "status", "ACTIVE", "startsAt", start.toString(),
                                "endsAt", start.minusSeconds(1).toString(), "provider", "MANUAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    private JsonTokens refresh(Session session) throws Exception {
        var body = read(postJson("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()))
                .andExpect(status().isOk()));
        return new JsonTokens(body.get("accessToken").asText());
    }

    private record JsonTokens(String accessToken) {
    }
}
