package dev.graphnous.application.scan.impact;

import dev.graphnous.application.project.GitRepositories;
import dev.graphnous.application.scan.source.SourcePaths;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Where a report's places are in their repositories: the file's path, with
 * its project's directory, target and module; and, for a repository on
 * GitHub, a link to the line at the commit the scan is of.
 *
 * @param scans    the scans the places are in
 * @param projects the project of each scan
 */
record RepositorySources(Map<Scan.ScanId, Scan> scans, Map<Scan.ScanId, Project> projects) implements ImpactComment.Sources {

    RepositorySources {
        scans = Map.copyOf(scans);
        projects = Map.copyOf(projects);
    }

    @Override
    public String path(final Scan.ScanId scanId, final String nodeId, final String file) {
        final var project = projects.get(scanId);

        return SourcePaths.inRepository(project == null ? null : project.path(), nodeId, file);
    }

    @Override
    public String link(final Scan.ScanId scanId, final String path, final Integer line) {
        final var scan = scans.get(scanId);
        final var project = projects.get(scanId);

        if (path == null || scan == null || project == null || project.gitUrl() == null
            || scan.revision() == null || scan.revision().revision() == null) {
            return null;
        }

        final var repository = GitRepositories.key(project.gitUrl());

        if (!repository.startsWith("github.com/")) {
            return null;
        }

        return "https://" + repository + "/blob/" + scan.revision().revision() + "/" + encode(path)
            + (line == null ? "" : "#L" + line);
    }

    private static String encode(final String path) {
        return Arrays.stream(path.split("/"))
            .map(part -> URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"))
            .collect(Collectors.joining("/"));
    }
}
