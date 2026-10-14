package dev.graphnous.enhancer.fetch;

import dev.graphnous.core.model.Argument;
import dev.graphnous.core.model.Call;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Parameter;
import dev.graphnous.enhancer.WrittenUrl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What the calls of a scan target request with fetch: the url and method
 * of each fetch call, and of each call of a function that wraps fetch.
 * <p>
 * A function or method wraps fetch when what it requests depends on one of
 * its parameters, such as
 * <pre>
 * function apiFetch(path: string, init: RequestInit = {}) {
 *     return fetch(`${API_URL}${path}`, { ...init, headers });
 * }
 * </pre>
 * whose url is {@code {API_URL}{path}}. A call of it requests that url with
 * its arguments in place of the parameters: {@code apiFetch(`/orders/${id}`)}
 * requests {@code {API_URL}/orders/{id}}. Its method is likewise the one
 * of the options the call passes on, here as {@code init}. A function that
 * wraps such a function wraps fetch too.
 * <p>
 * The method of options written out is the one of their {@code method};
 * GET without one, or without options, as fetch has it. It is not known
 * when the options are a variable, spread one that is not a parameter, or
 * set the method to a variable.
 */
final class Requests {

    /**
     * How deep wrappers of wrappers are followed.
     */
    private static final int DEPTH = 5;

    private static final String GET = "GET";

    /**
     * The method of options written out, e.g. {@code method: "POST"}.
     */
    private static final Pattern METHOD = Pattern.compile("(?:^|[{,\\s])method\\s*:\\s*([\"'`])([A-Za-z]+)\\1");

    /**
     * A method that is set, but not to a string, e.g. {@code method: verb}
     * or {@code { method }}.
     */
    private static final Pattern ANY_METHOD = Pattern.compile("(?:^|[{,\\s])method\\s*[:,}]");

