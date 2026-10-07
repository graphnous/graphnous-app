package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.graph.ScanGraph;
import dev.graphnous.application.scan.graph.ScanGraphService;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanGraphControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanGraphService scanGraphService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void returnsTheGraphAroundTheScan() throws Exception {
        final var scan = scanId.id().toString();
        final var target = scan + "|backend";

        when(scanGraphService.getNeighbourhood(any(), eq(scanId), isNull(), isNull())).thenReturn(
            new ScanGraph.Neighbourhood(
                scan,
                2,
                List.of(
                    new ScanGraph.Node(scan, "Scan", "Scan", 0, Map.of()),
                    new ScanGraph.Node(target, "ScanTarget", "backend", 1, Map.of("language", "JAVA"))
                ),
                List.of(new ScanGraph.Edge(scan, target, "HAS_TARGET", Map.of())),
                false
            )
        );

        mockMvc.perform(get("/api/v1/scans/{id}/graph", scanId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scanId").value(scan))
            .andExpect(jsonPath("$.focus").value(scan))
            .andExpect(jsonPath("$.depth").value(2))
            .andExpect(jsonPath("$.nodes[1].type").value("ScanTarget"))
            .andExpect(jsonPath("$.nodes[1].properties.language").value("JAVA"))
            .andExpect(jsonPath("$.edges[0].source").value(scan))
            .andExpect(jsonPath("$.edges[0].target").value(target))
            .andExpect(jsonPath("$.edges[0].type").value("HAS_TARGET"))
            .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void passesTheFocusAndDepth() throws Exception {
        final var focus = scanId.id() + "|backend|orders|class:com.example.Order";

        when(scanGraphService.getNeighbourhood(any(), eq(scanId), eq(focus), eq(3))).thenReturn(
            new ScanGraph.Neighbourhood(focus, 3, List.of(), List.of(), true)
        );

        mockMvc.perform(get("/api/v1/scans/{id}/graph", scanId.id())
                .queryParam("focus", focus)
                .queryParam("depth", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.focus").value(focus))
            .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void answersNotFoundForANodeThatIsNotInTheScan() throws Exception {
        when(scanGraphService.getNeighbourhood(any(), any(), any(), any()))
            .thenThrow(new NotFoundException("Not in the graph"));

        mockMvc.perform(get("/api/v1/scans/{id}/graph", scanId.id()).queryParam("focus", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void answersBadRequestForADepthOutOfRange() throws Exception {
        when(scanGraphService.getNeighbourhood(any(), any(), any(), eq(9)))
            .thenThrow(new ValidationException("depth must be between 0 and 5"));

        mockMvc.perform(get("/api/v1/scans/{id}/graph", scanId.id()).queryParam("depth", "9"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void answersBadRequestForADepthThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/scans/{id}/graph", scanId.id()).queryParam("depth", "deep"))
            .andExpect(status().isBadRequest());
    }
}
