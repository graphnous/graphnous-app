package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanUsageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanUsageService scanUsageService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void returnsWhatUsesANode() throws Exception {
        final var list = new ScanUsages.Node("list", "Method", "list", "com.example.Orders.list");

        when(scanUsageService.getUsages(any(), eq(scanId), eq("list"), eq(2))).thenReturn(new ScanUsages.Found(
            list,
            List.of(new ScanUsages.Usage(
                "CALLS",
                new ScanUsages.Node("show", "Method", "show", "com.example.OrderPage.show"),
                "OrderPage",
                "src/main/java/com/example/OrderPage.java",
                21,
                list,
                "Orders",
                1
            )),
            true
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "list").queryParam("depth", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scanId").value(scanId.id().toString()))
            .andExpect(jsonPath("$.subject.type").value("Method"))
            .andExpect(jsonPath("$.usages[0].relationship").value("CALLS"))
            .andExpect(jsonPath("$.usages[0].user.name").value("show"))
            .andExpect(jsonPath("$.usages[0].owner").value("OrderPage"))
            .andExpect(jsonPath("$.usages[0].file").value("src/main/java/com/example/OrderPage.java"))
            .andExpect(jsonPath("$.usages[0].line").value(21))
            .andExpect(jsonPath("$.usages[0].target.id").value("list"))
            .andExpect(jsonPath("$.usages[0].targetOwner").value("Orders"))
            .andExpect(jsonPath("$.usages[0].depth").value(1))
            .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void answersNotFoundForANodeTheScanDoesNotHave() throws Exception {
        when(scanUsageService.getUsages(any(), any(), eq("missing"), isNull()))
            .thenThrow(new NotFoundException("Node missing is not in the graph"));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "missing"))
            .andExpect(status().isNotFound());
    }

    @Test
    void answersBadRequestForWhatItCannotFollow() throws Exception {
        when(scanUsageService.getUsages(any(), any(), any(), eq(9)))
            .thenThrow(new ValidationException("depth must be between 1 and 3"));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "list").queryParam("depth", "9"))
            .andExpect(status().isBadRequest());
        // Without a node
        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()))
            .andExpect(status().isBadRequest());
    }
}
