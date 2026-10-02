package dev.graphnous.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security off, the default: the open-source container.
 */
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class SecurityDisabledTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void letsAnyoneInToTheDefaultOrganization() throws Exception {
        this.mvc.perform(get("/api/v1/context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user").doesNotExist())
            .andExpect(jsonPath("$.organization").value("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void ignoresAnAccessToken() throws Exception {
        this.mvc.perform(get("/api/v1/context").header("Authorization", "Bearer not-checked"))
            .andExpect(status().isOk());
    }
}
