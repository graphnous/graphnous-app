package dev.graphnous.scanner.java.targetdetector;

import dev.graphnous.core.model.ScanTarget;
import dev.graphnous.scanner.java.maven.MavenPomFinder;
import dev.graphnous.scanner.targetdetector.ScanTargetDetector;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MavenScanTargetDetector implements ScanTargetDetector {

    @Override
    public List<ScanTarget> detect(final Path repository) {
        final var projects = MavenPomFinder.find(repository)
            .stream()
            .map(Path::getParent)
            .collect(Collectors.toSet());

        return projects.stream()
            .filter(project -> !isModuleOfAnotherProject(project, projects))
            .sorted()
            .map(project -> createTarget(repository, project))
            .toList();
    }

    /**
     * A pom inside the directory of another pom is a module of that
     * project; it is scanned as part of it rather than as its own target.
     */
    private boolean isModuleOfAnotherProject(
        final Path project,
        final Set<Path> projects
    ) {
        for (var parent = project.getParent(); parent != null; parent = parent.getParent()) {
            if (projects.contains(parent)) {
                return true;
            }
        }

        return false;
    }

    private ScanTarget createTarget(
        final Path repository,
        final Path project
    ) {
        final var target = new ScanTarget();

        final var path = repository
            .relativize(project)
            .toString();

        target.setPath(
            path.isBlank() ? "." : path
        );

        target.setLanguage(ScanTarget.Language.JAVA);
        target.setBuildSystem(ScanTarget.BuildSystem.MAVEN);

        return target;
    }
}
