package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.classes.ScanClass;
import dev.graphnous.application.scan.classes.ScanClassFilter;
import dev.graphnous.application.scan.classes.ScanClassService;
import dev.graphnous.application.scan.dependencies.ScanDependency;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.dependencies.ScanDependencyService;
import dev.graphnous.application.scan.files.ScanFile;
import dev.graphnous.application.scan.files.ScanFileFilter;
import dev.graphnous.application.scan.files.ScanFileService;
import dev.graphnous.application.scan.methods.ScanMethod;
import dev.graphnous.application.scan.methods.ScanMethodFilter;
import dev.graphnous.application.scan.methods.ScanMethodService;
import dev.graphnous.application.scan.modules.ScanModule;
import dev.graphnous.application.scan.modules.ScanModuleFilter;
import dev.graphnous.application.scan.modules.ScanModuleService;
import dev.graphnous.application.scan.packages.ScanPackage;
import dev.graphnous.application.scan.packages.ScanPackageFilter;
import dev.graphnous.application.scan.packages.ScanPackageService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The modules, packages, files, classes, methods and dependencies of a scan,
 * a page at a time.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ScanListingControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanModuleService scanModuleService;

    @MockitoBean
    private ScanPackageService scanPackageService;

    @MockitoBean
    private ScanFileService scanFileService;

    @MockitoBean
    private ScanClassService scanClassService;

    @MockitoBean
    private ScanMethodService scanMethodService;

    @MockitoBean
    private ScanDependencyService scanDependencyService;

    @MockitoBean
    private RequestContextProvider contextProvider;

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void listsTheModulesOfAScan() throws Exception {
        when(scanModuleService.getModules(
            any(),
            eq(scanId),
            eq(new ScanModuleFilter("ord", "backend")),
            eq(new PageQuery(0, 50, new Sort("name", Sort.Direction.ASC)))
        )).thenReturn(new Page<>(
            List.of(new ScanModule(scanId.id() + "|backend|orders", "backend", "orders", "orders", 2, 1, 3, 2, 2)),
            0, 50, 1, 1
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/modules", scanId.id())
                .queryParam("query", "ord")
                .queryParam("target", "backend")
                .queryParam("sort", "name"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(scanId.id() + "|backend|orders"))
            .andExpect(jsonPath("$.content[0].target").value("backend"))
            .andExpect(jsonPath("$.content[0].path").value("orders"))
            .andExpect(jsonPath("$.content[0].files").value(2))
            .andExpect(jsonPath("$.content[0].classes").value(3))
            .andExpect(jsonPath("$.content[0].methods").value(2))
            .andExpect(jsonPath("$.content[0].dependencies").value(2))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listsThePackagesOfAScanByQualifiedNameByDefault() throws Exception {
        when(scanPackageService.getPackages(
            any(),
            eq(scanId),
            eq(new ScanPackageFilter(null, "orders")),
            eq(new PageQuery(0, 50, new Sort("qualifiedName", Sort.Direction.ASC)))
        )).thenReturn(new Page<>(
            List.of(new ScanPackage("package", "orders", "example", "com.example", 3, 0, 0)),
            0, 50, 1, 1
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/packages", scanId.id()).queryParam("module", "orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].qualifiedName").value("com.example"))
            .andExpect(jsonPath("$.content[0].module").value("orders"))
            .andExpect(jsonPath("$.content[0].classes").value(3));
    }

    @Test
    void listsTheFilesOfAScan() throws Exception {
        final var file = scanId.id() + "|backend|orders|file:src/main/java/com/example/Order.java";

        when(scanFileService.getFiles(
            any(),
            eq(scanId),
            eq(new ScanFileFilter("order", "orders", null, "MAIN")),
            eq(new PageQuery(1, 10, new Sort("size", Sort.Direction.DESC)))
        )).thenReturn(new Page<>(
            List.of(new ScanFile(file, "backend", "orders", "src/main/java/com/example/Order.java", "JAVA", "MAIN", 120L, "abc", 1, 0)),
            1, 10, 11, 2
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/files", scanId.id())
                .queryParam("query", "order")
                .queryParam("module", "orders")
                .queryParam("sourceSet", "MAIN")
                .queryParam("page", "1")
                .queryParam("size", "10")
                .queryParam("sort", "size")
                .queryParam("direction", "desc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(file))
            .andExpect(jsonPath("$.content[0].path").value("src/main/java/com/example/Order.java"))
            .andExpect(jsonPath("$.content[0].size").value(120))
            .andExpect(jsonPath("$.content[0].classes").value(1))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.totalElements").value(11))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void listsTheClassesOfAScanByQualifiedNameByDefault() throws Exception {
        when(scanClassService.getClasses(
            any(),
            eq(scanId),
            eq(ScanClassFilter.none()),
            eq(new PageQuery(0, 50, new Sort("qualifiedName", Sort.Direction.ASC)))
        )).thenReturn(new Page<>(
            List.of(new ScanClass(
                "class", "Order", "com.example.Order", "CLASS", List.of("PUBLIC"), "orders",
                "src/main/java/com/example/Order.java", "com.example", "com.example.Entity",
                List.of("com.example.Identified"), List.of("jakarta.ws.rs.Path"), 2, 1
            )),
            0, 50, 1, 1
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/classes", scanId.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].qualifiedName").value("com.example.Order"))
            .andExpect(jsonPath("$.content[0].packageName").value("com.example"))
            .andExpect(jsonPath("$.content[0].interfaces[0]").value("com.example.Identified"))
            .andExpect(jsonPath("$.content[0].annotations[0]").value("jakarta.ws.rs.Path"))
            .andExpect(jsonPath("$.content[0].methods").value(2))
            .andExpect(jsonPath("$.content[0].fields").value(1));
    }

    @Test
    void listsTheMethodsAndFunctionsOfAScan() throws Exception {
        when(scanMethodService.getMethods(
            any(),
            eq(scanId),
            eq(new ScanMethodFilter(null, null, "function", null)),
            any()
        )).thenReturn(new Page<>(
            List.of(new ScanMethod(
                "function", "total", "app.orders.total", "FUNCTION", List.of(), "float",
                List.of("order"), List.of("app.orders.Order"), List.of("functools.cache"), null, ".", "app/orders.py"
            )),
            0, 50, 1, 1
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/methods", scanId.id()).queryParam("kind", "function"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].kind").value("FUNCTION"))
            .andExpect(jsonPath("$.content[0].returnType").value("float"))
            .andExpect(jsonPath("$.content[0].parameterTypes[0]").value("app.orders.Order"))
            .andExpect(jsonPath("$.content[0].className").doesNotExist())
            .andExpect(jsonPath("$.content[0].file").value("app/orders.py"));
    }

    @Test
    void listsTheDependenciesOfAScan() throws Exception {
        when(scanDependencyService.getDependencies(
            any(),
            eq(scanId),
            eq(new ScanDependencyFilter(null, null, "test")),
            eq(new PageQuery(0, 50, new Sort("name", Sort.Direction.ASC)))
        )).thenReturn(new Page<>(
            List.of(new ScanDependency("dependency:org.junit.jupiter:junit-jupiter:", "orders", "org.junit.jupiter:junit-jupiter", null, "test")),
            0, 50, 1, 1
        ));

        mockMvc.perform(get("/api/v1/scans/{id}/dependencies", scanId.id()).queryParam("scope", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value("dependency:org.junit.jupiter:junit-jupiter:"))
            .andExpect(jsonPath("$.content[0].name").value("org.junit.jupiter:junit-jupiter"))
            .andExpect(jsonPath("$.content[0].scope").value("test"));
    }

    @Test
    void answersNotFoundForAScanThatIsNotThere() throws Exception {
        when(scanClassService.getClasses(any(), any(), any(), any())).thenThrow(new NotFoundException("Scan not found"));

        mockMvc.perform(get("/api/v1/scans/{id}/classes", scanId.id()))
            .andExpect(status().isNotFound());
    }

    @Test
    void answersBadRequestForAnInvalidPage() throws Exception {
        for (final var request : List.of(
            get("/api/v1/scans/{id}/modules", scanId.id()).queryParam("sort", "files"),
            get("/api/v1/scans/{id}/packages", scanId.id()).queryParam("size", "0"),
            get("/api/v1/scans/{id}/files", scanId.id()).queryParam("sort", "name"),
            get("/api/v1/scans/{id}/files", scanId.id()).queryParam("sourceSet", "both"),
            get("/api/v1/scans/{id}/classes", scanId.id()).queryParam("size", "101"),
            get("/api/v1/scans/{id}/methods", scanId.id()).queryParam("kind", "lambda"),
            get("/api/v1/scans/{id}/methods", scanId.id()).queryParam("page", "-1"),
            get("/api/v1/scans/{id}/dependencies", scanId.id()).queryParam("direction", "sideways"),
            get("/api/v1/scans/{id}/dependencies", "latest")
        )) {
            mockMvc.perform(request).andExpect(status().isBadRequest());
        }

        verifyNoInteractions(
            scanModuleService, scanPackageService, scanFileService, scanClassService, scanMethodService, scanDependencyService
        );
    }
}
