package dev.graphnous.persistence.scan;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.scan.classes.ScanClass;
import dev.graphnous.application.scan.classes.ScanClassFilter;
import dev.graphnous.application.scan.dependencies.ScanDependency;
import dev.graphnous.application.scan.dependencies.ScanDependencyFilter;
import dev.graphnous.application.scan.files.ScanFile;
import dev.graphnous.application.scan.files.ScanFileFilter;
import dev.graphnous.application.scan.methods.ScanMethod;
import dev.graphnous.application.scan.methods.ScanMethodFilter;
import dev.graphnous.application.scan.modules.ScanModule;
import dev.graphnous.application.scan.modules.ScanModuleFilter;
import dev.graphnous.application.scan.packages.ScanPackage;
import dev.graphnous.application.scan.packages.ScanPackageFilter;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.scan.classes.ScanClassRepositoryImpl;
import dev.graphnous.persistence.scan.dependencies.ScanDependencyRepositoryImpl;
import dev.graphnous.persistence.scan.files.ScanFileRepositoryImpl;
import dev.graphnous.persistence.scan.methods.ScanMethodRepositoryImpl;
import dev.graphnous.persistence.scan.modules.ScanModuleRepositoryImpl;
import dev.graphnous.persistence.scan.packages.ScanPackageRepositoryImpl;
import dev.graphnous.persistence.scan.result.ScanResultRepositoryImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Lists the modules, packages, files, classes, methods and dependencies of a
 * scan with a Java target and a Python one, stored by ScanResultRepositoryImpl, in Neo4j in a
 * container; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ScanListingRepositoriesTest {

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
        .withoutAuthentication();

    private static Driver driver;

    private Scan.ScanId scanId;

    @BeforeAll
    static void connect() {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    static void disconnect() {
        driver.close();
    }

    @BeforeEach
    void storeAScan() throws IOException {
        try (final var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n").consume();
        }

        scanId = createScan();

        new ScanResultRepositoryImpl(driver).save(scanId, List.of(
            read("/scan-result.json"),
            read("/scan-result-python.json")
        ));
    }

    @Test
    void listsTheModulesWithWhatTheyHold() {
        final var modules = new ScanModuleRepositoryImpl(driver).findModules(scanId, ScanModuleFilter.none(), sortedBy("path"));

        assertThat(modules.content())
            .extracting(
                ScanModule::target, ScanModule::name, ScanModule::path, ScanModule::files, ScanModule::packages,
                ScanModule::classes, ScanModule::methods, ScanModule::dependencies
            )
            .containsExactly(
                // The methods of the Python module include its two functions
                tuple("shop", "shop", ".", 2L, 1L, 1L, 3L, 0L),
                tuple("backend", "orders", "orders", 2L, 1L, 3L, 2L, 2L)
            );
        assertThat(modules.content().get(1).id()).isEqualTo(scan() + "|backend|orders");
    }

    @Test
    void filtersModules() {
        final var repository = new ScanModuleRepositoryImpl(driver);

        assertThat(repository.findModules(scanId, new ScanModuleFilter("ORD", null), sortedBy("name")).content())
            .extracting(ScanModule::path)
            .containsExactly("orders");
        assertThat(repository.findModules(scanId, new ScanModuleFilter(null, "shop"), sortedBy("name")).content())
            .extracting(ScanModule::path)
            .containsExactly(".");
    }

    @Test
    void listsThePackagesWithWhatTheyContain() {
        final var packages = new ScanPackageRepositoryImpl(driver).findPackages(scanId, ScanPackageFilter.none(), sortedBy("qualifiedName"));

        assertThat(packages.content())
            .extracting(
                ScanPackage::id, ScanPackage::module, ScanPackage::name, ScanPackage::qualifiedName,
                ScanPackage::classes, ScanPackage::functions, ScanPackage::variables
            )
            .containsExactly(
                tuple(scan() + "|shop|.|package:app", ".", "app", "app", 1L, 1L, 1L),
                tuple(scan() + "|backend|orders|package:com.example", "orders", "example", "com.example", 3L, 0L, 0L)
            );
    }

    @Test
    void filtersPackages() {
        final var repository = new ScanPackageRepositoryImpl(driver);

        assertThat(repository.findPackages(scanId, new ScanPackageFilter("EXAMPLE", null), sortedBy("name")).content())
            .extracting(ScanPackage::qualifiedName)
            .containsExactly("com.example");
        assertThat(repository.findPackages(scanId, new ScanPackageFilter(null, "."), sortedBy("name")).content())
            .extracting(ScanPackage::qualifiedName)
            .containsExactly("app");
    }

    @Test
    void listsTheFilesOfEveryModule() {
        final var files = new ScanFileRepositoryImpl(driver).findFiles(scanId, ScanFileFilter.none(), sortedBy("path"));

        assertThat(files.totalElements()).isEqualTo(4);
        assertThat(files.content())
            .extracting(ScanFile::target, ScanFile::module, ScanFile::path, ScanFile::classes, ScanFile::functions)
            .containsExactly(
                tuple("shop", ".", "app/orders.py", 1L, 1L),
                tuple("shop", ".", "main.py", 0L, 1L),
                tuple("backend", "orders", "src/main/java/com/example/Entity.java", 2L, 0L),
                tuple("backend", "orders", "src/main/java/com/example/Order.java", 1L, 0L)
            );
        assertThat(files.content().get(3))
            .extracting(ScanFile::id, ScanFile::language, ScanFile::sourceSet, ScanFile::size)
            .containsExactly(scan() + "|backend|orders|file:src/main/java/com/example/Order.java", "JAVA", "MAIN", 120L);
    }

    @Test
    void filtersFiles() {
        final var repository = new ScanFileRepositoryImpl(driver);

        assertThat(repository.findFiles(scanId, new ScanFileFilter("ORDER", null, null, null), sortedBy("path")).content())
            .extracting(ScanFile::path)
            .containsExactly("app/orders.py", "src/main/java/com/example/Order.java");
        assertThat(repository.findFiles(scanId, new ScanFileFilter(null, "orders", null, null), sortedBy("path")).totalElements())
            .isEqualTo(2);
        assertThat(repository.findFiles(scanId, new ScanFileFilter(null, null, "python", null), sortedBy("path")).totalElements())
            .isEqualTo(2);
        assertThat(repository.findFiles(scanId, new ScanFileFilter(null, null, null, "main"), sortedBy("path")).content())
            .extracting(ScanFile::path)
            .containsExactly("src/main/java/com/example/Order.java");
    }

    @Test
    void pagesThroughTheFiles() {
        final var second = new ScanFileRepositoryImpl(driver).findFiles(
            scanId,
            ScanFileFilter.none(),
            new PageQuery(1, 3, new Sort("path", Sort.Direction.DESC))
        );

        assertThat(second.content()).extracting(ScanFile::path).containsExactly("app/orders.py");
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.totalElements()).isEqualTo(4);
        assertThat(second.totalPages()).isEqualTo(2);
    }

    @Test
    void listsTheClassesWithWhereTheyAreDeclared() {
        final var classes = new ScanClassRepositoryImpl(driver).findClasses(scanId, ScanClassFilter.none(), sortedBy("qualifiedName"));

        assertThat(classes.content())
            .extracting(ScanClass::qualifiedName, ScanClass::module, ScanClass::file, ScanClass::packageName)
            .containsExactly(
                tuple("app.orders.Order", ".", "app/orders.py", "app"),
                tuple("com.example.Entity", "orders", "src/main/java/com/example/Entity.java", "com.example"),
                tuple("com.example.Identified", "orders", "src/main/java/com/example/Entity.java", "com.example"),
                tuple("com.example.Order", "orders", "src/main/java/com/example/Order.java", "com.example")
            );
        assertThat(classes.content().get(3))
            .satisfies(order -> {
                assertThat(order.id()).isEqualTo(scan() + "|backend|orders|class:com.example.Order");
                assertThat(order.kind()).isEqualTo("CLASS");
                assertThat(order.modifiers()).containsExactly("PUBLIC", "FINAL");
                assertThat(order.superClass()).isEqualTo("com.example.Entity<java.util.UUID>");
                assertThat(order.methods()).isEqualTo(2);
                assertThat(order.fields()).isEqualTo(1);
            });
    }

    @Test
    void filtersClasses() {
        final var repository = new ScanClassRepositoryImpl(driver);

        assertThat(repository.findClasses(scanId, new ScanClassFilter("order", null, null), sortedBy("qualifiedName")).content())
            .extracting(ScanClass::qualifiedName)
            .containsExactly("app.orders.Order", "com.example.Order");
        assertThat(repository.findClasses(scanId, new ScanClassFilter(null, "orders", null), sortedBy("qualifiedName")).totalElements())
            .isEqualTo(3);
        assertThat(repository.findClasses(scanId, new ScanClassFilter(null, null, "interface"), sortedBy("qualifiedName")).content())
            .extracting(ScanClass::qualifiedName)
            .containsExactly("com.example.Identified");
    }

    @Test
    void listsMethodsAndFunctionsWithWhereTheyAreDeclared() {
        final var methods = new ScanMethodRepositoryImpl(driver).findMethods(scanId, ScanMethodFilter.none(), sortedBy("qualifiedName"));

        assertThat(methods.content())
            .extracting(ScanMethod::qualifiedName, ScanMethod::kind, ScanMethod::className, ScanMethod::module, ScanMethod::file)
            .containsExactly(
                tuple("app.orders.Order.add", "METHOD", "app.orders.Order", ".", "app/orders.py"),
                tuple("app.orders.total", "FUNCTION", null, ".", "app/orders.py"),
                tuple("com.example.Order.Order(java.util.UUID)", "CONSTRUCTOR", "com.example.Order", "orders", "src/main/java/com/example/Order.java"),
                tuple("com.example.Order.compareTo(com.example.Order)", "METHOD", "com.example.Order", "orders", "src/main/java/com/example/Order.java"),
                tuple("main.main", "FUNCTION", null, ".", "main.py")
            );
    }

    @Test
    void filtersMethods() {
        final var repository = new ScanMethodRepositoryImpl(driver);

        assertThat(repository.findMethods(scanId, new ScanMethodFilter(null, null, "function", null), sortedBy("name")).content())
            .extracting(ScanMethod::name)
            .containsExactly("main", "total");
        assertThat(repository.findMethods(scanId, new ScanMethodFilter(null, null, null, "com.example.Order"), sortedBy("name")).content())
            .extracting(ScanMethod::name)
            .containsExactly("Order", "compareTo");
        assertThat(repository.findMethods(scanId, new ScanMethodFilter("TOTAL", ".", null, null), sortedBy("name")).content())
            .singleElement()
            .satisfies(total -> {
                assertThat(total.returnType()).isEqualTo("float");
                assertThat(total.parameterNames()).containsExactly("order");
                assertThat(total.annotations()).containsExactly("functools.cache");
            });
    }

    @Test
    void listsAndFiltersTheDependencies() {
        final var repository = new ScanDependencyRepositoryImpl(driver);

        assertThat(repository.findDependencies(scanId, ScanDependencyFilter.none(), sortedBy("name")).content())
            .containsExactly(
                new ScanDependency("dependency:org.junit.jupiter:junit-jupiter:", "orders", "org.junit.jupiter:junit-jupiter", null, "test"),
                new ScanDependency("dependency:org.slf4j:slf4j-api:2.0.18", "orders", "org.slf4j:slf4j-api", "2.0.18", "compile")
            );
        assertThat(repository.findDependencies(scanId, new ScanDependencyFilter("SLF4J", "orders", "Compile"), sortedBy("name")).content())
            .extracting(ScanDependency::name)
            .containsExactly("org.slf4j:slf4j-api");
        assertThat(repository.findDependencies(scanId, new ScanDependencyFilter(null, "shop", null), sortedBy("name")).content())
            .isEmpty();
    }

    @Test
    void listsNothingOfAnotherScan() {
        final var other = createScan();

        assertThat(new ScanModuleRepositoryImpl(driver).findModules(other, ScanModuleFilter.none(), sortedBy("path")).totalElements()).isZero();
        assertThat(new ScanPackageRepositoryImpl(driver).findPackages(other, ScanPackageFilter.none(), sortedBy("name")).totalElements()).isZero();
        assertThat(new ScanFileRepositoryImpl(driver).findFiles(other, ScanFileFilter.none(), sortedBy("path")).totalElements()).isZero();
        assertThat(new ScanClassRepositoryImpl(driver).findClasses(other, ScanClassFilter.none(), sortedBy("name")).totalElements()).isZero();
        assertThat(new ScanMethodRepositoryImpl(driver).findMethods(other, ScanMethodFilter.none(), sortedBy("name")).totalElements()).isZero();
        assertThat(new ScanDependencyRepositoryImpl(driver).findDependencies(other, ScanDependencyFilter.none(), sortedBy("name")).content())
            .isEmpty();
    }

    private String scan() {
        return scanId.id().toString();
    }

    private static PageQuery sortedBy(final String property) {
        return new PageQuery(0, 100, new Sort(property, Sort.Direction.ASC));
    }

    private static ScanResult read(final String resource) throws IOException {
        try (final var json = ScanListingRepositoriesTest.class.getResourceAsStream(resource)) {
            return new ObjectMapper().readValue(json, ScanResult.class);
        }
    }

    private static Scan.ScanId createScan() {
        final var scanId = Scan.ScanId.generate();

        try (final var session = driver.session()) {
            session.run("CREATE (:Scan {id: $id})", Map.of("id", scanId.id().toString())).consume();
        }

        return scanId;
    }
}
