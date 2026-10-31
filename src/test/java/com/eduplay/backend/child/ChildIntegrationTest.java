package com.eduplay.backend.child;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import java.time.Year;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ChildIntegrationTest extends IntegrationTest {

    @Test
    void parentCanCreateListUpdateAndDeleteChildren() throws Exception {
        Session parent = registerParent();
        int birthYear = Year.now().getValue() - 6;

        postJson("/api/v1/children", Map.of("displayName", "Tendai", "birthYear", birthYear, "avatarId", "lion_01",
                        "gradeId", "ecd_b"), parent.accessToken())
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.displayName").value("Tendai"))
                .andExpect(jsonPath("$.birthYear").value(birthYear))
                .andExpect(jsonPath("$.avatarId").value("lion_01"))
                .andExpect(jsonPath("$.gradeId").value("ecd_b"))
                .andExpect(jsonPath("$.access").value("OWNER"));
        clock.advance(java.time.Duration.ofSeconds(1));
        UUID second = createChild(parent.accessToken(), "Rudo");

        getAuthorized("/api/v1/children", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].displayName").value("Tendai"))
                .andExpect(jsonPath("$.totalItems").value(2));

        mockMvc.perform(authorized(patch("/api/v1/children/" + second), parent.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Rudo M", "avatarId", "zebra_02", "gradeId", "ecd_b"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradeId").value("ecd_b"))
                .andExpect(jsonPath("$.displayName").value("Rudo M"))
                .andExpect(jsonPath("$.avatarId").value("zebra_02"));

        mockMvc.perform(authorized(delete("/api/v1/children/" + second), parent.accessToken()))
                .andExpect(status().isNoContent());
        getAuthorized("/api/v1/children/" + second, parent.accessToken()).andExpect(status().isNotFound());
        getAuthorized("/api/v1/children", parent.accessToken()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void childListIsPaginated() throws Exception {
        Session parent = registerParent();
        for (int i = 0; i < 3; i++) {
            createChild(parent.accessToken(), "Child " + i);
        }
        getAuthorized("/api/v1/children?page=1&size=2", parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalItems").value(3));
        getAuthorized("/api/v1/children?size=1000", parent.accessToken())
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherParentCannotSeeOrModifyMyChild() throws Exception {
        Session owner = registerParent();
        Session other = registerParent();
        UUID childId = createChild(owner.accessToken(), "Tariro");
        String base = "/api/v1/children/" + childId;

        getAuthorized(base, other.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHILD_NOT_FOUND"));
        mockMvc.perform(authorized(patch(base), other.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("displayName", "Hacked"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(delete(base), other.accessToken())).andExpect(status().isNotFound());
        getAuthorized(base + "/progress", other.accessToken()).andExpect(status().isNotFound());
        getAuthorized(base + "/game-results", other.accessToken()).andExpect(status().isNotFound());
        getAuthorized(base + "/report", other.accessToken()).andExpect(status().isNotFound());
        postJson(base + "/sync", Map.of("lessonProgress", java.util.List.of()), other.accessToken())
                .andExpect(status().isNotFound());
        getAuthorized("/api/v1/children", other.accessToken()).andExpect(jsonPath("$.items.length()").value(0));

        getAuthorized(base, owner.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Tariro"));
    }

    @Test
    void childValidationRules() throws Exception {
        Session parent = registerParent();
        postJson("/api/v1/children", Map.of("displayName", ""), parent.accessToken())
                .andExpect(status().isBadRequest());
        postJson("/api/v1/children", Map.of("displayName", "Too Old", "birthYear", 1980, "gradeId", "ecd_a"), parent.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIRTH_YEAR"));
        postJson("/api/v1/children", Map.of("displayName", "Bad Avatar", "avatarId", "../etc", "gradeId", "ecd_a"),
                        parent.accessToken())
                .andExpect(status().isBadRequest());
        postJson("/api/v1/children", Map.of("displayName", "No Grade"), parent.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("gradeId"));
        postJson("/api/v1/children", Map.of("displayName", "Grade 7", "gradeId", "grade_7"), parent.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRADE"));
    }

    @Test
    void numberOfChildrenPerParentIsLimited() throws Exception {
        Session parent = registerParent();
        for (int i = 0; i < 10; i++) {
            createChild(parent.accessToken(), "Child " + i);
        }
        postJson("/api/v1/children", Map.of("displayName", "One too many", "gradeId", "ecd_a"), parent.accessToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHILD_LIMIT_REACHED"));
    }
}
