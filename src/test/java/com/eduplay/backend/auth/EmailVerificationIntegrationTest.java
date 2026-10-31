package com.eduplay.backend.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EmailVerificationIntegrationTest extends IntegrationTest {

    @Test
    void verificationTokenMarksEmailVerified() throws Exception {
        String email = uniqueEmail();
        Session session = registerParent(email);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerification(eq(email), anyString(), token.capture(), any(Duration.class));

        postJson("/api/v1/auth/verify-email", Map.of("token", token.getValue())).andExpect(status().isOk());
        getAuthorized("/api/v1/me", session.accessToken()).andExpect(jsonPath("$.emailVerified").value(true));

        postJson("/api/v1/auth/verify-email", Map.of("token", token.getValue()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
        postJson("/api/v1/auth/verify-email/resend", Map.of(), session.accessToken())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Your email address is already verified."));
    }

    @Test
    void invalidVerificationTokenIsRejected() throws Exception {
        postJson("/api/v1/auth/verify-email", Map.of("token", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }
}
