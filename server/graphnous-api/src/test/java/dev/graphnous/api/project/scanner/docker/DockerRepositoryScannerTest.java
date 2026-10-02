package dev.graphnous.api.project.scanner.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import dev.graphnous.application.project.scanner.ScanLogger;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.scanner.docker.DockerWorkspace;
import dev.graphnous.scanner.java.JavaScannerDefinition;
import dev.graphnous.scanner.java.language.JavaVersionDetector;
import dev.graphnous.scanner.java.targetdetector.MavenScanTargetDetector;
import dev.graphnous.scanner.plan.DefaultScanPlanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DockerRepositoryScannerTest {

    private static final String POM = """
        <project>
            <modelVersion>4.0.0</modelVersion>
            <groupId>com.example</groupId>
            <artifactId>shop</artifactId>
            <version>1.0</version>
        </project>
        """;

    @TempDir
    Path repository;

    // Never reached for a cancelled scan; deep stubs let cancelling list no containers
    private final DockerClient docker = mock(DockerClient.class, RETURNS_DEEP_STUBS);

    private final DockerScanContainers containers = new DockerScanContainers(docker, "test");

    private final ScanLogger logger = (level, message) -> { };

    @Test
    void startsNoContainerForACancelledScan() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), POM);

        final var scanId = Scan.ScanId.generate();
        containers.cancel(scanId);

        final var scanner = scanner();
        final var plan = scanner.plan(repository, logger);

        assertThat(plan.targets()).hasSize(1);

        final var report = scanner.scan(scanId, repository, plan, logger);

        assertThat(report.results()).isEmpty();
        assertThat(report.failures())
            .singleElement()
            .satisfies(failure -> assertThat(failure.error()).hasMessage("Scan was cancelled"));

        verify(docker, never()).createContainerCmd(anyString());
    }

    private DockerRepositoryScanner scanner() {
        final var planner = new DefaultScanPlanner(
            List.of(new MavenScanTargetDetector()),
            List.of(new JavaVersionDetector())
        );

        return new DockerRepositoryScanner(
            planner,
            List.of(new JavaScannerDefinition(repository.resolve("java-scanner.jar"))),
            docker,
            new DockerWorkspace.NamedVolume("checkouts", repository),
            new ObjectMapper(),
            containers
        );
    }
}
