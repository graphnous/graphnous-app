package dev.graphnous.scanner.definition;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScannerFileTest {

    @Test
    void keepsACopyOfItsContent() {
        final var content = "<settings/>".getBytes(StandardCharsets.UTF_8);
        final var file = new ScannerFile("/maven-settings/settings.xml", content);

        content[0] = 'x';
        file.content()[1] = 'x';

        assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo("<settings/>");
    }

    @Test
    void leavesTheContentOutOfItsDescription() {
        assertThat(new ScannerFile("/maven-settings/settings.xml", "secret".getBytes(StandardCharsets.UTF_8)))
            .hasToString("ScannerFile[/maven-settings/settings.xml]");
    }

    @Test
    void rejectsATargetThatIsNotAnAbsoluteFileInADirectory() {
        for (final var target : new String[] {null, "settings.xml", "/settings.xml", "/maven-settings/"}) {
            assertThatThrownBy(() -> new ScannerFile(target, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an absolute file path in a directory");
        }
    }
}
