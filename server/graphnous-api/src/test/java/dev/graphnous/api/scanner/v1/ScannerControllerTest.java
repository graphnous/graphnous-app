package dev.graphnous.api.scanner.v1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScannerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getsTheLanguagesOfTheConfiguredScanners() throws Exception {
        mockMvc.perform(get("/api/v1/scanners"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].language").value("JAVA"))
            .andExpect(jsonPath("$[1].language").value("TYPESCRIPT"));
    }

}
