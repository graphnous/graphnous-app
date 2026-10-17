package dev.graphnous.application.scan.source;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where what a scan found is in its repository. A node's id names the scan
 * target and the module it is in, as {@code scanId|target|module|...}; the
 * path its file was stored with is relative to the target, or to the
 * module; and a project can be a directory of its repository.
 */
public final class SourcePaths {

    /**
     * The directories test code is in, by convention.
     */
    private static final Set<String> TEST_DIRECTORIES = Set.of("test", "tests", "__tests__", "__test__", "testFixtures");

    /**
     * The names of test files, by convention: OrderServiceTest.java,
     * OrderServiceIT.java, orders.spec.ts, orders.test.tsx.
     */
    private static final Pattern TEST_FILE = Pattern.compile(
        ".*(Tests?|IT)\\.(java|kt)|.*\\.(spec|test)\\.[cm]?[jt]sx?"
    );

    private SourcePaths() {
    }

    /**
     * The file's path in the project: its target's and module's directories
     * and its own path, as far as the node's id says them; the file as it
     * is otherwise.
     *
     * @param nodeId the id of what the file declares, such as a method
     * @param file   the file's path as the scan stored it
     */
    public static String inProject(final String nodeId, final String file) {
        if (file == null) {
            return null;
        }

        final var parts = nodeId == null ? new String[0] : nodeId.split("\\|");

        if (parts.length < 4) {
            return normalize(file);
        }

        final var target = parts[1];
        final var module = normalize(parts[2]);
        final var path = normalize(file);

        final var inTarget = module.isEmpty() || path.equals(module) || path.startsWith(module + "/")
            ? path
            : module + "/" + path;

        return join(target, inTarget);
    }

    /**
     * @param projectPath the project's directory in its repository; null or
     *                    empty for its root
     */
    public static String inRepository(final String projectPath, final String nodeId, final String file) {
        final var inProject = inProject(nodeId, file);

        return inProject == null ? null : join(projectPath, inProject);
    }

    /**
     * Whether the path is of test code, by the conventions of Maven, Gradle
     * and JavaScript tools: in a test directory, or named as a test.
     */
    public static boolean isTest(final String path) {
        if (path == null) {
            return false;
        }

        final var segments = normalize(path).split("/");

        for (var i = 0; i < segments.length - 1; i++) {
            if (TEST_DIRECTORIES.contains(segments[i])) {
                return true;
            }
        }

        return TEST_FILE.matcher(segments[segments.length - 1]).matches();
    }

    private static String join(final String... parts) {
        return normalize(String.join("/", Arrays.stream(parts).map(part -> part == null ? "" : part).toList()));
    }

    /**
     * Forward slashes, without empty, {@code .} or leading and trailing
     * parts.
     */
    static String normalize(final String path) {
        if (path == null) {
            return "";
        }

        return String.join("/", Arrays.stream(path.replace('\\', '/').split("/"))
            .filter(part -> !part.isEmpty() && !part.equals("."))
            .toList());
    }
}
