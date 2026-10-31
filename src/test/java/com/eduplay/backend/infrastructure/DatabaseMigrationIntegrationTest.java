package com.eduplay.backend.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduplay.backend.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DatabaseMigrationIntegrationTest extends IntegrationTest {

    @Test
    void allTablesExist() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("users", "refresh_tokens", "password_reset_tokens", "email_verification_tokens",
                "consent_records", "children", "lesson_progress", "game_results", "subscriptions",
                "flyway_schema_history");
    }

    @Test
    void requiredIndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);
        assertThat(indexes).contains("ix_children_parent_id", "ix_lesson_progress_child_id",
                "ix_game_results_child_played_at", "ux_lesson_progress_lesson", "ux_users_email_active");
    }

    @Test
    void emailMustBeLowercase() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into users (email, password_hash, full_name, role) values ('Upper@Example.com', 'x', 'n', 'PARENT')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void childRequiresExistingParent() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into children (parent_id, display_name) values (?, 'Orphan')", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void lessonProgressIsUniquePerChildAndLesson() throws Exception {
        Session parent = registerParent();
        UUID childId = createChild(parent.accessToken(), "Unique");
        String insert = "insert into lesson_progress (child_id, section_id, topic_id, lesson_id) values (?, 'a', 'b', 'c')";
        jdbcTemplate.update(insert, childId);
        assertThatThrownBy(() -> jdbcTemplate.update(insert, childId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
