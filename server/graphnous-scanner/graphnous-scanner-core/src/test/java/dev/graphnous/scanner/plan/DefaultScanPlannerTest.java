package dev.graphnous.scanner.plan;

import dev.graphnous.scanner.language.LanguageVersionDetector;
import dev.graphnous.scanner.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultScanPlannerTest {

    private static final Path REPOSITORY = Path.of("repo");

    @Test
    void combinesTargetsFromAllDetectors() {
        final var backend = target(ScanTarget.Language.JAVA, "backend");
        final var frontend = target(ScanTarget.Language.TYPESCRIPT, "frontend");

        final var planner = new DefaultScanPlanner(
            List.of(
                repository -> List.of(backend),
                repository -> List.of(frontend)
            ),
            List.of()
        );

        assertThat(planner.plan(REPOSITORY).targets())
            .containsExactly(backend, frontend);
    }

    @Test
    void setsLanguageVersionFromTheFirstSupportingDetector() {
        final var backend = target(ScanTarget.Language.JAVA, "backend");
        final var frontend = target(ScanTarget.Language.TYPESCRIPT, "frontend");

        final var planner = new DefaultScanPlanner(
            List.of(repository -> List.of(backend, frontend)),
            List.of(
                versionDetector(ScanTarget.Language.JAVA, "25"),
                versionDetector(ScanTarget.Language.JAVA, "21"),
                versionDetector(ScanTarget.Language.TYPESCRIPT, "24")
            )
        );

        planner.plan(REPOSITORY);

        assertThat(backend.getLanguageVersion()).isEqualTo("25");
        assertThat(frontend.getLanguageVersion()).isEqualTo("24");
    }

    @Test
    void leavesLanguageVersionEmptyWithoutSupportingDetector() {
        final var backend = target(ScanTarget.Language.JAVA, "backend");

        final var planner = new DefaultScanPlanner(
            List.of(repository -> List.of(backend)),
            List.of(versionDetector(ScanTarget.Language.TYPESCRIPT, "24"))
        );

        planner.plan(REPOSITORY);

        assertThat(backend.getLanguageVersion()).isNull();
    }

    @Test
    void scansTargetsWithoutVersionWhenDetectionFails() {
        final var broken = target(ScanTarget.Language.JAVA, "legacy");
        final var fine = target(ScanTarget.Language.TYPESCRIPT, "web");

        final var planner = new DefaultScanPlanner(
            List.of(repository -> List.of(broken, fine)),
            List.of(
                new LanguageVersionDetector() {
                    @Override
                    public boolean supports(final ScanTarget target) {
                        return target.getLanguage() == ScanTarget.Language.JAVA;
                    }

                    @Override
                    public String detect(final Path repository, final ScanTarget target) {
                        throw new IllegalStateException(
                            "Failed to determine Java version from legacy/pom.xml",
                            new IllegalStateException("Could not resolve ${jdk} in legacy/pom.xml")
                        );
                    }
                },
                versionDetector(ScanTarget.Language.TYPESCRIPT, "24")
            )
        );

        final var plan = planner.plan(REPOSITORY);

        assertThat(plan.targets()).containsExactly(broken, fine);
        assertThat(broken.getLanguageVersion()).isNull();
        assertThat(fine.getLanguageVersion()).isEqualTo("24");

        assertThat(plan.warnings()).containsExactly(
            "legacy: could not determine the JAVA version, scanning without it"
            + " (Could not resolve ${jdk} in legacy/pom.xml)"
        );
    }

    @Test
    void hasNoWarningsWhenAllVersionsAreFound() {
        final var planner = new DefaultScanPlanner(
            List.of(repository -> List.of(target(ScanTarget.Language.JAVA, "."))),
            List.of(versionDetector(ScanTarget.Language.JAVA, "25"))
        );

        assertThat(planner.plan(REPOSITORY).warnings()).isEmpty();
    }

    @Test
    void passesTheRepositoryToDetectors() {
        final var planner = new DefaultScanPlanner(
            List.of(repository -> {
                assertThat(repository).isEqualTo(REPOSITORY);
                return List.of(target(ScanTarget.Language.JAVA, "."));
            }),
            List.of(new LanguageVersionDetector() {
                @Override
                public boolean supports(final ScanTarget target) {
                    return true;
                }

                @Override
                public String detect(final Path repository, final ScanTarget target) {
                    assertThat(repository).isEqualTo(REPOSITORY);
                    return "25";
                }
            })
        );

        assertThat(planner.plan(REPOSITORY).targets()).hasSize(1);
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

    private static LanguageVersionDetector versionDetector(
        final ScanTarget.Language language,
        final String version
    ) {
        return new LanguageVersionDetector() {
            @Override
            public boolean supports(final ScanTarget target) {
                return target.getLanguage() == language;
            }

            @Override
            public String detect(final Path repository, final ScanTarget target) {
                return version;
            }
        };
    }
}
