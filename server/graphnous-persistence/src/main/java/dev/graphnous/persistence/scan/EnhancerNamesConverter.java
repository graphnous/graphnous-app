package dev.graphnous.persistence.scan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Stores the names of the enhancers chosen for a scan as a JSON array, such
 * as {@code ["react", "spring"]}, by name; nothing for a scan none were
 * chosen for, which is not the same as an empty choice.
 */
@Converter
public class EnhancerNamesConverter implements AttributeConverter<Set<String>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<LinkedHashSet<String>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(final Set<String> enhancers) {
        if (enhancers == null) {
            return null;
        }

        try {
            return JSON.writeValueAsString(new TreeSet<>(enhancers));
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to write the enhancers of a scan as JSON", e);
        }
    }

    @Override
    public Set<String> convertToEntityAttribute(final String json) {
        if (json == null) {
            return null;
        }

        try {
            return JSON.readValue(json, TYPE);
        } catch (final JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to read the enhancers of a scan from JSON", e);
        }
    }
}
