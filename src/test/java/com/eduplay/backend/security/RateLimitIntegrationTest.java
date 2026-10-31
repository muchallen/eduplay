package com.eduplay.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
        "eduplay.rate-limit.login-per-account.capacity=3",
        "eduplay.rate-limit.password-reset-per-account.capacity=2",
        "eduplay.rate-limit.register-per-ip.capacity=2"
})
class RateLimitIntegrationTest extends IntegrationTest {

    @Test
    void repeatedFailedLoginsAreRateLimited() throws Exception {
        String email = uniqueEmail();
        Map<String, String> body = Map.of("email", email, "password", "wrong-password");
        for (int i = 0; i < 3; i++) {
            postJson("/api/v1/auth/login", body).andExpect(status().isUnauthorized());
        }
        postJson("/api/v1/auth/login", body)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void passwordResetRequestsAreRateLimited() throws Exception {
        Map<String, String> body = Map.of("email", uniqueEmail());
        postJson("/api/v1/auth/password-reset/request", body).andExpect(status().isAccepted());
        postJson("/api/v1/auth/password-reset/request", body).andExpect(status().isAccepted());
        postJson("/api/v1/auth/password-reset/request", body).andExpect(status().isTooManyRequests());
    }

    @Test
    void registrationsPerIpAreRateLimited() throws Exception {
        for (int i = 0; i < 2; i++) {
            register("10.9.9.9").andExpect(status().isCreated());
        }
        register("10.9.9.9").andExpect(status().isTooManyRequests());
        register("10.9.9.10").andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions register(String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(registration(uniqueEmail()))));
    }
}
