package dev.graphnous.enhancer.fetch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The url a fetch call requests, from its first argument as written: the
 * text of its strings, with what is only known when it runs written as a
 * variable in braces, as in Spring urls. For example
 * <ul>
 *     <li>{@code "/api/orders"} is {@code /api/orders}</li>
 *     <li>{@code `/api/orders/${id}`} is {@code /api/orders/{id}}</li>
 *     <li>{@code "/api/orders/" + order.id + "/lines"} is
 *     {@code /api/orders/{order.id}/lines}</li>
 *     <li>{@code url} is {@code {url}}</li>
 * </ul>
 * A variable is named by its expression as written, without the encoding
 * around it, so {@code ${encodeURIComponent(id)}} is {@code {id}}.
 */
final class FetchUrl {

    /**
     * Calls that only encode or convert the value they are given.
     */
    private static final Pattern ENCODING = Pattern.compile(
        "(?:encodeURIComponent|encodeURI|String)\\((.*)\\)"
    );

    private FetchUrl() {
    }

    static String of(final String argument) {
        final var url = new StringBuilder();

        for (final var part : split(argument.strip())) {
            if (isQuoted(part, '"') || isQuoted(part, '\'')) {
                url.append(unescape(part.substring(1, part.length() - 1)));
            } else if (isQuoted(part, '`')) {
                template(part, url);
            } else {
                url.append('{').append(variable(part)).append('}');
            }
        }

        return url.toString();
    }

    /**
     * The parts of a concatenation, e.g. {@code "/a/"}, {@code id} for
     * {@code "/a/" + id}; one part for anything else.
     */
    private static List<String> split(final String expression) {
        final var parts = new ArrayList<String>();
        var depth = 0;
        var start = 0;
        var i = 0;

        while (i < expression.length()) {
            final var c = expression.charAt(i);

            if (c == '"' || c == '\'' || c == '`') {
                i = skipString(expression, i);
                continue;
            }

            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == '+' && depth == 0) {
                parts.add(expression.substring(start, i).strip());
                start = i + 1;
            }

            i++;
        }

        parts.add(expression.substring(start).strip());

        return parts;
    }

    /**
     * Appends the template's text, with each {@code ${...}} as a variable.
     */
    private static void template(final String template, final StringBuilder url) {
        var i = 1;
        final var end = template.length() - 1;

        while (i < end) {
            final var c = template.charAt(i);

            if (c == '\\' && i + 1 < end) {
                url.append(template.charAt(i + 1));
                i += 2;
            } else if (c == '$' && i + 1 < end && template.charAt(i + 1) == '{') {
                final var close = skipBraces(template, i + 2);

                url.append('{').append(variable(template.substring(i + 2, close - 1))).append('}');
                i = close;
            } else {
                url.append(c);
                i++;
            }
        }
    }

    /**
     * The name of a variable part: its expression without the encoding
     * around it.
     */
    private static String variable(final String expression) {
        var name = expression.strip();

        while (true) {
            final var encoding = ENCODING.matcher(name);

            if (encoding.matches()) {
                name = encoding.group(1).strip();
            } else if (name.endsWith(".toString()")) {
                name = name.substring(0, name.length() - ".toString()".length()).strip();
            } else {
                return name;
            }
        }
    }

    private static boolean isQuoted(final String part, final char quote) {
        return part.length() >= 2
            && part.charAt(0) == quote
            && skipString(part, 0) == part.length();
    }

    /**
     * The index after the string, template or not, that starts at the index.
     */
    private static int skipString(final String text, final int start) {
        final var quote = text.charAt(start);
        var i = start + 1;

        while (i < text.length()) {
            final var c = text.charAt(i);

            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (quote == '`' && c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                i = skipBraces(text, i + 2);
            } else {
                i++;
            }
        }

        return text.length();
    }

    /**
     * The index after the brace that closes the one before the index.
     */
    private static int skipBraces(final String text, final int start) {
        var depth = 1;
        var i = start;

        while (i < text.length()) {
            final var c = text.charAt(i);

            if (c == '"' || c == '\'' || c == '`') {
                i = skipString(text, i);
                continue;
            }

            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i + 1;
            }

            i++;
        }

        return text.length();
    }

    private static String unescape(final String text) {
        return text.replaceAll("\\\\(.)", "$1");
    }
}
