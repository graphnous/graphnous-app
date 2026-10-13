package dev.graphnous.enhancer.fetch;

import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget.Language;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Enhancements;
import dev.graphnous.enhancer.Enhancer;
import dev.graphnous.enhancer.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds the HTTP calls made with the fetch API:
 * <pre>
 * (Method)-[:ENHANCE]->(HttpCall {client, url, startLine, startColumn, endLine, endColumn})
 * </pre>
 * The {@code ENHANCE} relationship comes with the node's source: the
 * method of a class, or the function of a file, that calls {@code fetch}.
 * <p>
 * A fetch call is a call of the global {@code fetch}, also as
 * {@code window.fetch}, {@code globalThis.fetch} or {@code self.fetch}, in
 * the browser or elsewhere. A function the target declares itself named
 * {@code fetch} is not one, as the scanner names it by its declaration.
 * So is a call of an expression that may be the global fetch, such as
 * {@code (this.fetchApi || fetch)(url)}, which the scanner names as
 * written; any of its alternatives, after {@code ||}, {@code ??} or in a
 * {@code ? :}, may be it.
 * Passing {@code fetch} on, as in {@code useSWR(url, fetch)}, is no call.
 * <p>
 * The url is the one the call's first argument gives, with the parts only
 * known when it runs as variables in braces, e.g. {@code /api/orders/{id}};
 * see {@link FetchUrl}. It is left out when the scanner did not record the
 * arguments. The call is identified by where it is.
 */
public class FetchBrowserEnhancer implements Enhancer {

    static final String NAME = "fetch-browser";
    static final String VERSION = "1.0.0";

    static final String CLIENT = "fetch";

    /**
     * The names a call of the global fetch is written as.
     */
    private static final List<String> FETCHES = List.of(
        "fetch",
        "window.fetch",
        "globalThis.fetch",
        "self.fetch"
    );

    /**
     * What separates the alternatives of a callee written as an expression,
     * e.g. {@code this.fetchApi || fetch}; the {@code ?} of optional
     * chaining, as in {@code this.config?.fetchApi}, is none.
     */
    private static final Pattern ALTERNATIVES = Pattern.compile("\\|\\||\\?\\?|\\?(?!\\.)|:");

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public Language forLanguage() {
        return Language.TYPESCRIPT;
    }

    @Override
    public Enhancements enhance(final ScanResult scanResult) {
        final var enhancements = new ArrayList<Enhancement>();
        final var targetId = scanResult.getTarget().getPath();

        for (final var module : scanResult.getModules()) {
            final var moduleId = targetId + "|" + module.getPath();

            for (final var file : module.getFiles()) {
                final var fileId = moduleId + "|file:" + file.getPath();

                for (final var type : file.getClasses()) {
                    addClass(enhancements, moduleId, type);
                }

                for (final var function : file.getFunctions()) {
                    add(enhancements, fileId + "|function:" + function.getQualifiedName(), function);
                }
            }
        }

        return new Enhancements(NAME, VERSION, enhancements);
    }

    /**
     * Adds the HTTP calls of the class's methods and of the classes nested
     * in it.
     */
    private static void addClass(
        final List<Enhancement> enhancements,
        final String moduleId,
        final Class type
    ) {
        final var classId = moduleId + "|class:" + type.getQualifiedName();

        for (final var method : type.getMethods()) {
            add(enhancements, classId + "|method:" + method.getQualifiedName(), method);
        }

        type.getClasses().forEach(nested -> addClass(enhancements, moduleId, nested));
    }

    /**
     * Adds the HTTP calls of the method, if it makes any.
     */
    private static void add(
        final List<Enhancement> enhancements,
        final String methodId,
        final Method method
    ) {
        final var nodes = method.getCalls()
            .stream()
            .filter(FetchBrowserEnhancer::isFetch)
            .map(call -> httpCall(methodId, call))
            .toList();

        if (!nodes.isEmpty()) {
            enhancements.add(new Enhancement(nodes, List.of()));
        }
    }

    static boolean isFetch(final Call call) {
        if (call.getKind() != Call.Kind.METHOD || call.getTarget() == null) {
            return false;
        }

        // A resolved call is of a declaration of the target, named by it
        if (!Boolean.FALSE.equals(call.getResolved())) {
            return FETCHES.contains(call.getTarget());
        }

        return ALTERNATIVES.splitAsStream(call.getTarget())
            .map(FetchBrowserEnhancer::withoutParentheses)
            .anyMatch(FETCHES::contains);
    }

    /**
     * The alternative without the parentheses that group it, e.g.
     * {@code fetch} for the {@code fetch)} of {@code (a ? b : fetch)};
     * the {@code ()} of a call, as in {@code fetch()}, stays.
     */
    private static String withoutParentheses(final String alternative) {
        var result = alternative.strip();

        while (result.startsWith("(")) {
            result = result.substring(1).strip();
        }

        while (result.endsWith(")") && !result.endsWith("()")) {
            result = result.substring(0, result.length() - 1).strip();
        }

        return result;
    }

    /**
     * The node of the call, identified within its method by where it starts.
     */
    private static Node httpCall(
        final String methodId,
        final Call call
    ) {
        // A null value leaves the property out
        final var metadata = new HashMap<String, Object>();
        metadata.put("client", CLIENT);

        if (!call.getArguments().isEmpty()) {
            metadata.put("url", FetchUrl.of(call.getArguments().getFirst()));
        }

        metadata.put("startLine", call.getStartLine());
        metadata.put("startColumn", call.getStartColumn());
        metadata.put("endLine", call.getEndLine());
        metadata.put("endColumn", call.getEndColumn());

        return new Node(
            methodId + "|httpCall:" + call.getStartLine() + ":" + call.getStartColumn(),
            methodId,
            List.of("HttpCall"),
            metadata
        );
    }
}
