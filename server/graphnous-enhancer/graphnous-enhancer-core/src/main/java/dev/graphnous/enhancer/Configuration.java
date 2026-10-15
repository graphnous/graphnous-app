package dev.graphnous.enhancer;

import dev.graphnous.core.model.ConfigurationProperty;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The configuration properties a module runs with, as the scanner recorded
 * them, to resolve the {@code ${key}} and {@code ${key:default}}
 * placeholders of Spring, such as {@code @RequestMapping("${api.prefix}/orders")}.
 * <p>
 * Only the default configuration counts, not that of a profile. The
 * properties of the module come first, then those of the other modules of
 * the scan result, as a library module runs with what an application
 * module sets. Of a key set twice, the one the scanner listed last counts,
 * as a file in a {@code config} directory overrides one beside it. Keys
 * are compared as Spring binds them, so {@code api.base-path} is
 * {@code api.basePath}.
 */
public final class Configuration {

    private static final Configuration NONE = new Configuration(Map.of());

    /**
     * The values by their keys as {@link #canonical}.
     */
    private final Map<String, String> values;

    private Configuration(final Map<String, String> values) {
        this.values = values;
    }

    /**
     * The configuration the module runs with, among the modules of the scan
     * result.
     */
    public static Configuration of(
        final ScanResult scanResult,
        final Module module
    ) {
        final var values = new HashMap<String, String>();

        // Those of the module last, so they count over the others
        Stream.concat(
                scanResult.getModules().stream().filter(other -> other != module),
                Stream.of(module)
            )
            .flatMap(each -> properties(each).stream())
            .filter(property -> property.getProfile() == null)
            .forEach(property -> values.put(canonical(property.getKey()), property.getValue()));

        return values.isEmpty() ? NONE : new Configuration(values);
    }

    private static List<ConfigurationProperty> properties(final Module module) {
        return module.getConfiguration() == null ? List.of() : module.getConfiguration();
    }

    /**
     * The value of the property, with its placeholders resolved as far as
     * they can be.
     */
    public Optional<String> get(final String key) {
        return Optional.ofNullable(values.get(canonical(key)))
            .map(value -> resolve(value, new HashSet<>(Set.of(canonical(key)))));
    }

    /**
     * The text with its placeholders replaced by the values of their keys,
     * or else by their defaults. A placeholder of a key that is not set and
     * has no default stays as written, e.g. {@code ${API_PREFIX}}.
     */
    public String resolve(final String text) {
        return resolve(text, new HashSet<>());
    }

    /**
     * @param resolving the keys being resolved, to stop at a cycle
     */
    private String resolve(
        final String text,
        final Set<String> resolving
    ) {
        if (text == null || !text.contains("${")) {
            return text;
        }

        final var result = new StringBuilder();
        var position = 0;

        while (position < text.length()) {
            final var start = text.indexOf("${", position);

            if (start < 0) {
                break;
            }

            final var end = closing(text, start + 2);

            if (end < 0) {
                break;
            }

            result.append(text, position, start);

            final var inner = text.substring(start + 2, end);
            final var colon = separator(inner);
            final var key = (colon < 0 ? inner : inner.substring(0, colon)).strip();
            final var canonical = canonical(key);
            final var value = values.get(canonical);

            if (value != null && resolving.add(canonical)) {
                result.append(resolve(value, resolving));
                resolving.remove(canonical);
            } else if (colon >= 0) {
                result.append(resolve(inner.substring(colon + 1), resolving));
            } else {
                result.append(text, start, end + 1);
            }

            position = end + 1;
        }

        return result.append(text.substring(position)).toString();
    }

    /**
     * The index of the brace that closes a placeholder whose key starts at
     * the index; -1 when none does. Braces within it, as in the default of
     * {@code ${item-path:/{id}}}, are nested, as Spring takes them.
     */
    private static int closing(
        final String text,
        final int start
    ) {
        var depth = 1;

        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '{') {
                depth++;
            } else if (text.charAt(i) == '}' && --depth == 0) {
                return i;
            }
        }

        return -1;
    }

    /**
     * The index of the colon between the key and the default of a
     * placeholder, outside nested placeholders; -1 when there is none.
     */
    private static int separator(final String inner) {
        var depth = 0;

        for (int i = 0; i < inner.length(); i++) {
            if (inner.charAt(i) == '{') {
                depth++;
            } else if (inner.charAt(i) == '}') {
                depth--;
            } else if (inner.charAt(i) == ':' && depth == 0) {
                return i;
            }
        }

        return -1;
    }

    /**
     * A key as Spring binds it: in lower case, without {@code -} and
     * {@code _}.
     */
    static String canonical(final String key) {
        return key.strip().toLowerCase(Locale.ROOT).replaceAll("[-_]", "");
    }
}
