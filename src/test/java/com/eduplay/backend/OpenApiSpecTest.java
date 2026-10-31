package com.eduplay.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Keeps docs/openapi.yaml in step with the code. When the API changes, regenerate it with:
 * {@code OPENAPI_WRITE=true ./mvnw test -Dtest=OpenApiSpecTest}
 */
class OpenApiSpecTest extends IntegrationTest {

    private static final Path SPEC_FILE = Path.of("docs", "openapi.yaml");

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if ("true".equalsIgnoreCase(System.getenv("OPENAPI_WRITE"))) {
            Files.createDirectories(SPEC_FILE.getParent());
            Files.writeString(SPEC_FILE, generated, StandardCharsets.UTF_8);
        }

        assertThat(SPEC_FILE).as("docs/openapi.yaml is missing; regenerate it (see class javadoc)").exists();
        YAMLMapper yaml = new YAMLMapper();
        JsonNode committed = yaml.readTree(Files.readString(SPEC_FILE, StandardCharsets.UTF_8));
        assertThat(committed)
                .as("docs/openapi.yaml is out of date; run: OPENAPI_WRITE=true ./mvnw test -Dtest=OpenApiSpecTest")
                .isEqualTo(yaml.readTree(generated));
    }
}
