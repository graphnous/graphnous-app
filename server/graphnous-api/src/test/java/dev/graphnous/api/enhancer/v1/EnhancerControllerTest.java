package dev.graphnous.api.enhancer.v1;

import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.EnhancerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EnhancerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnhancerRegistry enhancerRegistry;

    @Test
    void getEnhancers() throws Exception {
        final var enhancers = List.of(
            enhancer("spring", "1.0.0"),
            enhancer("angular", "0.1.0")
        );

        when(enhancerRegistry.enhancers()).thenReturn(enhancers);

        mockMvc.perform(get("/api/v1/enhancers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("spring"))
            .andExpect(jsonPath("$[0].version").value("1.0.0"))
            .andExpect(jsonPath("$[1].name").value("angular"))
            .andExpect(jsonPath("$[1].version").value("0.1.0"));
    }

    @Test
    void getEnhancersWhenThereAreNone() throws Exception {
        when(enhancerRegistry.enhancers()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/enhancers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    private static Enhancer enhancer(final String name, final String version) {
        final var enhancer = mock(Enhancer.class);

        when(enhancer.name()).thenReturn(name);
        when(enhancer.version()).thenReturn(version);

        return enhancer;
    }

}
