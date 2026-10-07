package dev.graphnous.persistence.scan.stats;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LanguagesConverterTest {

    private final LanguagesConverter converter = new LanguagesConverter();

    @Test
    void storesTheFilesOfEachLanguageAsJson() {
        final var languages = new LinkedHashMap<String, List<String>>();
        languages.put("JAVA", List.of("backend/src/main/java/Order.java"));
        languages.put("TYPESCRIPT", List.of("web/src/index.ts", "web/src/app.ts"));

        final var json = converter.convertToDatabaseColumn(languages);

        assertThat(json).isEqualTo(
            "{\"JAVA\":[\"backend/src/main/java/Order.java\"],\"TYPESCRIPT\":[\"web/src/index.ts\",\"web/src/app.ts\"]}"
        );
        assertThat(converter.convertToEntityAttribute(json)).isEqualTo(languages);
    }

    @Test
    void readsNoLanguagesFromAnEmptyColumn() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute("{}")).isEmpty();
    }
}
