package dev.graphnous.enhancer.spring;

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
import dev.graphnous.enhancer.Node;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class InjectionsTest {

    private static final String STEREOTYPE = "org.springframework.stereotype.";
    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    private static final String MODULE = "backend|orders|class:";

    @Test
    void injectsAnAutowiredFieldsSpringClass() {
        final var controller = spring("com.example.OrderController", "org.springframework.web.bind.annotation.RestController");
        controller.getFields().add(field("orders", "com.example.OrderService", AUTOWIRED));
        // Not autowired
        controller.getFields().add(field("repository", "com.example.OrderRepository"));

        final var injects = injects(
            controller,
            spring("com.example.OrderService", STEREOTYPE + "Service"),
            spring("com.example.OrderRepository", STEREOTYPE + "Repository")
        );

        assertThat(injects).containsExactly(
            tuple(MODULE + "com.example.OrderController|controller", MODULE + "com.example.OrderService|service")
        );
    }

    @Test
    void injectsTheParametersOfAnAutowiredConstructorAndSetter() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getMethods().add(constructor("OrderService", AUTOWIRED, parameter("repository", "com.example.OrderRepository")));
        service.getMethods().add(method("setMapper", AUTOWIRED, parameter("mapper", "com.example.OrderMapper")));
        service.getMethods().add(method("find", null, parameter("id", "com.example.OrderMapper")));

        final var injects = injects(
            service,
            spring("com.example.OrderRepository", STEREOTYPE + "Repository"),
            spring("com.example.OrderMapper", STEREOTYPE + "Component")
        );

        assertThat(injects).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderRepository|repository"),
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderMapper|component")
        );
    }

    @Test
    void injectsTheParametersOfTheOneConstructor() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getMethods().add(constructor("OrderService", null,
            parameter("repository", "com.example.OrderRepository"),
            parameter("mapper", "com.example.OrderMapper")
        ));

        final var injects = injects(
            service,
            spring("com.example.OrderRepository", STEREOTYPE + "Repository"),
            spring("com.example.OrderMapper", STEREOTYPE + "Component")
        );

        assertThat(injects).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderRepository|repository"),
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderMapper|component")
        );
    }

    @Test
    void injectsOnlyTheAutowiredOneOfSeveralConstructors() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getMethods().add(constructor("OrderService", null, parameter("mapper", "com.example.OrderMapper")));
        service.getMethods().add(constructor("OrderService", AUTOWIRED, parameter("repository", "com.example.OrderRepository")));

        final var injects = injects(
            service,
            spring("com.example.OrderRepository", STEREOTYPE + "Repository"),
            spring("com.example.OrderMapper", STEREOTYPE + "Component")
        );

        assertThat(injects).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderRepository|repository")
        );
    }

    @Test
    void injectsNoneOfSeveralConstructorsWithoutTheAnnotation() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getMethods().add(constructor("OrderService", null));
        service.getMethods().add(constructor("OrderService", null, parameter("mapper", "com.example.OrderMapper")));

        assertThat(injects(service, spring("com.example.OrderMapper", STEREOTYPE + "Component"))).isEmpty();
    }

    @Test
    void injectsNothingThroughTheConstructorOfAClassThatIsNoSpringClass() {
        final var mapper = plain("com.example.OrderMapper");
        mapper.getMethods().add(constructor("OrderMapper", null, parameter("repository", "com.example.OrderRepository")));

        assertThat(injects(mapper, spring("com.example.OrderRepository", STEREOTYPE + "Repository"))).isEmpty();
    }

    @Test
    void injectsTheOneSpringClassImplementingAnInterface() {
        final var controller = spring("com.example.OrderController", STEREOTYPE + "Controller");
        controller.getFields().add(field("orders", "com.example.Orders", AUTOWIRED));

        final var implementation = spring("com.example.OrderService", STEREOTYPE + "Service");
        implementation.getInterfaces().add(type("com.example.Orders"));

        assertThat(injects(controller, plain("com.example.Orders"), implementation)).containsExactly(
            tuple(MODULE + "com.example.OrderController|controller", MODULE + "com.example.OrderService|service")
        );
    }

    @Test
    void injectsTheTypeItselfWhenSeveralSpringClassesImplementIt() {
        final var controller = spring("com.example.OrderController", STEREOTYPE + "Controller");
        controller.getFields().add(field("orders", "com.example.Orders", AUTOWIRED));

        final var first = spring("com.example.LocalOrders", STEREOTYPE + "Service");
        first.getInterfaces().add(type("com.example.Orders"));

        final var second = spring("com.example.RemoteOrders", STEREOTYPE + "Service");
        second.getSuperClasses().add(type("com.example.Orders"));

        assertThat(injects(controller, plain("com.example.Orders"), first, second)).containsExactly(
            tuple(MODULE + "com.example.OrderController|controller", MODULE + "com.example.Orders")
        );
    }

    @Test
    void injectsEverySpringClassOfACollection() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getFields().add(field("handlers", "java.util.List<com.example.Handler>", AUTOWIRED));
        service.getFields().add(field("validators", "java.util.Set<? extends com.example.Validator>", AUTOWIRED));

        final var created = spring("com.example.CreatedHandler", STEREOTYPE + "Component");
        created.getInterfaces().add(type("com.example.Handler"));

        final var paid = spring("com.example.PaidHandler", STEREOTYPE + "Component");
        paid.getInterfaces().add(type("com.example.Handler<com.example.Payment>"));

        final var validator = spring("com.example.OrderValidator", STEREOTYPE + "Component");
        validator.getInterfaces().add(type("com.example.Validator"));

        assertThat(injects(service, plain("com.example.Handler"), created, paid, validator)).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.CreatedHandler|component"),
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.PaidHandler|component"),
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderValidator|component")
        );
    }

    @Test
    void injectsWhatAnOptionalOrProviderHolds() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getFields().add(field("mapper", "java.util.Optional<com.example.OrderMapper>", AUTOWIRED));
        service.getFields().add(field("repository", "org.springframework.beans.factory.ObjectProvider<com.example.OrderRepository>", AUTOWIRED));

        final var injects = injects(
            service,
            spring("com.example.OrderMapper", STEREOTYPE + "Component"),
            spring("com.example.OrderRepository", STEREOTYPE + "Repository")
        );

        assertThat(injects).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderMapper|component"),
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderRepository|repository")
        );
    }

    @Test
    void injectsAClassThatIsNoSpringClass() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getFields().add(field("clock", "com.example.OrderClock", AUTOWIRED));

        assertThat(injects(service, plain("com.example.OrderClock"))).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderClock")
        );
    }

    @Test
    void leavesOutTypesTheScanResultDoesNotHave() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getFields().add(field("jdbc", "org.springframework.jdbc.core.JdbcTemplate", AUTOWIRED));

        assertThat(injects(service)).isEmpty();
    }

    @Test
    void injectsNothingForClassesThatAreNoSpringClasses() {
        final var mapper = plain("com.example.OrderMapper");
        mapper.getFields().add(field("orders", "com.example.OrderService", AUTOWIRED));

        assertThat(injects(mapper, spring("com.example.OrderService", STEREOTYPE + "Service"))).isEmpty();
    }

    @Test
    void injectsIntoAndFromApplicationsAndConfigurations() {
        final var application = spring("com.example.OrderApplication", "org.springframework.boot.autoconfigure.SpringBootApplication");
        application.getFields().add(field("properties", "com.example.OrderConfiguration", AUTOWIRED));

        final var configuration = spring("com.example.OrderConfiguration", "org.springframework.context.annotation.Configuration");
        configuration.getMethods().add(constructor("OrderConfiguration", null, parameter("orders", "com.example.OrderService")));

        final var injects = injects(application, configuration, spring("com.example.OrderService", STEREOTYPE + "Service"));

        assertThat(injects).containsExactly(
            tuple(MODULE + "com.example.OrderApplication|application", MODULE + "com.example.OrderConfiguration|configuration"),
            tuple(MODULE + "com.example.OrderConfiguration|configuration", MODULE + "com.example.OrderService|service")
        );
    }

    @Test
    void addsABeanForEachBeanMethod() {
        final var configuration = configuration(
            beanMethod("jdbc", "org.springframework.jdbc.core.JdbcTemplate", null),
            beanMethod("client", "com.example.OrderClient", List.of("orderClient", "client"))
        );

        final var configurationClass = MODULE + "com.example.OrderConfiguration";

        assertThat(nodes(configuration)).filteredOn(node -> node.labels().equals(List.of("Bean"))).containsExactly(
            beanNode(configurationClass + "|method:com.example.jdbc()", "jdbc", "org.springframework.jdbc.core.JdbcTemplate"),
            beanNode(configurationClass + "|method:com.example.client()", "orderClient", "com.example.OrderClient")
        );
    }

    @Test
    void injectsTheBeanThatProvidesALibraryType() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getFields().add(field("jdbc", "org.springframework.jdbc.core.JdbcTemplate", AUTOWIRED));

        final var configuration = configuration(beanMethod("jdbc", "org.springframework.jdbc.core.JdbcTemplate", null));

        assertThat(injects(service, configuration)).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderConfiguration|method:com.example.jdbc()|bean")
        );
    }

    @Test
    void injectsTheBeanWhoseClassImplementsTheType() {
        final var controller = spring("com.example.OrderController", STEREOTYPE + "Controller");
        controller.getFields().add(field("orders", "com.example.Orders", AUTOWIRED));

        final var local = plain("com.example.LocalOrders");
        local.getInterfaces().add(type("com.example.Orders"));

        final var configuration = configuration(beanMethod("orders", "com.example.LocalOrders", null));

        assertThat(injects(controller, plain("com.example.Orders"), local, configuration)).containsExactly(
            tuple(MODULE + "com.example.OrderController|controller", MODULE + "com.example.OrderConfiguration|method:com.example.orders()|bean")
        );
    }

    @Test
    void injectsTheTypeItselfWhenASpringClassAndABeanProvideIt() {
        final var controller = spring("com.example.OrderController", STEREOTYPE + "Controller");
        controller.getFields().add(field("orders", "com.example.Orders", AUTOWIRED));

        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getInterfaces().add(type("com.example.Orders"));

        final var configuration = configuration(beanMethod("orders", "com.example.Orders", null));

        assertThat(injects(controller, plain("com.example.Orders"), service, configuration)).containsExactly(
            tuple(MODULE + "com.example.OrderController|controller", MODULE + "com.example.Orders")
        );
    }

    @Test
    void injectsTheBeansOfACollection() {
        final var service = spring("com.example.OrderService", STEREOTYPE + "Service");
        service.getFields().add(field("handlers", "java.util.List<com.example.Handler>", AUTOWIRED));

        final var created = spring("com.example.CreatedHandler", STEREOTYPE + "Component");
        created.getInterfaces().add(type("com.example.Handler"));

        final var configuration = configuration(beanMethod("paid", "com.example.Handler", null));

        assertThat(injects(service, created, configuration)).containsExactly(
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.CreatedHandler|component"),
            tuple(MODULE + "com.example.OrderService|service", MODULE + "com.example.OrderConfiguration|method:com.example.paid()|bean")
        );
    }

    @Test
    void injectsTheParametersOfABeanMethod() {
        final var properties = spring("com.example.OrderProperties", STEREOTYPE + "Component");
        final var client = beanMethod("client", "com.example.OrderClient", null);
        client.setParameters(List.of(parameter("properties", "com.example.OrderProperties")));

        assertThat(injects(properties, configuration(client))).containsExactly(
            tuple(MODULE + "com.example.OrderConfiguration|method:com.example.client()|bean", MODULE + "com.example.OrderProperties|component")
        );
    }

    @Test
    void takesTheOneTypeArgument() {
        assertThat(Injections.typeArgument("java.util.List<com.example.Handler<java.lang.String>>"))
            .isEqualTo("com.example.Handler<java.lang.String>");
        assertThat(Injections.typeArgument("java.util.Map<java.lang.String, com.example.Handler>")).isNull();
        assertThat(Injections.typeArgument("com.example.Handler")).isNull();
    }

    private static List<Node> nodes(final Class... classes) {
        return new SpringEnhancer().enhance(scanResult(classes))
            .enhancements()
            .stream()
            .flatMap(enhancement -> enhancement.nodes().stream())
            .toList();
    }

    private static Node beanNode(final String methodId, final String name, final String type) {
        return new Node(methodId + "|bean", methodId, List.of("Bean"), Map.of("name", name, "type", type));
    }

    private static Class configuration(final Method... beans) {
        final var configuration = spring("com.example.OrderConfiguration", "org.springframework.context.annotation.Configuration");
        configuration.getMethods().addAll(List.of(beans));

        return configuration;
    }

    /**
     * A {@code @Bean} method, with the names its annotation gives, if any.
     */
    private static Method beanMethod(final String name, final String returnType, final List<String> names) {
        final var method = method(name, "org.springframework.context.annotation.Bean");
        method.setReturnType(type(returnType));

        if (names != null) {
            final var arguments = new Arguments();
            arguments.setAdditionalProperty("name", names);
            method.getAnnotations().getFirst().setArguments(arguments);
        }

        return method;
    }

    private static List<Tuple> injects(final Class... classes) {
        return new SpringEnhancer().enhance(scanResult(classes))
            .enhancements()
            .stream()
            .flatMap(enhancement -> enhancement.relationships().stream())
            .filter(relationship -> relationship.type().equals("INJECTS"))
            .map(relationship -> tuple(relationship.sourceId(), relationship.targetId()))
            .toList();
    }

    private static Class spring(final String qualifiedName, final String annotation) {
        final var type = plain(qualifiedName);
        type.getAnnotations().add(annotation(annotation));

        return type;
    }

    private static Class plain(final String qualifiedName) {
        final var type = new Class();
        type.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        type.setQualifiedName(qualifiedName);

        return type;
    }

    private static Field field(final String name, final String type, final String... annotations) {
        final var field = new Field();
        field.setName(name);
        field.setType(type(type));
        Arrays.stream(annotations).map(InjectionsTest::annotation).forEach(field.getAnnotations()::add);

        return field;
    }

    private static Method method(final String name, final String annotation, final Parameter... parameters) {
        final var method = new Method();
        method.setName(name);
        method.setQualifiedName("com.example." + name + "()");
        method.setParameters(List.of(parameters));

        if (annotation != null) {
            method.getAnnotations().add(annotation(annotation));
        }

        return method;
    }

    private static Method constructor(final String name, final String annotation, final Parameter... parameters) {
        final var constructor = method(name, annotation, parameters);
        constructor.setKind(Method.Kind.CONSTRUCTOR);

        return constructor;
    }

    private static Parameter parameter(final String name, final String type) {
        final var parameter = new Parameter();
        parameter.setName(name);
        parameter.setType(type(type));

        return parameter;
    }

    private static TypeRef type(final String name) {
        final var type = new TypeRef();
        type.setName(name);

        return type;
    }

    private static Annotation annotation(final String qualifiedName) {
        final var annotation = new Annotation();
        annotation.setName(qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1));
        annotation.setQualifiedName(qualifiedName);

        return annotation;
    }

    private static ScanResult scanResult(final Class... classes) {
        final var file = new File();
        file.setPath("src/main/java/com/example/Orders.java");
        file.getClasses().addAll(List.of(classes));

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
