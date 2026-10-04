package dev.graphnous.application.scan.stats;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.stats.ScanStats;
import dev.graphnous.scanner.model.Class;
import dev.graphnous.scanner.model.File;
import dev.graphnous.scanner.model.Method;
import dev.graphnous.scanner.model.Module;
import dev.graphnous.scanner.model.Package;
import dev.graphnous.scanner.model.ScanResultSchema;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
        final var existing = new ScanStats(ScanStats.ScanStatId.generate(), scanId, projectId, 0, 0, 0);

        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.of(existing));

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend")));

        assertThat(stats.id()).isEqualTo(existing.id());
        assertThat(stats.numberOfFilesScanned()).isEqualTo(2);
    }

    @Test
    void countsAClassListedInAFileAndAPackageOnce() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend")));

        assertThat(stats.numberOfFilesScanned()).isEqualTo(2);
        assertThat(stats.numberOfClassesParsed()).isEqualTo(2);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(3);
    }

    @Test
    void countsTheClassesOfEachTarget() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of(result("backend"), result("frontend")));

        assertThat(stats.numberOfFilesScanned()).isEqualTo(4);
        assertThat(stats.numberOfClassesParsed()).isEqualTo(4);
        assertThat(stats.numberOfMethodsParsed()).isEqualTo(6);
    }

    @Test
    void countsNothingWithoutResults() {
        when(scanStatRepository.findByScanId(scanId)).thenReturn(Optional.empty());

        final var stats = service().createOrUpdate(scanId, projectId, List.of());

        assertThat(stats.numberOfFilesScanned()).isZero();
        assertThat(stats.numberOfClassesParsed()).isZero();
        assertThat(stats.numberOfMethodsParsed()).isZero();
    }

    private ScanStatService service() {
        return new ScanStatService(scanStatRepository);
    }

    /**
     * A target with one module of two files: one with a class of two
     * methods, also listed in its package, and one with a class of one.
     */
    private static ScanResultSchema result(final String path) {
        final var service = type("com.acme.Service", 2);

        final var first = new File();
        first.setPath("src/main/java/com/acme/Service.java");
        first.setClasses(List.of(service));

        final var second = new File();
        second.setPath("src/main/java/com/acme/Repository.java");
        second.setClasses(List.of(type("com.acme.Repository", 1)));

        final var pkg = new Package();
        pkg.setName("acme");
        pkg.setQualifiedName("com.acme");
        pkg.setClasses(List.of(service));

        final var module = new Module();
        module.setPath(".");
        module.setFiles(List.of(first, second));
        module.setPackages(List.of(pkg));

        final var target = new ScanTarget();
        target.setPath(path);
        target.setLanguage(ScanTarget.Language.JAVA);

        final var result = new ScanResultSchema();
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
