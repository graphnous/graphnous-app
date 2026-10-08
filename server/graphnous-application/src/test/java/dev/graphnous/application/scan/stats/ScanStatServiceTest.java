package dev.graphnous.application.scan.stats;

import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.Package;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanStatServiceTest {

    @Mock
    private ScanStatRepository scanStatRepository;

    private final Scan.ScanId scanId = Scan.ScanId.generate();
    private final Project.ProjectId projectId = Project.ProjectId.generate();

    @BeforeEach
    void setUp() {
        when(scanStatRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsTheStatsOfAScanWithoutAny() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend")));

        assertThat(stats.scanId()).isEqualTo(scanId);
        assertThat(stats.projectId()).isEqualTo(projectId);
        assertThat(stats.id()).isNotNull();
    }

    @Test
    void updatesTheStatsTheScanHas() {
        final var existing = new ScanStats(ScanStats.ScanStatId.generate(), scanId, projectId, 0, Map.of(), 0, 0);

        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.of(existing));

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend")));

        assertThat(stats.id()).isEqualTo(existing.id());
        assertThat(stats.languages().get("JAVA")).hasSize(2);
    }

    @Test
    void countsTheClassesOfAModule() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend")));

        assertThat(stats.numberOfModulesScanned()).isEqualTo(1);
        assertThat(stats.languages()).containsExactly(entry("JAVA", List.of(
            "backend/src/main/java/com/acme/Service.java",
            "backend/src/main/java/com/acme/Repository.java"
        )));
        assertThat(stats.numberOfClassesParsed()).isEqualTo(2);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(3);
    }

    @Test
    void countsNestedClasses() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var result = result("backend");
        final var service = result.getModules().getFirst().getFiles().getFirst().getClasses().getFirst();
        service.setClasses(List.of(type("com.acme.Service.Builder", 1)));

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result));

        assertThat(stats.numberOfClassesParsed()).isEqualTo(3);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(4);
    }

    @Test
    void countsTheClassesOfEachTarget() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend"), result("frontend")));

        // Both targets have a module at ".", which are different modules
        assertThat(stats.numberOfModulesScanned()).isEqualTo(2);
        assertThat(stats.languages().get("JAVA")).containsExactly(
            "backend/src/main/java/com/acme/Service.java",
            "backend/src/main/java/com/acme/Repository.java",
            "frontend/src/main/java/com/acme/Service.java",
            "frontend/src/main/java/com/acme/Repository.java"
        );
        assertThat(stats.numberOfClassesParsed()).isEqualTo(4);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(6);
    }

    @Test
    void listsTheFilesUnderTheLanguageOfTheirTarget() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(
            scanId,
            projectId,
            List.of(result("backend"), result("web", ScanTarget.Language.TYPESCRIPT), result(".", ScanTarget.Language.JAVA))
        );

        assertThat(stats.languages().keySet()).containsExactly("JAVA", "TYPESCRIPT");
        assertThat(stats.languages().get("TYPESCRIPT")).containsExactly(
            "web/src/main/java/com/acme/Service.java",
            "web/src/main/java/com/acme/Repository.java"
        );
        // A target at the root has no prefix
        assertThat(stats.languages().get("JAVA")).contains("src/main/java/com/acme/Service.java");
    }

    @Test
    void countsFunctionsOutsideClassesAsMethods() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var main = new Method();
        main.setName("main");
        main.setQualifiedName("src/main:main");

        final var result = result("web", ScanTarget.Language.TYPESCRIPT);
        result.getModules().getFirst().getFiles().getFirst().setFunctions(List.of(main));

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result));

        assertThat(stats.numberOfClassesParsed()).isEqualTo(2);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(4);
    }

    @Test
    void countsNothingWithoutResults() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of());

        assertThat(stats.numberOfModulesScanned()).isZero();
        assertThat(stats.languages()).isEmpty();
        assertThat(stats.numberOfClassesParsed()).isZero();
        assertThat(stats.numberOfMethodsParsed()).isZero();
    }

    private ScanStatService service() {
        return new ScanStatService(scanStatRepository);
    }

    /**
     * A target with one module of two files in one package: one with a
     * class of two methods, and one with a class of one.
     */
    private static ScanResult result(final String path) {
        return result(path, ScanTarget.Language.JAVA);
    }

    private static ScanResult result(final String path, final ScanTarget.Language language) {
        final var first = new File();
        first.setPath("src/main/java/com/acme/Service.java");
        first.setPackage("com.acme");
        first.setClasses(List.of(type("com.acme.Service", 2)));

        final var second = new File();
        second.setPath("src/main/java/com/acme/Repository.java");
        second.setPackage("com.acme");
        second.setClasses(List.of(type("com.acme.Repository", 1)));

        final var pkg = new Package();
        pkg.setName("acme");
        pkg.setQualifiedName("com.acme");

        final var module = new Module();
        module.setPath(".");
        module.setFiles(List.of(first, second));
        module.setPackages(List.of(pkg));

        final var target = new ScanTarget();
        target.setPath(path);
        target.setLanguage(language);

        final var result = new ScanResult();
        result.setTarget(target);
        result.setModules(List.of(module));

        return result;
    }

    private static Class type(final String qualifiedName, final int methods) {
        final var type = new Class();
        type.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        type.setQualifiedName(qualifiedName);

        final var list = new ArrayList<Method>();

        for (int i = 0; i < methods; i++) {
            final var method = new Method();
            method.setName("method" + i);
            method.setQualifiedName(qualifiedName + "#method" + i);
            list.add(method);
        }

        type.setMethods(list);

        return type;
    }
}
