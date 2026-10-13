package dev.graphnous.persistence.scan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a list of texts, in its order, as a JSON array, such as
 * {@code ["*.spec.ts", "generated/"]}; nothing for no list.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<ArrayList<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(final List<String> values) {
        return write(values);
    }

    @Override
    public List<String> convertToEntityAttribute(final String json) {
        return read(json);
    }

    public static String write(final List<String> values) {
        if (values == null) {
            return null;
        }

        try {
            return JSON.writeValueAsString(values);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to write a list as JSON", e);
        }
    }

    public static List<String> read(final String json) {
        if (json == null) {
            return null;
        }

        try {
            return JSON.readValue(json, TYPE);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to read a list from JSON", e);
        }
    }
}
