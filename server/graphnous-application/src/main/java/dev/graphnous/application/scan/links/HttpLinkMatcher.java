package dev.graphnous.application.scan.links;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds which HTTP call requests which endpoint, from their urls and
 * methods as enhancers wrote them: both with what varies in braces, such as
 * {@code /api/orders/{id}}.
 * <p>
 * A call is of an endpoint when their paths have the same segments:
 * <ul>
 *     <li>text is the same text;</li>
 *     <li>a variable of the endpoint is any one segment of the call;</li>
 *     <li>a variable of the call is only a variable of the endpoint: what
 *     is only known when it runs is not taken for a fixed path;</li>
 *     <li>the rest of the endpoint's path, {@code {*path}} or {@code **},
 *     is whatever is left of the call's.</li>
 * </ul>
 * At least one segment must be the same text, so a call of nothing but
 * variables is of no endpoint. The call's scheme and host, query and
 * fragment are left out. A call that starts with a variable, such as
 * {@code {API_URL}/orders}, has a base that may hold the start of the
 * path, so it is also of an endpoint whose path ends with the call's. A
 * variable right after the text of the call's last segment, as in
 * {@code /orders{query}}, is taken for its query: that segment is its text.
 * <p>
 * The methods must be the same, unless the call's is not known or the
 * endpoint takes any. Of the endpoints of one scan, a call is of those
 * that fit best: a whole path before the end of one, then the most
 * segments of the same text, so {@code /orders/latest} is of the endpoint
 * {@code /orders/latest} rather than of {@code /orders/{id}}.
 */
public final class HttpLinkMatcher {

    static final String ANY_METHOD = "ANY";

    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://");

    private HttpLinkMatcher() {
    }

    /**
     * Every link between the calls and the endpoints, in the order of the
     * calls.
     */
    public static List<HttpLinks.Link> match(
        final List<HttpLinks.Call> calls,
        final List<HttpLinks.Endpoint> endpoints
    ) {
        final var paths = new HashMap<String, List<Segment>>();
        final var links = new ArrayList<HttpLinks.Link>();

        for (final var call : calls) {
            final var path = CallPath.of(call.url());

            if (path == null) {
                continue;
            }

            // The best fit of each scan, and the endpoints that fit that well
            final var best = new HashMap<Object, Integer>();
            final var fits = new ArrayList<Fit>();

            for (final var endpoint : endpoints) {
                if (!sameMethod(call.httpMethod(), endpoint.httpMethod())) {
                    continue;
                }

                final var fit = fit(path, paths.computeIfAbsent(endpoint.url(), HttpLinkMatcher::endpointPath));

                if (fit != null) {
                    fits.add(new Fit(endpoint, fit.match(), fit.score()));
                    best.merge(endpoint.scanId(), fit.score(), Math::max);
                }
            }

            for (final var fit : fits) {
                if (fit.score() == best.get(fit.endpoint().scanId())) {
                    links.add(new HttpLinks.Link(call, fit.endpoint(), fit.match()));
                }
            }
        }

        return links;
    }

    private record Fit(HttpLinks.Endpoint endpoint, HttpLinks.Match match, int score) {
    }

    private record Scored(HttpLinks.Match match, int score) {
    }

    private static boolean sameMethod(
        final String call,
        final String endpoint
    ) {
        return call == null || endpoint == null || endpoint.equalsIgnoreCase(ANY_METHOD) || call.equalsIgnoreCase(endpoint);
    }

    /**
     * How well the call's path fits the endpoint's; null when it does not.
     * A whole path scores above any end of one.
     */
    private static Scored fit(
        final CallPath call,
        final List<Segment> endpoint
    ) {
        final var whole = same(call.segments(), endpoint, 0);

        if (whole > 0) {
            return new Scored(HttpLinks.Match.PATH, 1_000 + whole);
        }

        if (!call.hasBase()) {
            return null;
        }

        // What the base holds is text: a variable or the rest would fit anything
        for (int skipped = 1; skipped < endpoint.size(); skipped++) {
            if (endpoint.get(skipped - 1).kind() != Kind.TEXT) {
                break;
            }

            final var end = same(call.segments(), endpoint, skipped);

            if (end > 0) {
                return new Scored(HttpLinks.Match.SUFFIX, end);
            }
        }

        return null;
    }

