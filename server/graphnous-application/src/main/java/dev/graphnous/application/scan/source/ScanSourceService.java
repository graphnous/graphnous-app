package dev.graphnous.application.scan.source;

import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.GitRepositories;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Finds a source file of a repository in the newest completed scan of its
 * project, as someone working on the file knows it: by the repository's
 * git url and the file's path in it. For a user who may read the scan: the
 * systems, projects and scans are got through their services, which check
 * the user's permissions and organization.
 */
public class ScanSourceService {

    /**
     * How many systems, projects or scans are read at once.
     */
    private static final int PAGE_SIZE = 100;

    private final SystemService systemService;
    private final ProjectService projectService;
    private final ScanService scanService;
    private final ScanSourceRepository scanSourceRepository;

    public ScanSourceService(
        final SystemService systemService,
        final ProjectService projectService,
        final ScanService scanService,
        final ScanSourceRepository scanSourceRepository
    ) {
        this.systemService = systemService;
        this.projectService = projectService;
        this.scanService = scanService;
        this.scanSourceRepository = scanSourceRepository;
    }

    /**
     * @param gitUrl the url of the repository, in any form git takes, such
     *               as {@code https://github.com/org/repo} or
     *               {@code git@github.com:org/repo.git}
     * @param path   the path of the file in the repository
     * @param symbol the name of what the file declares to find, such as
     *               {@code OrderService}, {@code create} or
     *               {@code OrderService.create}, ignoring case; everything
     *               it declares when null
     * @throws ValidationException when the url or path is missing
     * @throws NotFoundException   when no project of the user's is that
     *                             repository, or the directory of it the
     *                             file is in; when that project has no
     *                             completed scan; or when its newest one
     *                             does not have the file
     */
    public ScanSource.Found find(
        final RequestContext context,
        final String gitUrl,
        final String path,
        final String symbol
    ) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new ValidationException("gitUrl is required");
        }

        final var file = normalizePath(path);

        if (file.isEmpty()) {
            throw new ValidationException("path is required");
        }

        final var repository = GitRepositories.key(gitUrl);
        final var projects = projects(context, repository, file);

        if (projects.isEmpty()) {
            throw new NotFoundException("No project is repository " + gitUrl.trim() + ", or the directory of it " + file + " is in");
        }

        // Of a repository's projects, the one of the deepest directory the
        // file is in: a repository can be a project, and a directory of it another
        final var project = projects.getFirst();
        final var scan = newestCompletedScan(context, project)
            .orElseThrow(() -> new NotFoundException("Project " + project.name() + " has no completed scan"));

        final var inProject = relativeTo(file, project.path());
        final var found = scanSourceRepository.findFilesEndingPath(scan.id(), inProject)
            .stream()
            .filter(candidate -> inProject.equals(join(candidate.target(), candidate.path()))
                || inProject.equals(join(candidate.target(), candidate.module(), candidate.path())))
            .findFirst()
            .orElseThrow(() -> new NotFoundException(
                "The newest completed scan of project " + project.name() + " has no file " + file
                    + (scan.revision() == null || scan.revision().revision() == null
                        ? ""
                        : ", at revision " + scan.revision().revision())
                    + ": it may be newer than the scan, left out of it, or in a language it did not scan"
            ));

        return new ScanSource.Found(project, scan, file, found, declarations(found, symbol));
    }

    /**
     * What the file declares that has the symbol's name; for a name with a
     * dot, such as {@code OrderService.create}, the member of that class.
     */
    static List<ScanSource.Declaration> declarations(final ScanSource.File file, final String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return file.declarations();
        }

        final var wanted = symbol.trim().toLowerCase(Locale.ROOT);
        final var dot = wanted.lastIndexOf('.');

        return file.declarations()
            .stream()
            .filter(declaration -> {
                final var name = lower(declaration.name());

                if (name.equals(wanted) || lower(declaration.qualifiedName()).equals(wanted)) {
                    return true;
                }

                if (dot < 0 || !name.equals(wanted.substring(dot + 1))) {
                    return false;
                }

                // Class.member: the member's class by its simple or qualified name
                final var owner = lower(declaration.className());
                final var wantedOwner = wanted.substring(0, dot);

                return owner.equals(wantedOwner) || owner.endsWith("." + wantedOwner);
            })
            .toList();
    }

    /**
     * The user's projects of the repository, by system and name: one, or
     * several when they are directories of it.
     *
     * @param gitUrl the url of the repository, in any form git takes, such
     *               as {@code git@github.com:acme/shop.git}
     */
    public List<Project> getRepositoryProjects(final RequestContext context, final String gitUrl) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new ValidationException("gitUrl is required");
        }

        return repositoryProjects(context, GitRepositories.key(gitUrl));
    }

    private List<Project> repositoryProjects(final RequestContext context, final String repository) {
        final var projects = new ArrayList<Project>();

        all(page -> systemService.getSystems(context, query(page, new Sort("name", Sort.Direction.ASC))))
            .forEach(system -> all(page -> projectService.getProjects(
                    context, query(page, new Sort("name", Sort.Direction.ASC)), system.id()
                ))
                .stream()
                .filter(project -> project.gitUrl() != null && repository.equals(GitRepositories.key(project.gitUrl())))
                .forEach(projects::add));

        return projects;
    }

    /**
     * The user's projects of the repository whose directory the file is in,
     * the deepest directory first.
     */
    private List<Project> projects(final RequestContext context, final String repository, final String file) {
        final var projects = new ArrayList<>(repositoryProjects(context, repository)
            .stream()
            .filter(project -> isIn(file, normalizePath(project.path())))
            .toList());

        projects.sort(Comparator.comparingInt((Project project) -> normalizePath(project.path()).length()).reversed());

        return projects;
    }

    private Optional<Scan> newestCompletedScan(final RequestContext context, final Project project) {
        for (var page = 0; ; page++) {
            final var scans = scanService.getScans(context, query(page, Sort.defaultSort()), project.id());

            final var completed = scans.content()
                .stream()
                .filter(scan -> scan.status() == Scan.ScanStatus.COMPLETED)
                .findFirst();

            if (completed.isPresent() || page + 1 >= scans.totalPages()) {
                return completed;
            }
        }
    }


    /**
     * The path with forward slashes and without a leading {@code ./} or
     * {@code /}, a trailing {@code /} or {@code .} parts; empty for the
     * root of the repository.
     */
    static String normalizePath(final String path) {
        if (path == null) {
            return "";
        }

        return String.join("/", Arrays.stream(path.trim().replace('\\', '/').split("/"))
            .filter(part -> !part.isEmpty() && !part.equals("."))
            .toList());
    }

    private static boolean isIn(final String file, final String directory) {
        return directory.isEmpty() || file.startsWith(directory + "/");
    }

    private static String relativeTo(final String file, final String directory) {
        final var normalized = normalizePath(directory);

        return normalized.isEmpty() ? file : file.substring(normalized.length() + 1);
    }

    private static String join(final String... parts) {
        return normalizePath(String.join("/", Arrays.stream(parts).map(part -> part == null ? "" : part).toList()));
    }

    private static String lower(final String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static PageQuery query(final int page, final Sort sort) {
        return new PageQuery(page, PAGE_SIZE, sort);
    }

    private static <T> List<T> all(final IntFunction<Page<T>> pages) {
        final var all = new ArrayList<T>();

        for (var page = 0; ; page++) {
            final var current = pages.apply(page);
            all.addAll(current.content());

            if (page + 1 >= current.totalPages()) {
                return all;
            }
        }
    }
}
