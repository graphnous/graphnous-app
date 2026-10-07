package dev.graphnous.persistence.scan.stats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the files of each language as a JSON object of arrays, such as
 * {@code {"JAVA": ["backend/src/main/java/Order.java"]}}.
 */
@Converter
public class LanguagesConverter implements AttributeConverter<Map<String, List<String>>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, List<String>>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(final Map<String, List<String>> languages) {
        try {
            return JSON.writeValueAsString(languages == null ? Map.of() : languages);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to write the languages of scan stats as JSON", e);
        }
    }

    @Override
    public Map<String, List<String>> convertToEntityAttribute(final String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return JSON.readValue(json, TYPE);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to read the languages of scan stats from JSON", e);
        }
    }
}
