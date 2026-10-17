package dev.graphnous.application.scan.impact;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.system.System;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RepositorySourcesTest {

    private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
    private static final String SHA = "6abd1b188d96145d6f232b7c5967aef254fc8e7f";

    private final Scan scan = new Scan(
        Scan.ScanId.generate(), Project.ProjectId.generate(), Scan.ScanStatus.COMPLETED,
        new Scan.SourceRevision(SHA, "main"), NOW, NOW, NOW
    );

    @Test
    void placesAndLinksAFileOfARepositoryOnGitHub() {
        final var sources = sources("git@github.com:graphnous/graphnous-app.git", null);
        final var path = sources.path(scan.id(), scan.id().id() + "|server|graphnous-api|class:dev.graphnous.Api", "graphnous-api/src/main/java/Api.java");

        assertThat(path).isEqualTo("server/graphnous-api/src/main/java/Api.java");
        assertThat(sources.link(scan.id(), path, 42))
            .isEqualTo("https://github.com/graphnous/graphnous-app/blob/" + SHA + "/server/graphnous-api/src/main/java/Api.java#L42");
        assertThat(sources.link(scan.id(), path, null)).doesNotContain("#L");
    }

    @Test
    void placesTheFileInTheProjectsDirectoryAndEncodesItsLink() {
        final var sources = sources("https://github.com/acme/monorepo", "services/order service");
        final var path = sources.path(scan.id(), "u1", "src/Orders.java");

        assertThat(path).isEqualTo("services/order service/src/Orders.java");
        assertThat(sources.link(scan.id(), path, 7))
            .isEqualTo("https://github.com/acme/monorepo/blob/" + SHA + "/services/order%20service/src/Orders.java#L7");
    }

    @Test
    void linksNothingElsewhereOrWithoutACommit() {
        assertThat(sources("https://gitlab.com/acme/shop.git", null).link(scan.id(), "src/A.java", 1)).isNull();
        assertThat(sources("https://github.com/acme/shop", null).link(Scan.ScanId.generate(), "src/A.java", 1)).isNull();

        final var unknown = new Scan(scan.id(), scan.projectId(), Scan.ScanStatus.RUNNING, Scan.SourceRevision.requested(null, "main"), NOW, NOW, NOW);
        final var project = new Project(scan.projectId(), "shop", null, "https://github.com/acme/shop", null, System.SystemId.generate(), NOW, NOW);

        assertThat(new RepositorySources(Map.of(scan.id(), unknown), Map.of(scan.id(), project)).link(scan.id(), "src/A.java", 1)).isNull();
    }

    private RepositorySources sources(final String gitUrl, final String path) {
        final var project = new Project(scan.projectId(), "shop", null, gitUrl, path, System.SystemId.generate(), NOW, NOW);

        return new RepositorySources(Map.of(scan.id(), scan), Map.of(scan.id(), project));
    }
}
