package com.eduplay.backend.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

    @Test
    void buildsUrlFromParts() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.resolve(null, "postgres", "5432", "eduplay",
                "disable", false, false);
        assertThat(props).containsEntry("spring.datasource.url",
                "jdbc:postgresql://postgres:5432/eduplay?sslmode=disable");
    }

    @Test
    void usesJdbcUrlAsIs() {
        String url = "jdbc:postgresql://db.example.com:5432/postgres?sslmode=verify-full";
        assertThat(DatabaseUrlEnvironmentPostProcessor.resolve(url, "x", "1", "y", "disable", false, false))
                .containsOnly(Map.entry("spring.datasource.url", url));
    }

    @Test
    void convertsSupabaseConnectionString() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.resolve(
                "postgresql://postgres.abcd:p%40ss%3Aword@aws-0-af-south-1.pooler.supabase.com:5432/postgres",
                "ignored", "1", "ignored", "require", false, false);
        assertThat(props)
                .containsEntry("spring.datasource.url",
                        "jdbc:postgresql://aws-0-af-south-1.pooler.supabase.com:5432/postgres?sslmode=require")
                .containsEntry("spring.datasource.username", "postgres.abcd")
                .containsEntry("spring.datasource.password", "p@ss:word");
    }

    @Test
    void explicitCredentialsAndSslModeWin() {
        Map<String, Object> props = DatabaseUrlEnvironmentPostProcessor.resolve(
                "postgres://user:secret@host/db?sslmode=verify-full", "x", "1", "y", "require", true, true);
        assertThat(props)
                .containsEntry("spring.datasource.url", "jdbc:postgresql://host:5432/db?sslmode=verify-full")
                .doesNotContainKeys("spring.datasource.username", "spring.datasource.password");
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThatThrownBy(() -> DatabaseUrlEnvironmentPostProcessor.resolve("mysql://h/db", "x", "1", "y", "z",
                false, false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitSpringDatasourceUrlIsNotOverridden() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://explicit/db")
                .withProperty("DB_HOST", "other");
        new DatabaseUrlEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://explicit/db");
    }
}
