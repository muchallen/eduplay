package com.eduplay.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.email.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base class for full-stack tests: real HTTP layer (MockMvc + Spring Security), real PostgreSQL
 * 16 (Testcontainers) with Flyway migrations, and a mocked email sender.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String POLICY_VERSION = "2026-01";
    public static final String PASSWORD = "Sup3r-Secret-Pass";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected EmailService emailService;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    protected static String uniqueEmail() {
        return "parent-" + UUID.randomUUID() + "@example.com";
    }

    protected ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions postJson(String url, Object body, String accessToken) throws Exception {
        return mockMvc.perform(authorized(post(url), accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions getAuthorized(String url, String accessToken) throws Exception {
        return mockMvc.perform(authorized(get(url), accessToken));
    }

    protected static MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected String json(Object body) throws Exception {
        return body instanceof String s ? s : objectMapper.writeValueAsString(body);
    }

    protected JsonNode read(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected Map<String, Object> registration(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("fullName", "Test Parent");
        body.put("phone", "+263 77 123 4567");
        body.put("policyVersion", POLICY_VERSION);
        body.put("parentalConsent", true);
        body.put("deviceLabel", "Pixel 7");
        return body;
    }

    /** Registers a new parent and returns the auth response. */
    protected Session registerParent() throws Exception {
        return registerParent(uniqueEmail());
    }

    protected Session registerParent(String email) throws Exception {
        JsonNode body = read(postJson("/api/v1/auth/register", registration(email)).andExpect(status().isCreated()));
        return Session.from(email, body);
    }

    protected Session login(String email, String password) throws Exception {
        JsonNode body = read(postJson("/api/v1/auth/login", Map.of("email", email, "password", password))
                .andExpect(status().isOk()));
        return Session.from(email, body);
    }

    protected UUID createChild(String accessToken, String name) throws Exception {
        JsonNode body = read(postJson("/api/v1/children", Map.of("displayName", name, "gradeId", "ecd_a"), accessToken)
                .andExpect(status().isCreated()));
        return UUID.fromString(body.get("id").asText());
    }

    public record Session(String email, UUID userId, String accessToken, String refreshToken) {

        static Session from(String email, JsonNode body) {
            return new Session(email, UUID.fromString(body.at("/user/id").asText()),
                    body.get("accessToken").asText(), body.get("refreshToken").asText());
        }
    }
}
