package dev.graphnous.application.scan.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SourcePathsTest {

    private static final String SCAN = "7d17d5e9-de33-4fd0-b9ac-16daa040ed92";

    @Test
    void placesAFileOfAModuleInItsTarget() {
        // A Java file is stored with its module's directory
        assertThat(SourcePaths.inProject(
            SCAN + "|server|graphnous-api|class:dev.graphnous.Api|method:dev.graphnous.Api.run()",
            "graphnous-api/src/main/java/dev/graphnous/Api.java"
        )).isEqualTo("server/graphnous-api/src/main/java/dev/graphnous/Api.java");

        // A file stored relative to its module
        assertThat(SourcePaths.inProject(SCAN + "|backend|orders|class:com.acme.Orders", "src/main/java/com/acme/Orders.java"))
            .isEqualTo("backend/orders/src/main/java/com/acme/Orders.java");

        // A module that is its target, as of an npm project
        assertThat(SourcePaths.inProject(SCAN + "|web/shop|.|class:src/app/orders.service:OrderService", "src/app/orders.service.ts"))
            .isEqualTo("web/shop/src/app/orders.service.ts");
    }

    @Test
    void placesTheFileInItsProjectsDirectory() {
        assertThat(SourcePaths.inRepository("services/orders/", SCAN + "|.|.|class:Orders", "./src/Orders.java"))
            .isEqualTo("services/orders/src/Orders.java");
        assertThat(SourcePaths.inRepository(null, SCAN + "|.|.|class:Orders", "src/Orders.java"))
            .isEqualTo("src/Orders.java");
    }

    @Test
    void leavesTheFileWhenTheIdDoesNotSayWhereItIs() {
        assertThat(SourcePaths.inProject("u1", "src/Caller.java")).isEqualTo("src/Caller.java");
        assertThat(SourcePaths.inProject(null, "src/Caller.java")).isEqualTo("src/Caller.java");
        assertThat(SourcePaths.inProject(SCAN + "|server|api|class:X", null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "server/graphnous-api/src/test/java/dev/graphnous/ApiTest.java",
        "src/main/java/com/acme/OrdersTest.java",
        "src/main/java/com/acme/OrdersIT.java",
        "src/app/orders.spec.ts",
        "web/src/components/Orders.test.tsx",
        "web/__tests__/orders.js",
        "tests/test_orders.py"
    })
    void tellsTestCode(final String path) {
        assertThat(SourcePaths.isTest(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "server/graphnous-api/src/main/java/dev/graphnous/Api.java",
        "src/main/java/com/acme/Testimonial.java",
        "src/app/test-data.ts",
        "contest/Orders.java"
    })
    void tellsCodeFromTests(final String path) {
        assertThat(SourcePaths.isTest(path)).isFalse();
    }
}
