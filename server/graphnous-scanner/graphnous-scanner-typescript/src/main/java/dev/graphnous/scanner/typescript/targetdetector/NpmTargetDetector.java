package dev.graphnous.scanner.typescript.targetdetector;

import dev.graphnous.scanner.model.ScanTarget;
import dev.graphnous.scanner.targetdetector.ScanTargetDetector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NpmTargetDetector implements ScanTargetDetector {

    private static final String PACKAGE_JSON = "package.json";

    private static final String NODE_MODULES = "node_modules";

    @Override
    public List<ScanTarget> detect(final Path repository) {
        final var projects = findProjects(repository);

        return projects.stream()
            .filter(project -> !isPackageOfAnotherProject(project, projects))
            .sorted()
            .map(project -> createTarget(repository, project))
            .toList();
    }

    private Set<Path> findProjects(final Path repository) {
        final var projects = new HashSet<Path>();

        try {
            Files.walkFileTree(repository, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(
                    final Path directory,
                    final BasicFileAttributes attributes
                ) {
                    return NODE_MODULES.equals(fileName(directory))
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(
                    final Path file,
                    final BasicFileAttributes attributes
                ) {
                    if (attributes.isRegularFile()
                        && PACKAGE_JSON.equals(fileName(file))) {
                        projects.add(file.getParent());
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to detect npm projects in " + repository,
                e
            );
        }

        return projects;
    }

    /**
     * A package inside the directory of another package (such as a
     * workspace package in a monorepo) is part of that project; it is
     * scanned with it rather than as its own target.
     */
    private boolean isPackageOfAnotherProject(
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

    private static String fileName(final Path path) {
        final var name = path.getFileName();

        return name == null ? "" : name.toString();
    }

    private ScanTarget createTarget(
        final Path repository,
        final Path project
    ) {
        final var target = new ScanTarget();

        final var targetPath = repository
            .relativize(project)
            .toString();

        target.setPath(
            targetPath.isBlank()
                ? "."
                : targetPath
        );

        target.setLanguage(
            ScanTarget.Language.TYPESCRIPT
        );

        target.setBuildSystem(
            ScanTarget.BuildSystem.NPM
        );

        return target;
    }

}
