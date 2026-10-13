package dev.graphnous.application.scan.result;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A pattern for the paths of files, written as in a {@code .gitignore}:
 * <ul>
 *     <li>{@code *} is any text within a folder or file name, {@code ?}
 *     one character of it;</li>
 *     <li>{@code **} is any number of folders, also none;</li>
 *     <li>{@code {a,b}} is either of them, and {@code [abc]} one of those
 *     characters;</li>
 *     <li>without a slash, it is of a name in any folder: {@code *.spec.ts}
 *     is every such file, {@code generated} every folder or file of that
 *     name;</li>
 *     <li>with one, it is of the path from the root of the repository:
 *     {@code src/generated}, which a slash in front also says;</li>
 *     <li>a folder it is of is everything below it too, which a slash at
 *     its end says of only folders.</li>
 * </ul>
 * Paths have forward slashes and no slash in front.
 */
public final class PathGlob {

    /**
     * How long a pattern can be.
     */
    public static final int MAX_LENGTH = 200;

    private final String pattern;
    private final Pattern regex;

    /**
     * Whether it is only of folders: it ended with a slash.
     */
    private final boolean foldersOnly;

    private PathGlob(
        final String pattern,
        final Pattern regex,
        final boolean foldersOnly
    ) {
        this.pattern = pattern;
        this.regex = regex;
        this.foldersOnly = foldersOnly;
    }

    /**
     * @throws IllegalArgumentException when the pattern is blank, too long
     *                                  or not well formed, such as a brace
     *                                  that is not closed
     */
    public static PathGlob of(final String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("A pattern must not be blank");
        }

        var glob = pattern.strip().replace('\\', '/');

        if (glob.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("A pattern must be at most " + MAX_LENGTH + " characters");
        }

        final var foldersOnly = glob.endsWith("/");

        if (foldersOnly) {
            glob = glob.substring(0, glob.length() - 1);
        }

        // Of the root with a slash anywhere but at its end, or of any folder
        final var anchored = glob.contains("/");

        if (glob.startsWith("/")) {
            glob = glob.substring(1);
        }

        if (glob.isEmpty()) {
            throw new IllegalArgumentException("Pattern " + pattern.strip() + " names nothing");
        }

        final var regex = (anchored ? "" : "(?:.*/)?") + regex(glob, pattern.strip());

        return new PathGlob(pattern.strip(), Pattern.compile(regex), foldersOnly);
    }

    /**
     * Whether the file at the path, or a folder it is in, fits the pattern.
     */
    public boolean matches(final String path) {
        if (path == null) {
            return false;
        }

        final var normalized = path.replace('\\', '/').replaceAll("^(\\./)+|^/+", "");

        if (!foldersOnly && regex.matcher(normalized).matches()) {
            return true;
        }

        // A folder that fits holds everything below it
        for (int slash = normalized.indexOf('/'); slash >= 0; slash = normalized.indexOf('/', slash + 1)) {
            if (regex.matcher(normalized.substring(0, slash)).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whether any of the patterns fits the path.
     */
    public static boolean anyMatches(final List<PathGlob> globs, final String path) {
        return globs.stream().anyMatch(glob -> glob.matches(path));
    }

    /**
     * The patterns as globs, without the blank ones.
     *
     * @throws IllegalArgumentException when one is not well formed
     */
    public static List<PathGlob> all(final List<String> patterns) {
        final var globs = new ArrayList<PathGlob>();

        for (final var pattern : patterns) {
            if (pattern != null && !pattern.isBlank()) {
                globs.add(of(pattern));
            }
        }

        return globs;
    }

    public String pattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return pattern;
    }

    private static String regex(final String glob, final String pattern) {
        final var regex = new StringBuilder();
        var braces = 0;

        for (int i = 0; i < glob.length(); i++) {
            final var c = glob.charAt(i);

            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        final var afterSlash = i == 0 || glob.charAt(i - 1) == '/';

                        // Skips the rest of the stars
                        while (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                            i++;
                        }

                        final var wholeSegment = afterSlash && (i + 1 == glob.length() || glob.charAt(i + 1) == '/');

                        if (wholeSegment && i + 1 < glob.length()) {
                            // Any number of folders, with the slash after them
                            regex.append("(?:.*/)?");
                            i++;
                        } else {
                            regex.append(".*");
                        }
                    } else {
                        regex.append("[^/]*");
                    }
                }
                case '?' -> regex.append("[^/]");
                case '{' -> {
                    braces++;
                    regex.append("(?:");
                }
                case '}' -> {
                    if (braces == 0) {
                        throw new IllegalArgumentException("Pattern " + pattern + " closes a brace it did not open");
                    }

                    braces--;
                    regex.append(')');
                }
                case ',' -> regex.append(braces > 0 ? "|" : ",");
                case '[' -> {
                    final var close = glob.indexOf(']', i + 2);

                    if (close < 0) {
                        throw new IllegalArgumentException("Pattern " + pattern + " does not close its bracket");
                    }

                    final var negated = glob.charAt(i + 1) == '!' || glob.charAt(i + 1) == '^';
                    final var characters = glob.substring(i + (negated ? 2 : 1), close);

                    regex.append(negated ? "[^/" : "[")
                        .append(characters.replace("\\", "\\\\").replace("[", "\\[").replace("^", "\\^"))
                        .append(']');
                    i = close;
                }
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }

        if (braces > 0) {
            throw new IllegalArgumentException("Pattern " + pattern + " does not close its brace");
        }

        return regex.toString();
    }
}
