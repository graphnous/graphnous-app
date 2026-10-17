package dev.graphnous.application.scan.source;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanSourceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");

    @Mock
    private SystemService systemService;

    @Mock
    private ProjectService projectService;

    @Mock
    private ScanService scanService;

    @Mock
    private ScanSourceRepository repository;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final System system = new System(System.SystemId.generate(), "Shop", null, NOW, NOW);

    private final Project app = project("app", "https://github.com/acme/shop.git", null);

    private final ScanSource.Declaration orderService = declaration("CLASS", "OrderService", "com.acme.OrderService", null);
    private final ScanSource.Declaration create = declaration("METHOD", "create", "com.acme.OrderService.create(Order)", "com.acme.OrderService");
    private final ScanSource.Declaration repositoryField = declaration("FIELD", "repository", null, "com.acme.OrderService");

    private final ScanSource.File file = new ScanSource.File(
        "f1", "server", "orders", "orders/src/main/java/com/acme/OrderService.java", "JAVA", "MAIN",
        List.of(orderService, create, repositoryField)
    );

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/acme/shop",
        "https://github.com/acme/shop.git",
        "https://user@GitHub.com/acme/shop/",
        "git@github.com:acme/shop.git",
        "ssh://git@github.com/acme/shop.git",
        "ssh://git@github.com:22/acme/shop"
    })
    void takesEachFormOfTheRepositorysUrl(final String gitUrl) {
        assertThat(ScanSourceService.repositoryKey(gitUrl)).isEqualTo("github.com/acme/shop");
    }

    @Test
    void findsAFileInTheNewestCompletedScanOfItsProject() {
        final var completed = scan(app, Scan.ScanStatus.COMPLETED);

        projects(app);
        when(scanService.getScans(eq(context), any(), eq(app.id())))
            .thenReturn(page(scan(app, Scan.ScanStatus.RUNNING), completed, scan(app, Scan.ScanStatus.COMPLETED)));
        when(repository.findFilesEndingPath(completed.id(), "server/orders/src/main/java/com/acme/OrderService.java"))
            .thenReturn(List.of(file));

        final var found = service().find(
            context, "git@github.com:acme/shop.git", "./server\\orders/src/main/java/com/acme/OrderService.java", null
        );

        assertThat(found.project()).isEqualTo(app);
        assertThat(found.scan()).isEqualTo(completed);
        assertThat(found.path()).isEqualTo("server/orders/src/main/java/com/acme/OrderService.java");
        assertThat(found.file()).isEqualTo(file);
        assertThat(found.declarations()).containsExactly(orderService, create, repositoryField);
    }

    @Test
    void findsAFileWhosePathIsWithinTheModule() {
        final var completed = scan(app, Scan.ScanStatus.COMPLETED);
        final var inModule = new ScanSource.File(
            "f2", "backend", "orders", "src/main/java/com/acme/OrderService.java", "JAVA", "MAIN", List.of()
        );

        projects(app);
        when(scanService.getScans(eq(context), any(), eq(app.id()))).thenReturn(page(completed));
        when(repository.findFilesEndingPath(completed.id(), "backend/orders/src/main/java/com/acme/OrderService.java"))
            .thenReturn(List.of(inModule));

        assertThat(service().find(context, "https://github.com/acme/shop", "backend/orders/src/main/java/com/acme/OrderService.java", null).file())
            .isEqualTo(inModule);
    }

    @Test
    void takesTheProjectOfTheDeepestDirectoryTheFileIsIn() {
        final var web = project("web", "https://github.com/acme/shop", "web");
        final var docs = project("docs", "https://github.com/acme/shop", "docs");
        final var completed = scan(web, Scan.ScanStatus.COMPLETED);
        final var page = new ScanSource.File("f3", ".", ".", "src/page.tsx", "TYPESCRIPT", null, List.of());

        projects(app, web, docs, project("other", "https://github.com/acme/other", null));
        when(scanService.getScans(eq(context), any(), eq(web.id()))).thenReturn(page(completed));
        when(repository.findFilesEndingPath(completed.id(), "src/page.tsx")).thenReturn(List.of(page));

        final var found = service().find(context, "https://github.com/acme/shop", "web/src/page.tsx", null);

        assertThat(found.project()).isEqualTo(web);
        assertThat(found.file()).isEqualTo(page);
    }

    @Test
    void picksTheFileWithTheSameWholePath() {
        final var completed = scan(app, Scan.ScanStatus.COMPLETED);
        final var other = new ScanSource.File("f4", "server", "orders", "Order.java", "JAVA", "MAIN", List.of());
        final var order = new ScanSource.File("f5", "server", ".", "orders/Order.java", "JAVA", "MAIN", List.of());

        projects(app);
        when(scanService.getScans(eq(context), any(), eq(app.id()))).thenReturn(page(completed));
        when(repository.findFilesEndingPath(completed.id(), "server/orders/Order.java")).thenReturn(List.of(order, other));

        // Both end the path, and both name it: the first in order
        assertThat(service().find(context, "https://github.com/acme/shop", "server/orders/Order.java", null).file())
            .isEqualTo(order);
    }

    @Test
    void findsWhatAFileDeclaresByName() {
        assertThat(ScanSourceService.declarations(file, "create")).containsExactly(create);
        assertThat(ScanSourceService.declarations(file, "orderservice")).containsExactly(orderService);
        assertThat(ScanSourceService.declarations(file, "OrderService.create")).containsExactly(create);
        assertThat(ScanSourceService.declarations(file, "com.acme.OrderService.repository")).containsExactly(repositoryField);
        assertThat(ScanSourceService.declarations(file, "Other.create")).isEmpty();
        assertThat(ScanSourceService.declarations(file, " ")).containsExactly(orderService, create, repositoryField);
    }

    @Test
    void findsNoProjectForAnotherRepository() {
        projects(app);

        assertThatThrownBy(() -> service().find(context, "https://github.com/acme/other", "README.md", null))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("No project is repository https://github.com/acme/other");
    }

    @Test
    void findsNoProjectForAFileOutsideItsDirectory() {
        projects(project("web", "https://github.com/acme/shop", "web"));

        assertThatThrownBy(() -> service().find(context, "https://github.com/acme/shop", "website/index.ts", null))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void saysWhenTheProjectHasNoCompletedScan() {
        projects(app);
        when(scanService.getScans(eq(context), any(), eq(app.id()))).thenReturn(page(scan(app, Scan.ScanStatus.FAILED)));

        assertThatThrownBy(() -> service().find(context, "https://github.com/acme/shop", "README.md", null))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Project app has no completed scan");
    }

    @Test
    void saysWhenTheScanDoesNotHaveTheFile() {
        final var completed = scan(app, Scan.ScanStatus.COMPLETED);

        projects(app);
        when(scanService.getScans(eq(context), any(), eq(app.id()))).thenReturn(page(completed));
        when(repository.findFilesEndingPath(completed.id(), "src/New.java")).thenReturn(List.of());

        assertThatThrownBy(() -> service().find(context, "https://github.com/acme/shop", "src/New.java", null))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("has no file src/New.java, at revision abc123");
    }

    @Test
    void needsTheUrlAndThePath() {
        assertThatThrownBy(() -> service().find(context, " ", "README.md", null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service().find(context, "https://github.com/acme/shop", "./", null))
            .isInstanceOf(ValidationException.class);

        verifyNoInteractions(systemService, repository);
    }

    private void projects(final Project... projects) {
        when(systemService.getSystems(eq(context), any())).thenReturn(page(system));
        when(projectService.getProjects(eq(context), any(), eq(system.id()))).thenReturn(page(projects));
    }

    private Project project(final String name, final String gitUrl, final String path) {
        return new Project(Project.ProjectId.generate(), name, null, gitUrl, path, system.id(), NOW, NOW);
    }

    private static Scan scan(final Project project, final Scan.ScanStatus status) {
        return new Scan(Scan.ScanId.generate(), project.id(), status, new Scan.SourceRevision("abc123", "main"), NOW, NOW, NOW);
    }

    private static ScanSource.Declaration declaration(
        final String type,
        final String name,
        final String qualifiedName,
        final String className
    ) {
        return new ScanSource.Declaration(UUID.randomUUID().toString(), type, type, name, qualifiedName, className);
    }

    @SafeVarargs
    private static <T> Page<T> page(final T... items) {
        return new Page<>(List.of(items), 0, 100, items.length, 1);
    }

    private ScanSourceService service() {
        return new ScanSourceService(systemService, projectService, scanService, repository);
    }
}
