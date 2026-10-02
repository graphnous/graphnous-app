package dev.graphnous.scanner.typescript.targetdetector;

import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NpmTargetDetectorTest {

    private final NpmTargetDetector detector = new NpmTargetDetector();

    @TempDir
    Path repository;

    @Test
    void detectsPackageJsonInRepositoryRootAsDotTarget() throws IOException {
        packageJson(repository);

        final var targets = detector.detect(repository);

        assertThat(targets).singleElement().satisfies(target -> {
            assertThat(target.getPath()).isEqualTo(".");
            assertThat(target.getLanguage()).isEqualTo(ScanTarget.Language.TYPESCRIPT);
            assertThat(target.getBuildSystem()).isEqualTo(ScanTarget.BuildSystem.NPM);
        });
    }

    @Test
    void detectsNestedPackagesRelativeToRepository() throws IOException {
        packageJson(repository.resolve("apps/web"));
        packageJson(repository.resolve("libs"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactlyInAnyOrder(
                Path.of("apps", "web").toString(),
                "libs"
            );
    }

    @Test
    void ignoresPackagesInsideNodeModules() throws IOException {
        packageJson(repository.resolve("node_modules/typescript"));
        packageJson(repository.resolve("apps/web"));
        packageJson(repository.resolve("apps/web/node_modules/@scope/lib"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly(Path.of("apps", "web").toString());
    }

    @Test
    void treatsWorkspacePackagesAsPartOfTheRootProject() throws IOException {
        packageJson(repository);
        packageJson(repository.resolve("packages/ui"));
        packageJson(repository.resolve("packages/api"));
        packageJson(repository.resolve("apps/web"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly(".");
    }

    @Test
    void detectsSeparateProjectsWithTheirOwnPackages() throws IOException {
        packageJson(repository.resolve("frontend"));
        packageJson(repository.resolve("frontend/packages/ui"));
        packageJson(repository.resolve("tools/cli"));

        assertThat(detector.detect(repository))
            .extracting(ScanTarget::getPath)
            .containsExactly(
                "frontend",
                Path.of("tools", "cli").toString()
            );
    }

    @Test
    void ignoresDirectoriesNamedPackageJson() throws IOException {
        Files.createDirectories(repository.resolve("package.json"));

        assertThat(detector.detect(repository)).isEmpty();
    }

    @Test
    void ignoresRepositoriesWithoutPackageJson() throws IOException {
        Files.writeString(repository.resolve("pom.xml"), "<project/>");

        assertThat(detector.detect(repository)).isEmpty();
    }

    private static void packageJson(final Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("package.json"), "{}");
    }
}
