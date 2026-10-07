package dev.graphnous.enhancer.spring;

import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Arguments;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.Parameter;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.core.model.TypeRef;
import dev.graphnous.enhancer.Enhancement;
import dev.graphnous.enhancer.Node;
import dev.graphnous.enhancer.Relationship;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SpringEnhancerTest {

    private static final String WEB = "org.springframework.web.bind.annotation.";

    private static final String CLASS = "backend|orders|class:com.example.OrderController";
    private static final String CONTROLLER = CLASS + "|controller";

    private final SpringEnhancer enhancer = new SpringEnhancer();

    @Test
    void enhancesJava() {
        assertThat(enhancer.forLanguage()).isEqualTo(ScanTarget.Language.JAVA);
    }

    @Test
    void namesItsEnhancements() {
        final var enhancements = enhancer.enhance(scanResult(controller(annotation(WEB + "RestController", Map.of()))));

        assertThat(enhancer.name()).isEqualTo("spring");
        assertThat(enhancer.version()).isEqualTo("1.0.0");
        assertThat(enhancements.name()).isEqualTo("spring");
        assertThat(enhancements.version()).isEqualTo("1.0.0");
    }

    @Test
    void addsAControllerForItsClass() {
        final var enhancement = enhance(controller(annotation("org.springframework.stereotype.Controller", Map.of())));

        assertThat(enhancement.nodes()).containsExactly(
            new Node(CONTROLLER, CLASS, List.of("Controller"), Map.of("baseUrl", ""))
        );
        assertThat(enhancement.relationships()).isEmpty();
    }

    @Test
    void takesTheBaseUrlFromTheRequestMappingOfTheController() {
        final var enhancement = enhance(controller(
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("value", "/api/orders"))
        ));

        assertThat(enhancement.nodes().getFirst().metadata()).containsEntry("baseUrl", "/api/orders");
    }

    @Test
    void addsAnEndpointForEachMappedMethod() {
        final var enhancement = enhance(controller(
            List.of(
                method("list()", annotation(WEB + "GetMapping", Map.of())),
                method("find(java.lang.String)", annotation(WEB + "GetMapping", Map.of("value", "{id}"))),
                method("create(com.example.Order)", annotation(WEB + "PostMapping", Map.of())),
                method("replace(java.lang.String)", annotation(WEB + "PutMapping", Map.of("path", "/{id}"))),
                method("change(java.lang.String)", annotation(WEB + "PatchMapping", Map.of("path", List.of("/{id}")))),
                method("delete(java.lang.String)", annotation(WEB + "DeleteMapping", Map.of("value", "/{id}"))),
                method("toString()")
            ),
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("path", List.of("/orders")))
        ));

        assertThat(enhancement.nodes())
            .filteredOn(node -> node.labels().equals(List.of("Endpoint")))
            .extracting(node -> node.metadata().get("httpMethod"), node -> node.metadata().get("url"))
            .containsExactly(
                tuple("GET", "/orders"),
                tuple("GET", "/orders/{id}"),
                tuple("POST", "/orders"),
                tuple("PUT", "/orders/{id}"),
                tuple("PATCH", "/orders/{id}"),
                tuple("DELETE", "/orders/{id}")
            );
    }

    @Test
    void connectsAnEndpointToItsControllerForItsMethod() {
        final var enhancement = enhance(controller(
            List.of(method("find(java.lang.String)", annotation(WEB + "GetMapping", Map.of("value", "/{id}")))),
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("value", "/orders"))
        ));

        final var method = CLASS + "|method:com.example.OrderController.find(java.lang.String)";
        final var endpoint = method + "|endpoint:GET /orders/{id}";

        assertThat(enhancement.nodes()).contains(
            new Node(endpoint, method, List.of("Endpoint"), Map.of("httpMethod", "GET", "url", "/orders/{id}"))
        );
        assertThat(enhancement.relationships()).containsExactly(
            new Relationship(CONTROLLER, endpoint, "HAS_ENDPOINT")
        );
    }

    @Test
    void takesTheHttpMethodsOfARequestMapping() {
        final var enhancement = enhance(controller(
            List.of(
                method("search()", annotation(WEB + "RequestMapping", Map.of(
                    "path", "/search",
                    "method", List.of(WEB + "RequestMethod.GET", WEB + "RequestMethod.POST")
                ))),
                method("any()", annotation(WEB + "RequestMapping", Map.of("value", "/any")))
            ),
            annotation("org.springframework.stereotype.Controller", Map.of())
        ));

        assertThat(endpoints(enhancement)).containsExactly(
            tuple("GET", "/search"),
            tuple("POST", "/search"),
            tuple(SpringEnhancer.ANY_METHOD, "/any")
        );
    }

    @Test
    void addsAServiceForItsClass() {
        final var service = new Class();
        service.setName("OrderService");
        service.setQualifiedName("com.example.OrderService");
        service.getAnnotations().add(annotation("org.springframework.stereotype.Service", Map.of()));
        // Not an endpoint, as a service is no controller
        service.getMethods().add(method("list()", annotation(WEB + "GetMapping", Map.of())));

        final var serviceClass = "backend|orders|class:com.example.OrderService";

        assertThat(enhance(service).nodes()).containsExactly(
            new Node(serviceClass + "|service", serviceClass, List.of("Service"), Map.of())
        );
        assertThat(enhance(service).relationships()).isEmpty();
    }

    @Test
    void recognisesServicesTheScannerCouldNotResolve() {
        final var unresolved = new Annotation();
        unresolved.setName("Service");

        final var service = new Class();
        service.setName("OrderService");
        service.setQualifiedName("com.example.OrderService");
        service.getAnnotations().add(unresolved);

        assertThat(enhance(service).nodes()).extracting(Node::labels).containsExactly(List.of("Service"));
    }

    @Test
    void addsARepositoryForItsClass() {
        final var repository = new Class();
        repository.setName("OrderRepository");
        repository.setQualifiedName("com.example.OrderRepository");
        repository.getAnnotations().add(annotation("org.springframework.stereotype.Repository", Map.of()));

        final var repositoryClass = "backend|orders|class:com.example.OrderRepository";

        assertThat(enhance(repository).nodes()).containsExactly(
            new Node(repositoryClass + "|repository", repositoryClass, List.of("Repository"), Map.of())
        );
        assertThat(enhance(repository).relationships()).isEmpty();
    }

    @Test
    void recognisesRepositoriesTheScannerCouldNotResolve() {
        final var unresolved = new Annotation();
        unresolved.setName("Repository");

        final var repository = new Class();
        repository.setName("OrderRepository");
        repository.setQualifiedName("com.example.OrderRepository");
        repository.getAnnotations().add(unresolved);

        assertThat(enhance(repository).nodes()).extracting(Node::labels).containsExactly(List.of("Repository"));
    }

    @Test
    void addsAComponentForItsClass() {
        final var component = new Class();
        component.setName("OrderMapper");
        component.setQualifiedName("com.example.OrderMapper");
        component.getAnnotations().add(annotation("org.springframework.stereotype.Component", Map.of()));

        final var componentClass = "backend|orders|class:com.example.OrderMapper";

        assertThat(enhance(component).nodes()).containsExactly(
            new Node(componentClass + "|component", componentClass, List.of("Component"), Map.of())
        );
        assertThat(enhance(component).relationships()).isEmpty();
    }

    @Test
    void addsAnApplicationForItsClass() {
        final var application = new Class();
        application.setName("OrderApplication");
        application.setQualifiedName("com.example.OrderApplication");
        application.getAnnotations().add(annotation("org.springframework.boot.autoconfigure.SpringBootApplication", Map.of()));

        final var applicationClass = "backend|orders|class:com.example.OrderApplication";

        assertThat(enhance(application).nodes()).containsExactly(
            new Node(applicationClass + "|application", applicationClass, List.of("Application"), Map.of())
        );
    }

    @Test
    void addsAConfigurationForItsClass() {
        final var configuration = new Class();
        configuration.setName("OrderConfiguration");
        configuration.setQualifiedName("com.example.OrderConfiguration");
        configuration.getAnnotations().add(annotation("org.springframework.context.annotation.Configuration", Map.of()));

        final var configurationClass = "backend|orders|class:com.example.OrderConfiguration";

        assertThat(enhance(configuration).nodes()).containsExactly(
            new Node(configurationClass + "|configuration", configurationClass, List.of("Configuration"), Map.of())
        );
    }

    @Test
    void ignoresClassesWithOtherAnnotations() {
        // Annotated with @Component itself, which the scan result does not tell
        final var advice = new Class();
        advice.setName("OrderAdvice");
        advice.setQualifiedName("com.example.OrderAdvice");
        advice.getAnnotations().add(annotation(WEB + "ControllerAdvice", Map.of()));
        advice.getMethods().add(method("list()", annotation(WEB + "GetMapping", Map.of())));

        assertThat(enhancer.enhance(scanResult(advice)).enhancements()).isEmpty();
    }

    @Test
    void recognisesControllersTheScannerCouldNotResolve() {
        final var unresolved = new Annotation();
        unresolved.setName("RestController");

        assertThat(enhance(controller(unresolved)).nodes()).hasSize(1);
    }

    @Test
    void findsNestedControllers() {
        final var outer = new Class();
        outer.setName("Api");
        outer.setQualifiedName("com.example.Api");
        outer.getClasses().add(controller(annotation(WEB + "RestController", Map.of())));

        assertThat(enhancer.enhance(scanResult(outer)).enhancements()).hasSize(1);
    }

    @Test
    void addsTheQueryParametersOfAnEndpoint() {
        final var list = method(
            "list(java.lang.String,int,java.util.Optional,java.lang.String,java.lang.String)",
            annotation(WEB + "GetMapping", Map.of())
        );
        list.setParameters(List.of(
            parameter("status", "java.lang.String", annotation(WEB + "RequestParam", Map.of())),
            parameter("page", "int", annotation(WEB + "RequestParam", Map.of("name", "p", "defaultValue", "0"))),
            parameter("sort", "java.util.Optional<java.lang.String>", annotation(WEB + "RequestParam", Map.of("value", "sort"))),
            parameter("filter", "java.lang.String", annotation(WEB + "RequestParam", Map.of("required", false))),
            // Not a query parameter
            parameter("tenant", "java.lang.String", annotation(WEB + "RequestHeader", Map.of()))
        ));

        final var enhancement = enhance(controller(
            List.of(list),
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("value", "/orders"))
        ));

        final var method = CLASS + "|method:com.example.OrderController." + "list(java.lang.String,int,java.util.Optional,java.lang.String,java.lang.String)";

        assertThat(enhancement.nodes())
            .filteredOn(node -> node.labels().equals(List.of("QueryParameter")))
            .containsExactly(
                queryParameter(method, "status", "java.lang.String", null, false),
                queryParameter(method, "p", "int", "0", true),
                queryParameter(method, "sort", "java.util.Optional<java.lang.String>", null, true),
                queryParameter(method, "filter", "java.lang.String", null, true)
            );
        assertThat(enhancement.relationships())
            .filteredOn(relationship -> relationship.type().equals("HAS_QUERY_PARAMETER"))
            .extracting(Relationship::sourceId, Relationship::targetId)
            .containsExactly(
                tuple(method + "|endpoint:GET /orders", method + "|queryParameter:status"),
                tuple(method + "|endpoint:GET /orders", method + "|queryParameter:p"),
                tuple(method + "|endpoint:GET /orders", method + "|queryParameter:sort"),
                tuple(method + "|endpoint:GET /orders", method + "|queryParameter:filter")
            );
    }

    @Test
    void sharesTheQueryParametersOfAMethodBetweenItsEndpoints() {
        final var search = method("search(java.lang.String)", annotation(WEB + "RequestMapping", Map.of(
            "path", "/search",
            "method", List.of(WEB + "RequestMethod.GET", WEB + "RequestMethod.POST")
        )));
        search.setParameters(List.of(parameter("q", "java.lang.String", annotation(WEB + "RequestParam", Map.of()))));

        final var enhancement = enhance(controller(List.of(search), annotation(WEB + "RestController", Map.of())));

        assertThat(enhancement.nodes()).filteredOn(node -> node.labels().equals(List.of("QueryParameter"))).hasSize(1);
        assertThat(enhancement.relationships())
            .filteredOn(relationship -> relationship.type().equals("HAS_QUERY_PARAMETER"))
            .hasSize(2);
    }

    @Test
    void addsThePathVariablesOfAnEndpoint() {
        final var find = method(
            "find(java.lang.String,java.lang.String,java.util.Optional)",
            annotation(WEB + "GetMapping", Map.of("value", "/{tenant}/{id}/{version}"))
        );
        find.setParameters(List.of(
            parameter("tenant", "java.lang.String", annotation(WEB + "PathVariable", Map.of("value", "tenant"))),
            parameter("orderId", "java.lang.String", annotation(WEB + "PathVariable", Map.of("name", "id"))),
            parameter("version", "java.util.Optional<java.lang.Integer>", annotation(WEB + "PathVariable", Map.of("required", false))),
            parameter("all", "java.util.Map<java.lang.String, java.lang.String>", annotation(WEB + "PathVariable", Map.of()))
        ));

        final var enhancement = enhance(controller(
            List.of(find),
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("value", "/orders"))
        ));

        final var method = CLASS + "|method:com.example.OrderController.find(java.lang.String,java.lang.String,java.util.Optional)";
        final var endpoint = method + "|endpoint:GET /orders/{tenant}/{id}/{version}";

        assertThat(enhancement.nodes())
            .filteredOn(node -> node.labels().equals(List.of("PathVariable")))
            .containsExactly(
                pathVariable(method, "tenant", "java.lang.String", false),
                pathVariable(method, "id", "java.lang.String", false),
                pathVariable(method, "version", "java.util.Optional<java.lang.Integer>", true)
            );
        assertThat(enhancement.relationships())
            .filteredOn(relationship -> relationship.type().equals("HAS_PATH_VARIABLE"))
            .extracting(Relationship::sourceId, Relationship::targetId)
            .containsExactly(
                tuple(endpoint, method + "|pathVariable:tenant"),
                tuple(endpoint, method + "|pathVariable:id"),
                tuple(endpoint, method + "|pathVariable:version")
            );
    }

    @Test
    void addsTheRequestBodyOfAnEndpoint() {
        final var create = method("create(com.example.Order)", annotation(WEB + "PostMapping", Map.of()));
        create.setParameters(List.of(
            parameter("order", "com.example.Order", annotation(WEB + "RequestBody", Map.of()))
        ));

        final var enhancement = enhance(controller(
            List.of(create),
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("value", "/orders"))
        ));

        final var method = CLASS + "|method:com.example.OrderController.create(com.example.Order)";

        assertThat(enhancement.nodes())
            .filteredOn(node -> node.labels().equals(List.of("RequestBody")))
            .containsExactly(new Node(method + "|requestBody:order", method, List.of("RequestBody"), Map.of(
                "name", "order",
                "type", "com.example.Order",
                "optional", false
            )));
        assertThat(enhancement.relationships())
            .filteredOn(relationship -> relationship.type().equals("HAS_REQUEST_BODY"))
            .extracting(Relationship::sourceId, Relationship::targetId)
            .containsExactly(tuple(method + "|endpoint:POST /orders", method + "|requestBody:order"));
    }

    @Test
    void addsAnOptionalRequestBodyAndOneOfAMap() {
        final var notRequired = method("patch(com.example.Order)", annotation(WEB + "PatchMapping", Map.of("value", "/a")));
        notRequired.setParameters(List.of(parameter("order", "com.example.Order", annotation(WEB + "RequestBody", Map.of("required", false)))));

        final var optional = method("replace(java.util.Optional)", annotation(WEB + "PutMapping", Map.of("value", "/b")));
        optional.setParameters(List.of(parameter("order", "java.util.Optional<com.example.Order>", annotation(WEB + "RequestBody", Map.of()))));

        // A map is a JSON object, not every part of the request
        final var map = method("merge(java.util.Map)", annotation(WEB + "PostMapping", Map.of("value", "/c")));
        map.setParameters(List.of(parameter("fields", "java.util.Map<java.lang.String, java.lang.Object>", annotation(WEB + "RequestBody", Map.of()))));

        final var enhancement = enhance(controller(List.of(notRequired, optional, map), annotation(WEB + "RestController", Map.of())));

        assertThat(enhancement.nodes())
            .filteredOn(node -> node.labels().equals(List.of("RequestBody")))
            .extracting(node -> node.metadata().get("name"), node -> node.metadata().get("optional"))
            .containsExactly(tuple("order", true), tuple("order", true), tuple("fields", false));
    }

    @Test
    void tellsQueryParametersAndPathVariablesOfOneMethodApart() {
        final var find = method("find(java.lang.String,java.lang.String)", annotation(WEB + "GetMapping", Map.of("value", "/{id}")));
        find.setParameters(List.of(
            parameter("id", "java.lang.String", annotation(WEB + "PathVariable", Map.of())),
            parameter("expand", "java.lang.String", annotation(WEB + "RequestParam", Map.of("defaultValue", "none")))
        ));

        final var enhancement = enhance(controller(List.of(find), annotation(WEB + "RestController", Map.of())));

        assertThat(enhancement.nodes())
            .extracting(node -> node.labels().getFirst(), node -> node.metadata().get("name"))
            .contains(tuple("PathVariable", "id"), tuple("QueryParameter", "expand"));
        assertThat(enhancement.relationships())
            .extracting(Relationship::type)
            .containsExactly("HAS_ENDPOINT", "HAS_PATH_VARIABLE", "HAS_QUERY_PARAMETER");
    }

    @Test
    void addsNoQueryParameterForAMapOfAllOfThem() {
        final var list = method("list(java.util.Map)", annotation(WEB + "GetMapping", Map.of()));
        list.setParameters(List.of(
            parameter("all", "java.util.Map<java.lang.String, java.lang.String>", annotation(WEB + "RequestParam", Map.of()))
        ));

        final var enhancement = enhance(controller(List.of(list), annotation(WEB + "RestController", Map.of())));

        assertThat(enhancement.nodes()).noneMatch(node -> node.labels().equals(List.of("QueryParameter")));
    }

    @Test
    void addsNoQueryParametersForMethodsThatAreNoEndpoints() {
        final var helper = method("helper(java.lang.String)");
        helper.setParameters(List.of(parameter("q", "java.lang.String", annotation(WEB + "RequestParam", Map.of()))));

        final var enhancement = enhance(controller(List.of(helper), annotation(WEB + "RestController", Map.of())));

        assertThat(enhancement.nodes()).extracting(Node::labels).containsExactly(List.of("Controller"));
    }

    @Test
    void joinsUrlsWithOneSlash() {
        assertThat(SpringEnhancer.url("", "")).isEqualTo("/");
        assertThat(SpringEnhancer.url("/orders/", "/{id}")).isEqualTo("/orders/{id}");
        assertThat(SpringEnhancer.url("orders", "{id}/")).isEqualTo("/orders/{id}");
        assertThat(SpringEnhancer.url("/orders", "")).isEqualTo("/orders");
    }

    private Enhancement enhance(final Class controller) {
        final var enhancements = enhancer.enhance(scanResult(controller)).enhancements();

        assertThat(enhancements).hasSize(1);

        return enhancements.getFirst();
    }

    private static List<Tuple> endpoints(final Enhancement enhancement) {
        return enhancement.nodes()
            .stream()
            .filter(node -> node.labels().equals(List.of("Endpoint")))
            .map(node -> tuple(node.metadata().get("httpMethod"), node.metadata().get("url")))
            .toList();
    }

    private static Class controller(final Annotation... annotations) {
        return controller(List.of(), annotations);
    }

    private static Class controller(final List<Method> methods, final Annotation... annotations) {
        final var type = new Class();
        type.setName("OrderController");
        type.setQualifiedName("com.example.OrderController");
        type.setAnnotations(List.of(annotations));
        type.setMethods(methods);

        return type;
    }

    private static Method method(final String signature, final Annotation... annotations) {
        final var method = new Method();
        method.setName(signature.substring(0, signature.indexOf('(')));
        method.setQualifiedName("com.example.OrderController." + signature);
        method.setAnnotations(List.of(annotations));

        return method;
    }

    private static Parameter parameter(final String name, final String type, final Annotation annotation) {
        final var typeRef = new TypeRef();
        typeRef.setName(type);

        final var parameter = new Parameter();
        parameter.setName(name);
        parameter.setType(typeRef);
        parameter.setAnnotations(List.of(annotation));

        return parameter;
    }

    private static Node queryParameter(
        final String methodId,
        final String name,
        final String type,
        final String defaultValue,
        final boolean optional
    ) {
        final var metadata = new HashMap<String, Object>();
        metadata.put("name", name);
        metadata.put("type", type);
        metadata.put("defaultValue", defaultValue);
        metadata.put("optional", optional);

        return new Node(methodId + "|queryParameter:" + name, methodId, List.of("QueryParameter"), metadata);
    }

    private static Node pathVariable(
        final String methodId,
        final String name,
        final String type,
        final boolean optional
    ) {
        return new Node(methodId + "|pathVariable:" + name, methodId, List.of("PathVariable"), Map.of(
            "name", name,
            "type", type,
            "optional", optional
        ));
    }

    private static Annotation annotation(final String qualifiedName, final Map<String, Object> arguments) {
        final var annotation = new Annotation();
        annotation.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        annotation.setQualifiedName(qualifiedName);

        if (!arguments.isEmpty()) {
            final var values = new Arguments();
            arguments.forEach(values::setAdditionalProperty);
            annotation.setArguments(values);
        }

        return annotation;
    }

    private static ScanResult scanResult(final Class type) {
        final var file = new File();
        file.setPath("src/main/java/com/example/OrderController.java");
        file.getClasses().add(type);

        final var module = new Module();
        module.setPath("orders");
        module.getFiles().add(file);

        final var target = new ScanTarget();
        target.setPath("backend");
        target.setLanguage(ScanTarget.Language.JAVA);

        final var result = new ScanResult();
        result.setTarget(target);
        result.getModules().add(module);

        return result;
    }
}
