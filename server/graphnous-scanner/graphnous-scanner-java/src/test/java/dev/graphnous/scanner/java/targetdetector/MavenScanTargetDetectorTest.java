package dev.graphnous.scanner.java.targetdetector;

import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MavenScanTargetDetectorTest {

    private final MavenScanTargetDetector detector = new MavenScanTargetDetector();

    @TempDir
    Path repository;

    @Test
    void detectsPomInRepositoryRootAsDotTarget() throws IOException {
        pom(repository);

        final var targets = detector.detect(repository);

        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.getPath()).isEqualTo(".");
            assertThat(target.getLanguage()).isEqualTo(ScanTarget.Language.JAVA);
            assertThat(target.getBuildSystem()).isEqualTo(ScanTarget.BuildSystem.MAVEN);
        });
    }

    @Test
    void detectsNestedPomsRelativeToRepository() throws IOException {
        pom(repository.resolve("services/api"));
        pom(repository.resolve("tools"));

        final var targets = detector.detect(repository);

        assertThat(targets)
            .extracting(ScanTarget::getPath)
            .containsExactlyInAnyOrder(
                Path.of("services", "api").toString(),
                "tools"
            );
    }

    @Test
    void treatsNestedPomsAsModulesOfTheRootProject() throws IOException {
        pom(repository);
        pom(repository.resolve("core"));
        pom(repository.resolve("services/api"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly(".");
    }

    @Test
    void detectsSeparateProjectsWithTheirOwnModules() throws IOException {
        pom(repository.resolve("backend"));
        pom(repository.resolve("backend/core"));
        pom(repository.resolve("backend/api"));
        pom(repository.resolve("tools/generator"));
        pom(repository.resolve("tools/generator/plugin"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly(
                "backend",
                Path.of("tools", "generator").toString()
            );
    }

    @Test
    void treatsDeeplyNestedPomsAsModules() throws IOException {
        pom(repository.resolve("backend"));
        pom(repository.resolve("backend/services/billing/api"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly("backend");
    }

    @Test
    void ignoresPomsInBuildOutput() throws IOException {
        pom(repository);
        pom(repository.resolve("target/generated-project"));
        pom(repository.resolve("core"));
        pom(repository.resolve("core/target/classes/META-INF/maven/dev.graphnous/core"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly(".");
    }

    @Test
    void ignoresRepositoriesWithoutPom() throws IOException {
        Files.createDirectories(repository.resolve("src"));
        Files.writeString(repository.resolve("build.gradle"), "");

        assertThat(detector.detect(repository)).isEmpty();
    }

    private static void pom(final Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), "<project/>");
    }
}
