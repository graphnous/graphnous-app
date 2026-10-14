package dev.graphnous.api.scanner.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScannerControllerTest {

    private static final String OPENAPI_SPEC =
            "https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getsTheLanguagesOfTheConfiguredScanners() throws Exception {
        mockMvc.perform(get("/api/v1/scanners"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].language").value("JAVA"))
            .andExpect(jsonPath("$[1].language").value("TYPESCRIPT"));
    }

}
