package dev.graphnous.scanner.java.language;

import dev.graphnous.scanner.java.maven.MavenPom;
import dev.graphnous.scanner.language.LanguageVersionDetector;
import dev.graphnous.scanner.model.ScanTarget;

import java.nio.file.Path;
import java.util.List;

public class JavaVersionDetector implements LanguageVersionDetector {

    private static final List<String> VERSION_PROPERTIES = List.of(
        "java.version",
        "maven.compiler.release",
        "maven.compiler.source"
    );

    @Override
    public boolean supports(final ScanTarget target) {
        return target.getLanguage().equals(ScanTarget.Language.JAVA);
    }

    @Override
    public String detect(
        final Path repository,
        final ScanTarget target
    ) {
        final var pom = repository
            .resolve(target.getPath())
            .resolve("pom.xml");

        return detectFromPom(pom);
    }

    private String detectFromPom(final Path pom) {
        try {
            final var mavenPom = MavenPom.read(pom);

            final var version = mavenPom.firstProperty(VERSION_PROPERTIES)
                .orElseThrow(() -> new IllegalStateException(
                    "Could not determine Java version from " + pom
                ));

            return mavenPom.resolve(version);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to determine Java version from " + pom,
                e
            );
        }
    }
}
