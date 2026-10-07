package dev.graphnous.persistence.scan.result;

import dev.graphnous.core.model.Class;
import dev.graphnous.core.model.File;
import dev.graphnous.core.model.Module;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.core.model.ScanTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScanResultGraphTest {

    private final ScanResultGraph graph = ScanResultGraph.of("scan", List.of(ScanResults.orders()));

    @Test
    void identifiesNodesByTheirParents() {
        assertThat(graph.targets()).extracting(row -> row.get("id")).containsExactly("scan|backend");
        assertThat(graph.modules()).extracting(row -> row.get("id")).containsExactly("scan|backend|orders");
        assertThat(graph.fields()).extracting(row -> row.get("id"))
            .containsExactly("scan|backend|orders|class:com.example.Order|field:total");
    }

    @Test
    void linksEachClassToItsFileAndPackage() {
        assertThat(graph.classes()).extracting(row -> row.get("qualifiedName"))
            .containsExactly("com.example.Order", "com.example.Entity", "com.example.Identified");

        assertThat(graph.fileClasses()).hasSize(3);
        assertThat(graph.packageClasses()).hasSize(3);
    }

    @Test
    void storesNestedClassesInTheFileAndPackageOfTheirClass() {
        final var builder = new Class();
        builder.setName("Builder");
        builder.setQualifiedName("com.example.Order.Builder");

        final var order = new Class();
        order.setName("Order");
        order.setQualifiedName("com.example.Order");
        order.getClasses().add(builder);

        final var file = new File();
        file.setPath("src/main/java/com/example/Order.java");
        file.setPackage("com.example");
        file.getClasses().add(order);

        final var module = new Module();
        module.setPath("orders");
        module.getFiles().add(file);

        final var target = new ScanTarget();
        target.setPath("backend");
        target.setLanguage(ScanTarget.Language.JAVA);

        final var result = new ScanResult();
        result.setTarget(target);
        result.getModules().add(module);

        final var nested = ScanResultGraph.of("scan", List.of(result));

        assertThat(nested.classes()).extracting(row -> row.get("qualifiedName"))
            .containsExactly("com.example.Order", "com.example.Order.Builder");
        assertThat(nested.fileClasses()).extracting(row -> row.get("fileId")).containsOnly(
            "scan|backend|orders|file:src/main/java/com/example/Order.java"
        ).hasSize(2);
        assertThat(nested.packageClasses()).extracting(row -> row.get("packageId")).containsOnly(
            "scan|backend|orders|package:com.example"
        ).hasSize(2);
    }

    @Test
    void keepsTheClassDetailsFromTheFirstListing() {
        final var order = classNamed("com.example.Order");

        assertThat(order)
            .containsEntry("kind", "CLASS")
            .containsEntry("modifiers", List.of("PUBLIC", "FINAL"))
            .containsEntry("superClass", "com.example.Entity<java.util.UUID>")
            .containsEntry("annotations", List.of("java.lang.Deprecated", "jakarta.ws.rs.Path", "jakarta.persistence.Table"));

        assertThat(graph.methods()).hasSize(2);
    }

    @Test
    void linksSupertypesByTheirRawQualifiedName() {
        assertThat(graph.extendsTypes()).extracting(row -> row.get("qualifiedName"))
            .containsExactly("com.example.Entity");

        assertThat(graph.implementsTypes()).extracting(row -> row.get("qualifiedName"))
            .containsExactly("com.example.Identified", "java.lang.Comparable");
    }

    @Test
    void storesParametersAndAnnotationsAsProperties() {
        final var compareTo = graph.methods().stream()
            .filter(row -> "compareTo".equals(row.get("name")))
            .findFirst()
            .orElseThrow();

        assertThat(compareTo)
            .containsEntry("parameterNames", List.of("other"))
            .containsEntry("parameterTypes", List.of("com.example.Order"))
            .containsEntry("returnType", "int")
            // No qualified name reported, so the simple name is kept
            .containsEntry("annotations", List.of("Override"));
    }

    @Test
    void storesEachArgumentAsAProperty() {
        final var path = annotation(graph.classAnnotations(), "jakarta.ws.rs.Path");

        assertThat(path)
            .containsEntry("ownerId", "scan|backend|orders|class:com.example.Order")
            .containsEntry("id", "scan|backend|orders|class:com.example.Order|annotation:1");
        assertThat(properties(path))
            .containsEntry("name", "Path")
            .containsEntry("arguments.value", "/orders")
            .containsEntry("arguments", "{\"value\":\"/orders\"}");
    }

    @Test
    void keepsArgumentsApartFromTheAnnotationsOwnProperties() {
        final var column = properties(annotation(graph.fieldAnnotations(), "jakarta.persistence.Column"));

        assertThat(column)
            .containsEntry("name", "Column")
            .containsEntry("arguments.name", "total")
            .containsEntry("arguments.nullable", false)
            .containsEntry("arguments.length", 10);
    }

    @Test
    void storesListsOfOneTypeAsListsAndOthersAsJson() {
        final var column = properties(annotation(graph.fieldAnnotations(), "jakarta.persistence.Column"));
        final var table = properties(annotation(graph.classAnnotations(), "jakarta.persistence.Table"));

        assertThat(column)
            .containsEntry("arguments.columnDefinition", List.of("a", "b"))
            .containsEntry("arguments.precision", List.of(1L, 2L))
            .containsEntry("arguments.scale", List.of(1.0, 2.5));

        assertThat(table)
            .containsEntry("arguments.uniqueConstraints", List.of())
            .containsEntry("arguments.indexes",
                "[{\"name\":\"Index\",\"qualifiedName\":\"jakarta.persistence.Index\",\"arguments\":{\"columnList\":\"total\"}}]");
    }

    @Test
    void linksParameterAnnotationsToTheirMethod() {
        final var pathParam = annotation(graph.methodAnnotations(), "jakarta.ws.rs.PathParam");

        assertThat(pathParam)
            .containsEntry("ownerId", "scan|backend|orders|class:com.example.Order|method:com.example.Order.Order(java.util.UUID)")
            .containsEntry("parameter", "id")
            .containsEntry("parameterIndex", 0);
        assertThat(properties(pathParam)).containsEntry("arguments.value", "id");

        assertThat(annotation(graph.methodAnnotations(), "jakarta.inject.Inject"))
            .containsEntry("parameter", null);
    }

    @Test
    void identifiesDependenciesByNameAndVersion() {
        assertThat(graph.dependencies()).extracting(row -> row.get("coordinates"))
            .containsExactly("org.slf4j:slf4j-api:2.0.18", "org.junit.jupiter:junit-jupiter:");
    }

    @Test
    void stripsTypeArguments() {
        assertThat(ScanResultGraph.rawType("java.util.Map<java.lang.String, java.util.List<Order>>"))
            .isEqualTo("java.util.Map");
        assertThat(ScanResultGraph.rawType("com.example.Order")).isEqualTo("com.example.Order");
    }

    private static Map<String, Object> annotation(
        final List<Map<String, Object>> rows,
        final String qualifiedName
    ) {
        return rows.stream()
            .filter(row -> qualifiedName.equals(properties(row).get("qualifiedName")))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(final Map<String, Object> row) {
        return (Map<String, Object>) row.get("properties");
    }

    private Map<String, Object> classNamed(final String qualifiedName) {
        return graph.classes().stream()
            .filter(row -> qualifiedName.equals(row.get("qualifiedName")))
            .findFirst()
            .orElseThrow();
    }
}
