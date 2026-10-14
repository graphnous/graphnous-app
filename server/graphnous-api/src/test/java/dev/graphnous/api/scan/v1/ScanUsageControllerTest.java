package dev.graphnous.api.scan.v1;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.usages.ScanUsageService;
import dev.graphnous.application.scan.usages.ScanUsages;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
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

    private static final String OPENAPI_SPEC =
            "https://graphnous.github.io/graphnous-schemas/openapi/v1.yaml";

    /**
     * Checks only the response, for requests that are invalid on purpose.
     */
    private static final OpenApiInteractionValidator RESPONSE_VALIDATOR =
        OpenApiInteractionValidator.createForSpecificationUrl(OPENAPI_SPEC)
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build()
            )
            .build();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanUsageService scanUsageService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    private final Project.ProjectId project = Project.ProjectId.generate();

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
                1,
                List.of(
                    new ScanUsages.Argument(null, "page", false),
                    new ScanUsages.Argument("size", "20", false),
                    new ScanUsages.Argument(null, "filters", true)
                ),
                scanId,
                null
            )),
            true
        ).withProjects(java.util.Map.of(scanId, project)));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "list").queryParam("depth", "2"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
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
            .andExpect(jsonPath("$.usages[0].arguments.length()").value(3))
            .andExpect(jsonPath("$.usages[0].arguments[0].name").doesNotExist())
            .andExpect(jsonPath("$.usages[0].arguments[0].value").value("page"))
            .andExpect(jsonPath("$.usages[0].arguments[1].name").value("size"))
            .andExpect(jsonPath("$.usages[0].arguments[2].spread").value(true))
            .andExpect(jsonPath("$.usages[0].scanId").value(scanId.id().toString()))
            .andExpect(jsonPath("$.usages[0].projectId").value(project.id().toString()))
            .andExpect(jsonPath("$.usages[0].request").doesNotExist())
            .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void returnsACallOverHttpFromAnotherProject() throws Exception {
        final var list = new ScanUsages.Node("list", "Method", "list", "com.example.Orders.list");
        final var webScan = Scan.ScanId.generate();
        final var web = Project.ProjectId.generate();

        when(scanUsageService.getUsages(any(), eq(scanId), eq("list"), isNull())).thenReturn(new ScanUsages.Found(
            list,
            List.of(new ScanUsages.Usage(
                "REQUESTS",
                new ScanUsages.Node("route", "Method", "GET", "src/app/api/orders/route:GET"),
                null,
                "src/app/api/orders/route.ts",
                9,
                list,
                "Orders",
                1,
                null,
                webScan,
                new ScanUsages.Request("GET", "{API_URL}/api/v1/orders", "GET", "/api/v1/orders")
            )),
            false
        ).withProjects(java.util.Map.of(scanId, project, webScan, web)));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "list"))
            .andExpect(status().isOk())
            .andExpect(openApi().isValid(OPENAPI_SPEC))
            .andExpect(jsonPath("$.usages[0].relationship").value("REQUESTS"))
            .andExpect(jsonPath("$.usages[0].scanId").value(webScan.id().toString()))
            .andExpect(jsonPath("$.usages[0].projectId").value(web.id().toString()))
            .andExpect(jsonPath("$.usages[0].request.callUrl").value("{API_URL}/api/v1/orders"))
            .andExpect(jsonPath("$.usages[0].request.endpointMethod").value("GET"))
            .andExpect(jsonPath("$.usages[0].request.endpointUrl").value("/api/v1/orders"));
    }

    @Test
    void answersNotFoundForANodeTheScanDoesNotHave() throws Exception {
        when(scanUsageService.getUsages(any(), any(), eq("missing"), isNull()))
            .thenThrow(new NotFoundException("Node missing is not in the graph"));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
    }

    @Test
    void answersBadRequestForWhatItCannotFollow() throws Exception {
        when(scanUsageService.getUsages(any(), any(), any(), eq(9)))
            .thenThrow(new ValidationException("depth must be between 1 and 3"));

        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()).queryParam("node", "list").queryParam("depth", "9"))
            .andExpect(status().isBadRequest())
            .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
        // Without a node
        mockMvc.perform(get("/api/v1/scans/{id}/usages", scanId.id()))
            .andExpect(status().isBadRequest())
            .andExpect(openApi().isValid(RESPONSE_VALIDATOR));
    }
}
