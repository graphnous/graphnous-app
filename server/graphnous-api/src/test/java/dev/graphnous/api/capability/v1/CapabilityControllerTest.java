package dev.graphnous.api.capability.v1;

import dev.graphnous.application.capability.Capabilities;
import dev.graphnous.application.capability.Capability;
import dev.graphnous.application.capability.CapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CapabilityControllerTest {

    private static final String OPENAPI_SPEC =
            "https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CapabilityService capabilityService;

    @Test
    void getCapabilities() throws Exception {
        when(capabilityService.getCapabilities()).thenReturn(
            new Capabilities(
                List.of(Capability.SCANNING, Capability.BYOK_AI),
                new Capabilities.Authorization(true)
            )
        );

        mockMvc.perform(get("/api/v1/capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities", contains("SCANNING", "BYOK_AI")))
            .andExpect(jsonPath("$.authorization.enabled").value(true))
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    @Test
    void getCapabilitiesWhenThereAreNone() throws Exception {
        when(capabilityService.getCapabilities()).thenReturn(
            new Capabilities(List.of(), new Capabilities.Authorization(false))
        );

        mockMvc.perform(get("/api/v1/capabilities"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities").isEmpty())
            .andExpect(jsonPath("$.authorization.enabled").value(false))
            .andExpect(openApi().isValid(OPENAPI_SPEC));
    }

}
