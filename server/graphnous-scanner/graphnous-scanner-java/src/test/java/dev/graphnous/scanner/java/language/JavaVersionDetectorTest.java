package dev.graphnous.scanner.java.language;

import dev.graphnous.core.model.ScanTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaVersionDetectorTest {

    private final JavaVersionDetector detector = new JavaVersionDetector();

    @TempDir
    Path repository;

    @Test
    void supportsJavaTargetsOnly() {
        assertThat(detector.supports(target(ScanTarget.Language.JAVA, "."))).isTrue();
        assertThat(detector.supports(target(ScanTarget.Language.TYPESCRIPT, "."))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"java.version", "maven.compiler.release", "maven.compiler.source"})
    void readsVersionFromProperty(final String property) throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <%s> 21 </%s>
                </properties>
            </project>
            """.formatted(property, property));

        assertThat(detect(".")).isEqualTo("21");
    }

    @Test
    void readsThePomOfTheTarget() throws IOException {
        pom(repository.resolve("backend"), """
            <project>
                <properties>
                    <maven.compiler.release>25</maven.compiler.release>
                </properties>
            </project>
            """);

        assertThat(detect("backend")).isEqualTo("25");
    }

    @Test
    void failsWithoutProperties() throws IOException {
        pom(repository, "<project/>");

        assertThatThrownBy(() -> detect("."))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageStartingWith("Failed to determine Java version");
    }

    @Test
    void failsWithoutVersionProperty() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>
            </project>
            """);

        assertThatThrownBy(() -> detect("."))
            .isInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageStartingWith("Could not determine Java version from");
    }

    @Test
    void failsWithoutPom() {
        assertThatThrownBy(() -> detect("."))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void readsVersionInheritedFromParent() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <maven.compiler.release>25</maven.compiler.release>
                </properties>
            </project>
            """);
        pom(repository.resolve("core"), """
            <project>
                <parent>
                    <relativePath>../pom.xml</relativePath>
                </parent>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("25");
    }

    @Test
    void usesDefaultRelativePathForParent() throws IOException {
        parentPom(repository, "21");
        pom(repository.resolve("core"), """
            <project>
                <parent>
                    <artifactId>parent</artifactId>
                </parent>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("21");
    }

    @Test
    void resolvesRelativePathPointingToADirectory() throws IOException {
        parentPom(repository.resolve("build"), "17");
        pom(repository.resolve("core"), """
            <project>
                <parent>
                    <relativePath>../build</relativePath>
                </parent>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("17");
    }

    @Test
    void readsVersionFromGrandparent() throws IOException {
        parentPom(repository, "25");
        pom(repository.resolve("services"), """
            <project>
                <parent/>
            </project>
            """);
        pom(repository.resolve("services/api"), """
            <project>
                <parent/>
            </project>
            """);

        assertThat(detect("services/api")).isEqualTo("25");
    }

    @Test
    void prefersVersionOfTheModuleOverItsParent() throws IOException {
        parentPom(repository, "21");
        pom(repository.resolve("core"), """
            <project>
                <parent/>
                <properties>
                    <maven.compiler.release>25</maven.compiler.release>
                </properties>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("25");
    }

    @Test
    void skipsLocalLookupForEmptyRelativePath() throws IOException {
        parentPom(repository, "25");
        pom(repository.resolve("core"), """
            <project>
                <parent>
                    <relativePath/>
                </parent>
            </project>
            """);

        assertThatThrownBy(() -> detect("core"))
            .isInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageStartingWith("Could not determine Java version from");
    }

    @Test
    void failsWhenParentPomDoesNotExist() throws IOException {
        pom(repository.resolve("core"), """
            <project>
                <parent>
                    <relativePath>../missing/pom.xml</relativePath>
                </parent>
            </project>
            """);

        assertThatThrownBy(() -> detect("core"))
            .isInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageStartingWith("Could not determine Java version from");
    }

    @Test
    void ignoresPropertiesOutsideTheProjectProperties() throws IOException {
        parentPom(repository, "25");
        pom(repository.resolve("core"), """
            <project>
                <parent/>
                <profiles>
                    <profile>
                        <properties>
                            <maven.compiler.release>8</maven.compiler.release>
                        </properties>
                    </profile>
                </profiles>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("25");
    }

    @Test
    void stopsOnParentCycles() throws IOException {
        pom(repository.resolve("a"), """
            <project>
                <parent>
                    <relativePath>../b</relativePath>
                </parent>
            </project>
            """);
        pom(repository.resolve("b"), """
            <project>
                <parent>
                    <relativePath>../a</relativePath>
                </parent>
            </project>
            """);

        assertThatThrownBy(() -> detect("a"))
            .isInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageStartingWith("Could not determine Java version from");
    }

    @Test
    void resolvesPropertyReferences() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <maven.compiler.release>${jdk}</maven.compiler.release>
                    <jdk>21</jdk>
                </properties>
            </project>
            """);

        assertThat(detect(".")).isEqualTo("21");
    }

    @Test
    void resolvesReferencesToPropertiesOfTheParent() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <java.version>25</java.version>
                </properties>
            </project>
            """);
        pom(repository.resolve("core"), """
            <project>
                <parent/>
                <properties>
                    <maven.compiler.release>${java.version}</maven.compiler.release>
                </properties>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("25");
    }

    @Test
    void resolvesInheritedReferencesWithPropertiesOfTheModule() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <maven.compiler.release>${jdk}</maven.compiler.release>
                    <jdk>17</jdk>
                </properties>
            </project>
            """);
        pom(repository.resolve("core"), """
            <project>
                <parent/>
                <properties>
                    <jdk>21</jdk>
                </properties>
            </project>
            """);

        assertThat(detect("core")).isEqualTo("21");
    }

    @Test
    void resolvesNestedReferences() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <maven.compiler.release>${java.version}</maven.compiler.release>
                    <java.version>${jdk}</java.version>
                    <jdk>25</jdk>
                </properties>
            </project>
            """);

        assertThat(detect(".")).isEqualTo("25");
    }

    @Test
    void failsOnUndefinedReference() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <maven.compiler.release>${missing}</maven.compiler.release>
                </properties>
            </project>
            """);

        assertThatThrownBy(() -> detect("."))
            .isInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageStartingWith("Could not resolve ${missing}");
    }

    @Test
    void failsOnCircularReference() throws IOException {
        pom(repository, """
            <project>
                <properties>
                    <maven.compiler.release>${a}</maven.compiler.release>
                    <a>${b}</a>
                    <b>${a}</b>
                </properties>
            </project>
            """);

        assertThatThrownBy(() -> detect("."))
            .isInstanceOf(IllegalStateException.class)
            .rootCause()
            .hasMessageContaining("nested too deeply");
    }

    private String detect(final String path) {
        return detector.detect(repository, target(ScanTarget.Language.JAVA, path));
    }

    private static void parentPom(final Path directory, final String version) throws IOException {
        pom(directory, """
            <project>
                <properties>
                    <maven.compiler.release>%s</maven.compiler.release>
                </properties>
            </project>
            """.formatted(version));
    }

    private static void pom(final Path directory, final String content) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("pom.xml"), content);
    }

    private static ScanTarget target(
        final ScanTarget.Language language,
        final String path
    ) {
        final var target = new ScanTarget();
        target.setLanguage(language);
        target.setPath(path);

        return target;
    }
}