    /**
     * How many segments of the call are the same text as the endpoint's
     * from the offset on, when all of them fit; 0 when they do not, or
     * when none is the same text.
     */
    private static int same(
        final List<Segment> call,
        final List<Segment> endpoint,
        final int offset
    ) {
        var score = 0;

        for (int i = 0; ; i++) {
            final var theirs = offset + i < endpoint.size() ? endpoint.get(offset + i) : null;

            if (theirs != null && theirs.kind() == Kind.REST) {
                return score;
            }

            final var ours = i < call.size() ? call.get(i) : null;

            if (ours == null || theirs == null) {
                return ours == null && theirs == null ? score : 0;
            }

            if (ours.kind() == Kind.TEXT && theirs.kind() == Kind.TEXT) {
                if (!ours.text().equals(theirs.text())) {
                    return 0;
                }

                score++;
            } else if (theirs.kind() == Kind.TEXT) {
                // A variable of the call is not taken for a fixed path
                return 0;
            }
        }
    }

    private enum Kind {
        TEXT,
        VARIABLE,
        REST
    }

    private record Segment(Kind kind, String text) {
    }

    /**
     * The path of a call.
     *
     * @param hasBase whether it started with a variable, which may hold the
     *                start of the path
     */
    private record CallPath(List<Segment> segments, boolean hasBase) {

        /**
         * The path of the url; null without one, or without any text in it.
         */
        static CallPath of(final String url) {
            if (url == null || url.isBlank()) {
                return null;
            }

            final var path = withoutQuery(url.strip());
            var hasBase = false;

            final var scheme = SCHEME.matcher(path);
            var parts = split(path);

            if (scheme.find()) {
                // Without the host, which is what comes before the path
                final var afterScheme = split(path.substring(scheme.end()));
                parts = afterScheme.subList(Math.min(1, afterScheme.size()), afterScheme.size());
            } else if (!path.startsWith("/") && !parts.isEmpty() && isVariable(parts.getFirst())) {
                parts = parts.subList(1, parts.size());
                hasBase = true;
            }

            if (!parts.isEmpty()) {
                parts = new ArrayList<>(parts);
                parts.set(parts.size() - 1, withoutQueryVariable(parts.getLast()));
            }

            final var segments = parts.stream()
                .map(part -> part.contains("{") ? new Segment(Kind.VARIABLE, part) : new Segment(Kind.TEXT, part))
                .toList();

            return segments.stream().anyMatch(segment -> segment.kind() == Kind.TEXT)
                ? new CallPath(segments, hasBase)
                : null;
        }

        /**
         * The text of a segment that variables follow, e.g. {@code orders}
         * for {@code orders{query}}; the segment itself otherwise.
         */
        private static String withoutQueryVariable(final String part) {
            final var brace = part.indexOf('{');

            if (brace <= 0 || !part.endsWith("}")) {
                return part;
            }

            // Only variables after the text
            var i = brace;

            while (i < part.length()) {
                if (part.charAt(i) != '{') {
                    return part;
                }

                var depth = 0;

                do {
                    final var c = part.charAt(i);
                    depth += c == '{' ? 1 : c == '}' ? -1 : 0;
                    i++;
                } while (depth > 0 && i < part.length());
            }

            return part.substring(0, brace);
        }

        private static boolean isVariable(final String part) {
            return part.startsWith("{") && part.endsWith("}");
        }
    }

    /**
     * The segments of an endpoint's path.
     */
    private static List<Segment> endpointPath(final String url) {
        if (url == null) {
            return List.of();
        }

        return split(withoutQuery(url.strip())).stream()
            .map(part -> {
                if (part.startsWith("{*") || part.equals("**")) {
                    return new Segment(Kind.REST, part);
                }

                return part.contains("{") || part.contains("*")
                    ? new Segment(Kind.VARIABLE, part)
                    : new Segment(Kind.TEXT, part);
            })
            .toList();
    }

    /**
     * The url up to its query or fragment; a {@code ?} within braces is
     * part of a variable's expression.
     */
    private static String withoutQuery(final String url) {
        var depth = 0;

        for (int i = 0; i < url.length(); i++) {
            final var c = url.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            } else if ((c == '?' || c == '#') && depth == 0) {
                return url.substring(0, i);
            }
        }

        return url;
    }

    /**
     * The parts between the slashes that are not within braces, without
     * the empty ones.
     */
    private static List<String> split(final String path) {
        final var parts = new ArrayList<String>();
        final var part = new StringBuilder();
        var depth = 0;

        for (int i = 0; i < path.length(); i++) {
            final var c = path.charAt(i);

            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
            }

            if (c == '/' && depth == 0) {
                if (!part.isEmpty()) {
                    parts.add(part.toString());
                    part.setLength(0);
                }
            } else {
                part.append(c);
            }
        }

        if (!part.isEmpty()) {
            parts.add(part.toString());
        }

        return parts;
    }
}
