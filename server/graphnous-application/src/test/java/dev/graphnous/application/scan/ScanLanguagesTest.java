package dev.graphnous.application.scan;

import dev.graphnous.application.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScanLanguagesTest {

    private final ScanLanguages languages = new ScanLanguages(List.of("TYPESCRIPT", "JAVA"));

    @Test
    void listsTheLanguagesThereAreScannersForInOrder() {
        assertThat(languages.available()).containsExactly("JAVA", "TYPESCRIPT");
    }

    @Test
    void keepsEachLanguageOnceInCapitals() {
        assertThat(languages.normalize(Arrays.asList(" java", "JAVA", "", null)))
            .containsExactly("JAVA");
    }

    @Test
    void scansEveryLanguageForNone() {
        assertThat(languages.normalize(null)).isEmpty();
        assertThat(languages.normalize(List.of())).isEmpty();
    }

    @Test
    void refusesALanguageWithoutAScanner() {
        assertThatThrownBy(() -> languages.normalize(List.of("JAVA", "python")))
            .isInstanceOf(ValidationException.class)
            .hasMessage("No scanner for PYTHON; there are scanners for JAVA, TYPESCRIPT");
    }

    @Test
    void dropsTheLanguagesKeptBeforeThatHaveNoScannerNow() {
        assertThat(languages.current(List.of("JAVA", "GO"))).containsExactly("JAVA");
        assertThat(languages.current(null)).isEmpty();
    }
}
