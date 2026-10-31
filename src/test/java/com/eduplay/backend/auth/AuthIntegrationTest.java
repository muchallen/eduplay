package com.eduplay.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void registerCreatesParentWithConsentTrialAndVerificationEmail() throws Exception {
        String email = uniqueEmail();
        JsonNode body = read(postJson("/api/v1/auth/register", registration(email.toUpperCase()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.role").value("PARENT"))
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()));

        String userId = body.at("/user/id").asText();
        String storedHash = jdbcTemplate.queryForObject(
                "select password_hash from users where id = ?::uuid", String.class, userId);
        assertThat(storedHash).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(jdbcTemplate.queryForObject(
                "select policy_version from consent_records where user_id = ?::uuid", String.class, userId))
                .isEqualTo(POLICY_VERSION);
        assertThat(jdbcTemplate.queryForObject(
                "select status from subscriptions where user_id = ?::uuid", String.class, userId))
                .isEqualTo("TRIAL");
        verify(emailService).sendEmailVerification(eq(email), eq("Test Parent"), anyString(), any(Duration.class));
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail();
        registerParent(email);
        postJson("/api/v1/auth/register", registration(email.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesPasswordLengthEmailAndConsent() throws Exception {
        Map<String, Object> body = registration("not-an-email");
        body.put("password", "short");
        body.put("parentalConsent", false);
        JsonNode error = read(postJson("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED")));
        assertThat(error.get("fieldErrors").findValuesAsText("field"))
                .contains("email", "password", "parentalConsent");
    }

    @Test
    void registerRequiresCurrentPolicyVersion() throws Exception {
        Map<String, Object> body = registration(uniqueEmail());
        body.put("policyVersion", "2019-01");
        postJson("/api/v1/auth/register", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POLICY_VERSION_MISMATCH"));
    }

    @Test
    void malformedJsonReturnsConsistentError() throws Exception {
        postJson("/api/v1/auth/register", "{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void loginSucceedsWithCaseInsensitiveEmail() throws Exception {
        String email = uniqueEmail();
        registerParent(email);
        postJson("/api/v1/auth/login", Map.of("email", email.toUpperCase(), "password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void loginFailuresAreGenericForWrongPasswordAndUnknownEmail() throws Exception {
        String email = uniqueEmail();
        registerParent(email);
        JsonNode wrongPassword = read(postJson("/api/v1/auth/login", Map.of("email", email, "password", "wrong-pass-1"))
                .andExpect(status().isUnauthorized()));
        JsonNode unknown = read(postJson("/api/v1/auth/login", Map.of("email", uniqueEmail(), "password", PASSWORD))
                .andExpect(status().isUnauthorized()));
        assertThat(wrongPassword.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.get("message")).isEqualTo(unknown.get("message"));
    }

    @Test
    void protectedEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void validAccessTokenGrantsAccess() throws Exception {
        Session session = registerParent();
        getAuthorized("/api/v1/me", session.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.userId().toString()))
                .andExpect(jsonPath("$.email").value(session.email()));
    }

    @Test
    void expiredAccessTokenIsRejectedWithTokenExpiredCode() throws Exception {
        Session session = registerParent();
        clock.advance(Duration.ofMinutes(16));
        getAuthorized("/api/v1/me", session.accessToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        Session session = registerParent();
        String token = session.accessToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        getAuthorized("/api/v1/me", tampered)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        getAuthorized("/api/v1/me", "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        Session session = registerParent();
        postJson("/api/v1/auth/logout", Map.of("refreshToken", session.refreshToken()))
                .andExpect(status().isNoContent());
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        Session session = registerParent();
        getAuthorized("/api/v1/me", session.accessToken())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void errorResponsesDoNotLeakInternals() throws Exception {
        Session session = registerParent();
        getAuthorized("/api/v1/children/not-a-uuid", session.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }
}
