package com.eduplay.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eduplay.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class EduplayApplicationTests extends IntegrationTest {

    @Test
    void contextLoadsAndProbesRespond() throws Exception {
        assertThat(mockMvc).isNotNull();
        mockMvc.perform(get("/livez")).andExpect(status().isOk());
        mockMvc.perform(get("/readyz")).andExpect(status().isOk());
    }

    @Test
    void openApiDocumentIsGenerated() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