    /**
     * A spread of the options, e.g. {@code ...init}.
     */
    private static final Pattern SPREAD = Pattern.compile("\\.\\.\\.\\s*([A-Za-z_$][\\w$]*)");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][\\w$]*");

    /**
     * The functions and methods that wrap fetch, by qualified name.
     */
    private final Map<String, Wrapper> wrappers;

    private Requests(final Map<String, Wrapper> wrappers) {
        this.wrappers = wrappers;
    }

    /**
     * @param callables every function and method of the scan target
     */
    static Requests of(final List<Method> callables) {
        final var wrappers = new HashMap<String, Wrapper>();
        final var requests = new Requests(wrappers);

        // Wrappers of wrappers are found once what they wrap is
        for (int depth = 0; depth < DEPTH; depth++) {
            final var found = new HashMap<String, Wrapper>();

            for (final var callable : callables) {
                if (callable.getQualifiedName() == null || wrappers.containsKey(callable.getQualifiedName())) {
                    continue;
                }

                requests.wrapper(callable).ifPresent(wrapper -> found.put(callable.getQualifiedName(), wrapper));
            }

            if (found.isEmpty()) {
                break;
            }

            wrappers.putAll(found);
        }

        return requests;
    }

    /**
     * What the call requests, when it calls fetch or a function that wraps
     * it.
     */
    Optional<Request> of(final Call call) {
        if (FetchBrowserEnhancer.isFetch(call)) {
            return Optional.of(fetch(call));
        }

        final var wrapper = Boolean.TRUE.equals(call.getResolved()) && call.getKind() == Call.Kind.METHOD
            ? wrappers.get(call.getTarget())
            : null;

        return wrapper == null ? Optional.empty() : Optional.of(wrapper.request(call));
    }

    /**
     * The first request of the callable whose url or method depends on one
     * of its parameters, as what it wraps.
     */
    private Optional<Wrapper> wrapper(final Method callable) {
        final var parameters = callable.getParameters().stream()
            .map(Parameter::getName)
            .filter(Objects::nonNull)
            .toList();

        if (parameters.isEmpty()) {
            return Optional.empty();
        }

        for (final var call : callable.getCalls()) {
            final var request = of(call);

            if (request.isEmpty()) {
                continue;
            }

            final var url = request.get().url();
            final var urlDepends = url != null && parameters.stream().anyMatch(name -> url.contains("{" + name + "}"));
            final var methodDepends = parameters.contains(request.get().methodFrom());

            if (urlDepends || methodDepends) {
                return Optional.of(new Wrapper(parameters, request.get()));
            }
        }

        return Optional.empty();
    }

    private static Request fetch(final Call call) {
        final var arguments = call.getArguments();

        // Without arguments the scanner did not record them
        if (arguments.isEmpty()) {
            return new Request(null, null, null);
        }

        // fetch(...request) does not say what it requests
        if (isSpread(arguments.getFirst())) {
            return new Request(null, null, null);
        }

        final var url = WrittenUrl.of(arguments.getFirst().getValue());

        return arguments.size() < 2 ? new Request(url, GET, null) : options(url, arguments.get(1));
    }

    /**
     * The request of the url with the options, as fetch takes them.
     */
    private static Request options(
        final String url,
        final Argument options
    ) {
        if (isSpread(options)) {
            return new Request(url, null, null);
        }

        final var value = options.getValue().strip();

        if (IDENTIFIER.matcher(value).matches()) {
            // Options passed on, which may be a parameter of a wrapper
            return new Request(url, null, value);
        }

        if (!value.startsWith("{")) {
            return new Request(url, null, null);
        }

        final var method = METHOD.matcher(value);

        if (method.find()) {
            return new Request(url, method.group(2).toUpperCase(Locale.ROOT), null);
        }

        final var spread = SPREAD.matcher(value);

        if (spread.find()) {
            return new Request(url, null, spread.group(1));
        }

        // A method set to a variable, or options cut off before the end
        if (ANY_METHOD.matcher(value).find() || value.endsWith("…")) {
            return new Request(url, null, null);
        }

        return new Request(url, GET, null);
    }

    private static boolean isSpread(final Argument argument) {
        return Boolean.TRUE.equals(argument.getSpread());
    }

    /**
     * @param url        the url requested, with what is only known when it
     *                   runs in braces; null when it is not known
     * @param method     the HTTP method, e.g. {@code GET}; null when it is
     *                   not known
     * @param methodFrom the variable the options, and so the method, come
     *                   from, when they are passed on; null otherwise
     */
    record Request(String url, String method, String methodFrom) {
    }

    /**
     * A function that wraps fetch.
     *
     * @param parameters the names of its parameters, in order
     * @param request    what it requests, with its parameters in the url
     *                   and as where its options come from
     */
    private record Wrapper(List<String> parameters, Request request) {

        /**
         * What the call of the wrapper requests: its request with the
         * call's arguments in place of the parameters.
         */
        Request request(final Call call) {
            final var arguments = call.getArguments();

            // Without arguments the scanner did not record them
            if (arguments.isEmpty() && !parameters.isEmpty()) {
                return new Request(null, null, null);
            }

            var url = request.url();
            String method = request.method();
            String methodFrom = null;

            for (int i = 0; i < parameters.size(); i++) {
                final var parameter = parameters.get(i);

                // After a spread, which argument is which is not known
                final var spreadBefore = arguments.stream().limit(Math.min(i + 1, arguments.size())).anyMatch(Requests::isSpread);
                final var argument = i < arguments.size() && !spreadBefore ? arguments.get(i) : null;
                final var known = !spreadBefore;

                if (url != null && url.contains("{" + parameter + "}")) {
                    url = argument != null
                        ? url.replace("{" + parameter + "}", WrittenUrl.of(argument.getValue()))
                        : known ? url : null;
                }

                if (parameter.equals(request.methodFrom())) {
                    if (!known) {
                        method = null;
                    } else if (argument == null) {
                        // Options left out, as fetch then has them
                        method = GET;
                    } else {
                        final var passed = options(null, argument);
                        method = passed.method();
                        methodFrom = passed.methodFrom();
                    }
                }
            }

            return new Request(url, method, methodFrom);
        }
    }
}
