package dev.graphnous.enhancer;

import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.core.model.ScanTarget.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnhancerProviderTest {

    private final List<Enhancer> ran = new ArrayList<>();

    @Test
    void returnsWhatEachEnhancerForTheTargetLanguageAdded() {
        final var controllers = enhancement("Endpoint");
        final var services = enhancement("Service");
        final var repositories = enhancement("Repository");

        final var spring = enhancer("spring", Language.JAVA, controllers, services);
        final var jpa = enhancer("jpa", Language.JAVA, repositories);
        final var react = enhancer("react", Language.TYPESCRIPT, enhancement("Component"));

        final var provider = new EnhancerProvider(new EnhancerRegistry(List.of(spring, react, jpa)));

        assertThat(provider.enhanceScan(scanResult(Language.JAVA))).containsExactly(
            new Enhancements("spring", "1.0.0", List.of(controllers, services)),
            new Enhancements("jpa", "1.0.0", List.of(repositories))
        );
        assertThat(ran).containsExactly(spring, jpa);
    }

    @Test
    void runsTheEnhancersForTheLanguagesOfTheFilesOnce() {
        final var java = enhancer("java", Language.JAVA);
        final var typescript = enhancer("typescript", Language.TYPESCRIPT);
        final var provider = new EnhancerProvider(new EnhancerRegistry(List.of(java, typescript)));

        provider.enhanceScan(scanResult(Language.JAVA, "java", "typescript", "TypeScript", "kotlin", null));

        assertThat(ran).containsExactly(java, typescript);
    }

    @Test
    void returnsNothingWithoutEnhancersForTheLanguage() {
        final var provider = new EnhancerProvider(new EnhancerRegistry(List.of(enhancer("python", Language.PYTHON))));

        assertThat(provider.enhanceScan(scanResult(Language.GO))).isEmpty();
        assertThat(ran).isEmpty();
    }

    private static Enhancement enhancement(final String label) {
        return new Enhancement(
            List.of(new Node("class|node", "class", List.of(label), Map.of())),
            List.of(new Relationship("class|node", "class", "DESCRIBES"))
        );
    }

    private Enhancer enhancer(final String name, final Language language, final Enhancement... enhancements) {
        return new Enhancer() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public Enhancements enhance(final ScanResult scanResult) {
                ran.add(this);
                return new Enhancements(name, "1.0.0", List.of(enhancements));
            }

            @Override
            public Language forLanguage() {
                return language;
            }
        };
    }

    private static ScanResult scanResult(final Language language, final String... fileLanguages) {
        final var target = new ScanTarget();
        target.setPath(".");
        target.setLanguage(language);

        final var module = new Module();

        for (final var fileLanguage : fileLanguages) {
            final var file = new File();
            file.setPath("src/file");
            file.setLanguage(fileLanguage);
            module.getFiles().add(file);
        }

        final var scanResult = new ScanResult();
        scanResult.setTarget(target);
        scanResult.getModules().add(module);

        return scanResult;
    }
}
