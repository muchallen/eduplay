package com.eduplay.backend.classroom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ClassroomIntegrationTest extends IntegrationTest {

    @Test
    void teacherRegistersCreatesClassAndParentLinksChildWithJoinCode() throws Exception {
        Session teacher = registerTeacher();
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "Tatenda");

        JsonNode classroom = read(postJson("/api/v1/classes", Map.of("name", "ECD A Blue", "gradeId", "ecd_a"),
                teacher.accessToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinCode").isNotEmpty())
                .andExpect(jsonPath("$.childCount").value(0)));
        String classId = classroom.get("id").asText();
        String joinCode = classroom.get("joinCode").asText();

        // Before linking, the teacher cannot see the child.
        getAuthorized("/api/v1/children/" + childId, teacher.accessToken()).andExpect(status().isNotFound());

        // Codes are forgiving: lower case and spaces are accepted.
        String typed = joinCode.substring(0, 4).toLowerCase() + " " + joinCode.substring(4);
        postJson("/api/v1/children/" + childId + "/classes", Map.of("joinCode", typed), parent.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(classId))
                .andExpect(jsonPath("$.teacherName").value("Test Parent"))
                .andExpect(jsonPath("$.joinCode").doesNotExist());
        getAuthorized("/api/v1/children/" + childId + "/classes", parent.accessToken())
                .andExpect(jsonPath("$.length()").value(1));

        getAuthorized("/api/v1/classes/" + classId + "/children", teacher.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(childId.toString()))
                .andExpect(jsonPath("$.items[0].access").value("TEACHER"));
        getAuthorized("/api/v1/children", teacher.accessToken())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].access").value("TEACHER"));
        getAuthorized("/api/v1/classes/" + classId, teacher.accessToken())
                .andExpect(jsonPath("$.childCount").value(1));

        // Teacher can sync and read reports for the linked child...
        postJson("/api/v1/children/" + childId + "/sync", Map.of("gameResults", List.of(result())),
                teacher.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedGameResults").value(1));
        getAuthorized("/api/v1/children/" + childId + "/report", teacher.accessToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.gamesPlayed").value(1));

        // ...but cannot edit or delete it, or see its other classes.
        mockMvc.perform(authorized(patch("/api/v1/children/" + childId), teacher.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("displayName", "X"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHILD_NOT_OWNED"));
        mockMvc.perform(authorized(delete("/api/v1/children/" + childId), teacher.accessToken()))
                .andExpect(status().isForbidden());
        getAuthorized("/api/v1/children/" + childId + "/classes", teacher.accessToken())
                .andExpect(status().isForbidden());

        // Parent removes the child from the class; the teacher loses access.
        mockMvc.perform(authorized(delete("/api/v1/children/" + childId + "/classes/" + classId),
                        parent.accessToken()))
                .andExpect(status().isNoContent());
        getAuthorized("/api/v1/children/" + childId + "/report", teacher.accessToken())
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCanCreateOwnPupilsAndManageClassMembership() throws Exception {
        Session teacher = registerTeacher();
        UUID pupil = createChild(teacher.accessToken(), "Pupil");
        String classId = read(postJson("/api/v1/classes", Map.of("name", "Class 1"), teacher.accessToken()))
                .get("id").asText();

        mockMvc.perform(authorized(put("/api/v1/classes/" + classId + "/children/" + pupil), teacher.accessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(authorized(put("/api/v1/classes/" + classId + "/children/" + pupil), teacher.accessToken()))
                .andExpect(status().isNoContent());
        getAuthorized("/api/v1/classes/" + classId, teacher.accessToken()).andExpect(jsonPath("$.childCount").value(1));
        getAuthorized("/api/v1/children/" + pupil, teacher.accessToken())
                .andExpect(jsonPath("$.access").value("OWNER"));

        // A parent's child cannot be added directly; the parent must use the join code.
        Session parent = registerParent();
        UUID parentsChild = createChild(parent.accessToken(), "Not yours");
        mockMvc.perform(authorized(put("/api/v1/classes/" + classId + "/children/" + parentsChild),
                        teacher.accessToken()))
                .andExpect(status().isNotFound());

        String oldCode = read(getAuthorized("/api/v1/classes/" + classId, teacher.accessToken()))
                .get("joinCode").asText();
        String newCode = read(postJson("/api/v1/classes/" + classId + "/join-code", Map.of(), teacher.accessToken())
                .andExpect(status().isOk())).get("joinCode").asText();
        postJson("/api/v1/children/" + parentsChild + "/classes", Map.of("joinCode", oldCode), parent.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        postJson("/api/v1/children/" + parentsChild + "/classes", Map.of("joinCode", newCode), parent.accessToken())
                .andExpect(status().isOk());

        mockMvc.perform(authorized(delete("/api/v1/classes/" + classId + "/children/" + parentsChild),
                        teacher.accessToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(authorized(delete("/api/v1/classes/" + classId), teacher.accessToken()))
                .andExpect(status().isNoContent());
        getAuthorized("/api/v1/classes", teacher.accessToken()).andExpect(jsonPath("$.length()").value(0));
        getAuthorized("/api/v1/children/" + pupil, teacher.accessToken()).andExpect(status().isOk());
    }

    @Test
    void classesAreTeacherOnlyAndPrivateToTheirTeacher() throws Exception {
        Session parent = registerParent();
        postJson("/api/v1/classes", Map.of("name", "Nope"), parent.accessToken())
                .andExpect(status().isForbidden());
        getAuthorized("/api/v1/classes", parent.accessToken()).andExpect(status().isForbidden());

        Session teacher = registerTeacher();
        Session otherTeacher = registerTeacher();
        String classId = read(postJson("/api/v1/classes", Map.of("name", "Mine"), teacher.accessToken()))
                .get("id").asText();
        getAuthorized("/api/v1/classes/" + classId, otherTeacher.accessToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        postJson("/api/v1/classes", Map.of("name", "Bad grade", "gradeId", "grade_7"), teacher.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GRADE"));
    }

    private Session registerTeacher() throws Exception {
        String email = uniqueEmail();
        Map<String, Object> body = registration(email);
        body.put("accountType", "TEACHER");
        JsonNode response = read(postJson("/api/v1/auth/register", body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("TEACHER")));
        return new Session(email, UUID.fromString(response.at("/user/id").asText()),
                response.get("accessToken").asText(), response.get("refreshToken").asText());
    }

    private Map<String, Object> result() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("gradeId", "ecd_a");
        item.put("sectionId", "number");
        item.put("topicId", "counting");
        item.put("lessonId", "level1");
        item.put("gameIndex", 0);
        item.put("gameType", "MatchingGame");
        item.put("score", 80);
        item.put("attempts", 1);
        item.put("mistakes", 1);
        item.put("durationSeconds", 40);
        item.put("completed", true);
        item.put("playedAt", Instant.now().minusSeconds(60).toString());
        return item;
    }
}
