package dev.graphnous.persistence.scan.result;

import dev.graphnous.core.model.Annotation;
import dev.graphnous.core.model.Arguments;
import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.Field;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Method;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.Parameter;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.core.model.TypeRef;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.enhancer.spring.SpringEnhancer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stores what the Spring enhancer finds in a stored scan result, to check
 * that the ids it builds are those of the stored nodes. Runs against Neo4j
 * in a container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class SpringEnhancementsTest {

    private static final String WEB = "org.springframework.web.bind.annotation.";

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void disconnect() {
        driver.close();
    }

    @Test
    void linksTheControllersEndpointsAndTheirParametersToTheClassesAndMethodsTheyEnhance() {
        final var scanId = new Scan.ScanId(UUID.randomUUID());
        final var result = scanResult();

        try (final var session = driver.session()) {
            session.run("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString())).consume();
        }

        new ScanResultRepositoryImpl(driver).save(scanId, List.of(result));
        new EnhancementRepositoryImpl(driver).save(scanId, List.of(new SpringEnhancer().enhance(result)));

        try (final var session = driver.session()) {
            final var endpoints = session.run("""
                    MATCH (:Scan {id: $scanId})-[:HAS_ENHANCEMENT]->(spring:Enhancement {name: 'spring', version: '1.0.0'})
                    MATCH (class:Class {scanId: $scanId})-[:ENHANCE]->(controller:ENHANCED:Controller)-[:HAS_ENDPOINT]->(endpoint:ENHANCED:Endpoint)
                    MATCH (class)-[:HAS_METHOD]->(method:Method)-[:ENHANCE]->(endpoint)
                    MATCH (spring)-[:ADDED]->(controller), (spring)-[:ADDED]->(endpoint)
                    RETURN class.qualifiedName AS class, controller.baseUrl AS baseUrl,
                           method.name AS method, endpoint.httpMethod AS httpMethod, endpoint.url AS url
                    ORDER BY url, httpMethod
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.asMap());

            assertThat(endpoints).containsExactly(
                Map.of("class", "com.example.OrderController", "baseUrl", "/orders",
                    "method", "list", "httpMethod", "GET", "url", "/orders"),
                Map.of("class", "com.example.OrderController", "baseUrl", "/orders",
                    "method", "create", "httpMethod", "POST", "url", "/orders"),
                Map.of("class", "com.example.OrderController", "baseUrl", "/orders",
                    "method", "find", "httpMethod", "GET", "url", "/orders/{id}")
            );

            final var queryParameters = session.run("""
                    MATCH (:Endpoint {scanId: $scanId, url: '/orders'})-[:HAS_QUERY_PARAMETER]->(parameter:ENHANCED:QueryParameter)
                    MATCH (:Method {name: 'list'})-[:ENHANCE]->(parameter)
                    RETURN parameter.name AS name, parameter.type AS type, parameter.defaultValue AS defaultValue,
                           parameter.optional AS optional
                    ORDER BY name
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.asMap());

            final var status = new HashMap<String, Object>();
            status.put("name", "status");
            status.put("type", "java.lang.String");
            status.put("defaultValue", null);
            status.put("optional", false);

            assertThat(queryParameters).containsExactly(
                Map.of("name", "page", "type", "int", "defaultValue", "0", "optional", true),
                status
            );

            final var pathVariables = session.run("""
                    MATCH (:Endpoint {scanId: $scanId, url: '/orders/{id}'})-[:HAS_PATH_VARIABLE]->(variable:ENHANCED:PathVariable)
                    MATCH (:Method {name: 'find'})-[:ENHANCE]->(variable)
                    RETURN variable.name AS name, variable.type AS type, variable.optional AS optional
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.asMap());

            assertThat(pathVariables).containsExactly(
                Map.of("name", "id", "type", "java.lang.String", "optional", false)
            );

            final var requestBodies = session.run("""
                    MATCH (:Endpoint {scanId: $scanId, httpMethod: 'POST'})-[:HAS_REQUEST_BODY]->(body:ENHANCED:RequestBody)
                    MATCH (:Method {name: 'create'})-[:ENHANCE]->(body)
                    RETURN body.name AS name, body.type AS type, body.optional AS optional
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.asMap());

            assertThat(requestBodies).containsExactly(
                Map.of("name", "order", "type", "com.example.Order", "optional", false)
            );

            final var services = session.run("""
                    MATCH (spring:Enhancement {scanId: $scanId, name: 'spring'})-[:ADDED]->(service:ENHANCED:Service)
                    MATCH (class:Class)-[:ENHANCE]->(service)
                    RETURN class.qualifiedName AS class
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.get("class").asString());

            assertThat(services).containsExactly("com.example.OrderService");

            final var repositories = session.run("""
                    MATCH (spring:Enhancement {scanId: $scanId, name: 'spring'})-[:ADDED]->(repository:ENHANCED:Repository)
                    MATCH (class:Class)-[:ENHANCE]->(repository)
                    RETURN class.qualifiedName AS class
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.get("class").asString());

            assertThat(repositories).containsExactly("com.example.OrderRepository");

            final var components = session.run("""
                    MATCH (spring:Enhancement {scanId: $scanId, name: 'spring'})-[:ADDED]->(component:ENHANCED:Component)
                    MATCH (class:Class)-[:ENHANCE]->(component)
                    RETURN class.qualifiedName AS class
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.get("class").asString());

            assertThat(components).containsExactly("com.example.OrderMapper");

            final var injects = session.run("""
                    MATCH (controller:ENHANCED:Controller {scanId: $scanId})-[:INJECTS]->(service:ENHANCED:Service)
                    MATCH (from:Class)-[:ENHANCE]->(controller), (to:Class)-[:ENHANCE]->(service)
                    RETURN from.qualifiedName AS from, to.qualifiedName AS to
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.asMap());

            assertThat(injects).containsExactly(
                Map.of("from", "com.example.OrderController", "to", "com.example.OrderService")
            );

            final var beans = session.run("""
                    MATCH (service:ENHANCED:Service {scanId: $scanId})-[:INJECTS]->(bean:ENHANCED:Bean)
                    MATCH (method:Method)-[:ENHANCE]->(bean)
                    RETURN bean.name AS name, bean.type AS type, method.name AS method
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.asMap());

            assertThat(beans).containsExactly(
                Map.of("name", "clock", "type", "java.time.Clock", "method", "clock")
            );

            // Through its one constructor, which needs no @Autowired
            final var injectedRepositories = session.run("""
                    MATCH (:ENHANCED:Service {scanId: $scanId})-[:INJECTS]->(repository:ENHANCED:Repository)
                    MATCH (class:Class)-[:ENHANCE]->(repository)
                    RETURN class.qualifiedName AS class
                    """, Map.of("scanId", scanId.id().toString()))
                .list(record -> record.get("class").asString());

            assertThat(injectedRepositories).containsExactly("com.example.OrderRepository");
        }
    }

    private static ScanResult scanResult() {
        final var controller = new Class();
        controller.setName("OrderController");
        controller.setQualifiedName("com.example.OrderController");
        controller.setAnnotations(List.of(
            annotation(WEB + "RestController", Map.of()),
            annotation(WEB + "RequestMapping", Map.of("value", "/orders"))
        ));
        controller.setMethods(List.of(
            method("list", "com.example.OrderController.list(java.lang.String,int)", annotation(WEB + "GetMapping", Map.of())),
            method("find", "com.example.OrderController.find(java.lang.String)", annotation(WEB + "GetMapping", Map.of("value", "/{id}"))),
            method("create", "com.example.OrderController.create(com.example.Order)", annotation(WEB + "PostMapping", Map.of()))
        ));

        controller.getMethods().getFirst().setParameters(List.of(
            parameter("status", "java.lang.String", annotation(WEB + "RequestParam", Map.of())),
            parameter("page", "int", annotation(WEB + "RequestParam", Map.of("defaultValue", "0")))
        ));

        controller.getMethods().get(1).setParameters(List.of(
            parameter("id", "java.lang.String", annotation(WEB + "PathVariable", Map.of()))
        ));
        controller.getMethods().get(2).setParameters(List.of(
            parameter("order", "com.example.Order", annotation(WEB + "RequestBody", Map.of()))
        ));

        final var orders = new Field();
        orders.setName("orders");
        orders.setType(type("com.example.OrderService"));
        orders.setAnnotations(List.of(annotation("org.springframework.beans.factory.annotation.Autowired", Map.of())));
        controller.getFields().add(orders);

        final var file = new File();
        file.setPath("src/main/java/com/example/OrderController.java");
        file.setPackage("com.example");
        file.getClasses().add(controller);

        final var service = new Class();
        service.setName("OrderService");
        service.setQualifiedName("com.example.OrderService");
        service.setAnnotations(List.of(annotation("org.springframework.stereotype.Service", Map.of())));
        final var clock = new Field();
        clock.setName("clock");
        clock.setType(type("java.time.Clock"));
        clock.setAnnotations(List.of(annotation("org.springframework.beans.factory.annotation.Autowired", Map.of())));
        service.getFields().add(clock);

        final var repositoryParameter = new Parameter();
        repositoryParameter.setName("repository");
        repositoryParameter.setType(type("com.example.OrderRepository"));

        final var constructor = new Method();
        constructor.setName("OrderService");
        constructor.setQualifiedName("com.example.OrderService.OrderService(com.example.OrderRepository)");
        constructor.setKind(Method.Kind.CONSTRUCTOR);
        constructor.setParameters(List.of(repositoryParameter));
        service.getMethods().add(constructor);
        file.getClasses().add(service);

        final var clockBean = method("clock", "com.example.OrderConfiguration.clock()", annotation("org.springframework.context.annotation.Bean", Map.of()));
        clockBean.setReturnType(type("java.time.Clock"));

        final var configuration = new Class();
        configuration.setName("OrderConfiguration");
        configuration.setQualifiedName("com.example.OrderConfiguration");
        configuration.setAnnotations(List.of(annotation("org.springframework.context.annotation.Configuration", Map.of())));
        configuration.setMethods(List.of(clockBean));
        file.getClasses().add(configuration);

        final var repository = new Class();
        repository.setName("OrderRepository");
        repository.setQualifiedName("com.example.OrderRepository");
        repository.setAnnotations(List.of(annotation("org.springframework.stereotype.Repository", Map.of())));
        file.getClasses().add(repository);

        final var component = new Class();
        component.setName("OrderMapper");
        component.setQualifiedName("com.example.OrderMapper");
        component.setAnnotations(List.of(annotation("org.springframework.stereotype.Component", Map.of())));
        file.getClasses().add(component);

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

    private static Method method(final String name, final String qualifiedName, final Annotation annotation) {
        final var method = new Method();
        method.setName(name);
        method.setQualifiedName(qualifiedName);
        method.setAnnotations(List.of(annotation));

        return method;
    }

    private static Parameter parameter(final String name, final String type, final Annotation annotation) {
        final var parameter = new Parameter();
        parameter.setName(name);
        parameter.setType(type(type));
        parameter.setAnnotations(List.of(annotation));

        return parameter;
    }

    private static TypeRef type(final String name) {
        final var type = new TypeRef();
        type.setName(name);

        return type;
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
}
