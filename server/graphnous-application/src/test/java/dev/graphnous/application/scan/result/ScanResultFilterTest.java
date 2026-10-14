package dev.graphnous.application.scan.result;

import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanResultFilterTest {

    @Test
    void leavesTheFilesOutWithWhatTheyDeclare() {
        final var result = result("web", ".", "src/order.ts", "src/order.spec.ts", "src/orders/list.spec.ts");

        assertThat(ScanResultFilter.exclude(List.of(result), List.of("*.spec.ts"))).isEqualTo(2);

        final var files = result.getModules().getFirst().getFiles();

        assertThat(files).extracting(File::getPath).containsExactly("src/order.ts");
        // The class of each file left out went with it
        assertThat(files).flatExtracting(File::getClasses).extracting(Class::getName).containsExactly("src/order.ts");
    }

    @Test
    void readsPathsFromTheRootOfTheRepository() {
        final var backend = result("backend", "orders", "src/main/java/Order.java", "src/test/java/OrderTest.java");
        final var web = result(".", ".", "src/test/setup.ts", "src/main.ts");

        assertThat(ScanResultFilter.exclude(List.of(backend, web), List.of("backend/orders/src/test"))).isEqualTo(1);

        assertThat(backend.getModules().getFirst().getFiles()).extracting(File::getPath).containsExactly("src/main/java/Order.java");
        // Not the same path from the root
        assertThat(web.getModules().getFirst().getFiles()).hasSize(2);

        assertThat(ScanResultFilter.exclude(List.of(web), List.of("/src/test"))).isEqualTo(1);
    }

    @Test
    void leavesNothingOutWithoutPatterns() {
        final var result = result("web", ".", "src/order.spec.ts");

        assertThat(ScanResultFilter.exclude(List.of(result), List.of())).isZero();
        assertThat(ScanResultFilter.exclude(List.of(result), null)).isZero();
        assertThat(ScanResultFilter.exclude(List.of(result), List.of(" "))).isZero();
        assertThat(result.getModules().getFirst().getFiles()).hasSize(1);
    }

    @Test
    void refusesAPatternThatIsNotWellFormed() {
        final var result = result("web", ".", "src/order.ts");

        assertThatThrownBy(() -> ScanResultFilter.exclude(List.of(result), List.of("src/{a")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * A result of one module, with a class in each file named as its path.
     */
    private static ScanResult result(final String target, final String module, final String... paths) {
        final var scanTarget = new ScanTarget();
        scanTarget.setPath(target);

        final var scanModule = new Module();
        scanModule.setPath(module);

        for (final var path : paths) {
            final var type = new Class();
            type.setName(path);

            final var file = new File();
            file.setPath(path);
            file.getClasses().add(type);

            scanModule.getFiles().add(file);
        }

        final var result = new ScanResult();
        result.setTarget(scanTarget);
        result.getModules().add(scanModule);

        return result;
    }
}
