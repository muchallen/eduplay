package com.eduplay.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasswordResetIntegrationTest extends IntegrationTest {

    private static final String NEW_PASSWORD = "Brand-New-Passw0rd";

    @Test
    void responseIsIdenticalForKnownAndUnknownEmails() throws Exception {
        String known = uniqueEmail();
        registerParent(known);

        JsonNode knownResponse = read(postJson("/api/v1/auth/password-reset/request", Map.of("email", known))
                .andExpect(status().isAccepted()));
        String unknown = uniqueEmail();
        JsonNode unknownResponse = read(postJson("/api/v1/auth/password-reset/request", Map.of("email", unknown))
                .andExpect(status().isAccepted()));

        assertThat(knownResponse).isEqualTo(unknownResponse);
        verify(emailService, never()).sendPasswordReset(eq(unknown), anyString(), anyString(), any());
    }

    @Test
    void confirmResetChangesPasswordAndSignsOutAllDevices() throws Exception {
        Session session = registerParent();
        String token = requestResetToken(session.email());

        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk());

        postJson("/api/v1/auth/login", Map.of("email", session.email(), "password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login(session.email(), NEW_PASSWORD);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", session.refreshToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetTokenIsSingleUse() throws Exception {
        Session session = registerParent();
        String token = requestResetToken(session.email());
        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk());
        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", token, "newPassword", "Another-Passw0rd"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void expiredResetTokenIsRejected() throws Exception {
        Session session = registerParent();
        String token = requestResetToken(session.email());
        clock.advance(Duration.ofMinutes(31));
        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", token, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }

    @Test
    void newRequestInvalidatesEarlierLinks() throws Exception {
        Session session = registerParent();
        String first = requestResetToken(session.email());
        String second = requestResetToken(session.email());
        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", first, "newPassword", NEW_PASSWORD))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", second, "newPassword", NEW_PASSWORD))
                .andExpect(status().isOk());
    }

    @Test
    void newPasswordMustMeetMinimumLength() throws Exception {
        Session session = registerParent();
        String token = requestResetToken(session.email());
        postJson("/api/v1/auth/password-reset/confirm", Map.of("token", token, "newPassword", "1234567"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    private String requestResetToken(String email) throws Exception {
        org.mockito.Mockito.clearInvocations(emailService);
        postJson("/api/v1/auth/password-reset/request", Map.of("email", email)).andExpect(status().isAccepted());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(eq(email), anyString(), token.capture(), any(Duration.class));
        return token.getValue();
    }
}
