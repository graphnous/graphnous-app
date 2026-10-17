package dev.graphnous.github;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.CreateScanCommand;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactAssessor;
import dev.graphnous.application.scan.impact.ImpactComment;
import dev.graphnous.application.scan.impact.ImpactReport;
import dev.graphnous.application.scan.source.ScanSourceService;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What the GitHub App does with what GitHub sends it, for the projects of
 * the repository it is about, in the organization of the installation:
 * <ul>
 *     <li>a push to the default branch scans it, so pull requests have a
 *     current scan to compare with;</li>
 *     <li>a pull request opened, pushed to or reopened scans its head, with
 *     a check run on the commit while it runs;</li>
 *     <li>once that scan is done, its impact report, compared with a scan
 *     of the commit its branch started from, which it scans too when there
 *     is none, completes the check run and goes to the pull request as a
 *     comment, edited on every new push; headed, with an assessor, by its
 *     assessment.</li>
 * </ul>
 * It acts without a user, for the organization, as an API key does.
 */
public class GitHubApp {

    static final Set<String> PULL_REQUEST_ACTIONS = Set.of("opened", "synchronize", "reopened", "ready_for_review");

    private static final Logger log = LoggerFactory.getLogger(GitHubApp.class);

    private final GitHubClient github;
    private final PullRequestScans pullRequestScans;
    private final InstallationOrganizations organizations;
    private final ScanSourceService scanSourceService;
    private final ScanService scanService;
    private final ProjectService projectService;
    private final ImpactAnalysisService impactAnalysisService;
    private final ImpactAssessor assessor;
    private final boolean scanDefaultBranch;
    private final Clock clock;

    GitHubApp(
        final GitHubClient github,
        final PullRequestScans pullRequestScans,
        final InstallationOrganizations organizations,
        final ScanSourceService scanSourceService,
        final ScanService scanService,
        final ProjectService projectService,
        final ImpactAnalysisService impactAnalysisService,
        final ImpactAssessor assessor,
        final boolean scanDefaultBranch,
        final Clock clock
    ) {
        this.github = github;
        this.pullRequestScans = pullRequestScans;
        this.organizations = organizations;
        this.scanSourceService = scanSourceService;
        this.scanService = scanService;
        this.projectService = projectService;
        this.impactAnalysisService = impactAnalysisService;
        this.assessor = assessor;
        this.scanDefaultBranch = scanDefaultBranch;
        this.clock = clock;
    }

    /**
     * @param event the delivery's X-GitHub-Event, such as push
     */
    void handle(final String event, final JsonNode payload) {
        switch (event) {
            case "push" -> push(payload);
            case "pull_request" -> pullRequest(payload);
            default -> log.debug("Ignoring GitHub event {}", event);
        }
    }

    private void push(final JsonNode payload) {
        final var repository = payload.path("repository");
        final var branch = repository.path("default_branch").asString();

        if (!scanDefaultBranch || payload.path("deleted").asBoolean()
            || !payload.path("ref").asString().equals("refs/heads/" + branch)) {
            return;
        }

        final var context = context(payload);
        final var revision = payload.path("after").asString();

        for (final var project : projects(context, repository)) {
            final var scan = scanService.create(context, scanOf(project, revision, branch));

            log.info("Scanning {} of {} for project {}: scan {}", revision, repository.path("full_name").asString(),
                project.name(), scan.id().id());
        }
    }

    private void pullRequest(final JsonNode payload) {
        if (!PULL_REQUEST_ACTIONS.contains(payload.path("action").asString())) {
            return;
        }

        final var installation = payload.path("installation").path("id").asLong();
        final var repository = payload.path("repository");
        final var name = repository.path("full_name").asString();
        final var pullRequest = payload.path("pull_request");
        final var number = pullRequest.path("number").asInt();
        final var headSha = pullRequest.path("head").path("sha").asString();
        // The head's branch, as named in the repository it is of, which for a fork is not this one
        final var headBranch = pullRequest.path("head").path("ref").asString();
        final var baseBranch = pullRequest.path("base").path("ref").asString();
        final var context = context(payload);
        final var projects = projects(context, repository);

        if (projects.isEmpty()) {
            return;
        }

        final var mergeBase = mergeBase(installation, name, pullRequest.path("base").path("sha").asString(), headSha);

        for (final var project : projects) {
            final var checkRun = github.startCheckRun(installation, name, checkRunName(project), headSha);

            final Scan scan;

            try {
                scan = scanService.create(context, scanOf(project, headSha, headBranch));
            } catch (final RuntimeException e) {
                log.warn("Scanning pull request {}#{} for project {} failed", name, number, project.name(), e);
                github.completeCheckRun(installation, name, checkRun, "failure",
                    new GitHubClient.Output("The scan could not start", e.getMessage() == null ? e.toString() : e.getMessage()));
                continue;
            }

            final var baseScan = mergeBase.flatMap(revision -> baseScan(context, project, revision, baseBranch)).orElse(null);

            pullRequestScans.save(new PullRequestScan(
                scan.id(), project.id(), installation, name, number, headSha, baseBranch, checkRun, clock.instant(), baseScan
            ));

            log.info("Scanning pull request {}#{} at {} for project {}: scan {}, compared with {}", name, number, headSha,
                project.name(), scan.id().id(), baseScan == null ? "the newest scan of " + baseBranch : "scan " + baseScan.id());
        }
    }

    /**
     * A scan the App starts, with every installed enhancer and the files
     * and languages its project says.
     */
    private static CreateScanCommand scanOf(final Project project, final String revision, final String branch) {
        return new CreateScanCommand(project.id(), revision, branch, null, null, null, Scan.Trigger.GITHUB_APP);
    }

    /**
     * The commit the pull request's branch started from, if GitHub says.
     */
    private Optional<String> mergeBase(final long installation, final String repository, final String base, final String head) {
        try {
            return github.mergeBase(installation, repository, base, head);
        } catch (final RuntimeException e) {
            log.warn("Finding where the branch of {} at {} started failed", repository, head, e);
            return Optional.empty();
        }
    }

    /**
     * A scan of the project at the commit: one done or under way, or else a
     * new one, of the base branch; none when it cannot start.
     */
    private Optional<Scan.ScanId> baseScan(
        final RequestContext context,
        final Project project,
        final String revision,
        final String baseBranch
    ) {
        try {
            final var scans = scanService.getScansOfRevision(context, project.id(), revision);
            final var reusable = scans.stream()
                .filter(scan -> scan.status() == Scan.ScanStatus.COMPLETED)
                .findFirst()
                .or(() -> scans.stream().filter(scan -> !scan.status().isEndState()).findFirst());

            if (reusable.isPresent()) {
                return reusable.map(Scan::id);
            }

            return Optional.of(scanService.create(context, scanOf(project, revision, baseBranch)).id());
        } catch (final RuntimeException e) {
            log.warn("Scanning {} of project {} to compare with failed", revision, project.name(), e);
            return Optional.empty();
        }
    }

    /**
     * Reports on the pull requests whose head or base the scan is, once
     * both are done.
     */
    void completed(final Scan.ScanId scanId) {
        pullRequestScans.findByScan(scanId).forEach(this::proceed);
    }

    /**
     * Fails the check run of a pull request whose head the scan is; reports
     * on one whose base it is against the newest scan of its base branch.
     */
    void failed(final Scan.ScanId scanId, final String reason) {
        for (final var scan : pullRequestScans.findByScan(scanId)) {
            if (scan.scanId().equals(scanId)) {
                if (pullRequestScans.claim(scanId)) {
                    fail(scan, reason);
                }
            } else {
                proceed(scan);
            }
        }
    }

    /**
     * Reports on the pull requests' scans that are done but were not
     * reported on, as when the server stopped before it did, and ends the
     * check runs of those deleted before they were done.
     */
    void sweep() {
        for (final var scan : pullRequestScans.findAll()) {
            try {
                proceed(scan);
            } catch (final RuntimeException e) {
                log.warn("Checking scan {} of {}#{} failed", scan.scanId().id(), scan.repository(), scan.pullRequest(), e);
            }
        }
    }

    /**
     * What to compare a pull request's head with: a scan, with what to say
     * about it; none while it is not done.
     */
    private record Base(Scan scan, String note) {
    }

    /**
     * Reports on the pull request's scan once its head and its base are
     * done, fails it when its head failed, and ends it when its head was
     * deleted: whichever this caller is the one to do.
     */
    private void proceed(final PullRequestScan scan) {
        final var context = context(scan.installation());
        final var head = find(context, scan.scanId());

        if (head.isEmpty()) {
            if (pullRequestScans.claim(scan.scanId())) {
                github.completeCheckRun(scan.installation(), scan.repository(), scan.checkRun(), "neutral",
                    new GitHubClient.Output("The scan was deleted", "The scan was deleted before it was done, so there is no report."));
            }
            return;
        }

        if (head.get().status() == Scan.ScanStatus.FAILED) {
            if (pullRequestScans.claim(scan.scanId())) {
                fail(scan, null);
            }
            return;
        }

        if (head.get().status() != Scan.ScanStatus.COMPLETED) {
            return;
        }

        final Base base;

        try {
            base = base(context, scan);
        } catch (final RuntimeException e) {
            if (pullRequestScans.claim(scan.scanId())) {
                failReport(scan, e);
            }
            return;
        }

        if (base != null && pullRequestScans.claim(scan.scanId())) {
            report(scan, base);
        }
    }

    /**
     * The scan of where the branch started, once done; or, without one,
     * the newest completed scan of the base branch, with a note why; null
     * while the scan of where the branch started is under way.
     */
    private Base base(final RequestContext context, final PullRequestScan scan) {
        if (scan.baseScan() != null) {
            final var started = find(context, scan.baseScan());

            if (started.isPresent() && !started.get().status().isEndState()) {
                return null;
            }

            if (started.isPresent() && started.get().status() == Scan.ScanStatus.COMPLETED) {
                return new Base(started.get(), "Compared with the commit the branch started from, so what `"
                    + scan.baseBranch() + "` got since is left out.");
            }
        }

        final var why = scan.baseScan() == null
            ? "Graphnous could not tell where the branch started"
            : "The scan of the commit the branch started from failed";

        return new Base(
            scanService.getLatestCompletedScan(context, scan.projectId(), scan.baseBranch()).orElse(null),
            why + ", so this compares with the newest completed scan of `" + scan.baseBranch()
                + "`: what that has and the branch does not shows as removed."
        );
    }

    private Optional<Scan> find(final RequestContext context, final Scan.ScanId scanId) {
        try {
            return Optional.of(scanService.getScan(context, scanId));
        } catch (final NotFoundException e) {
            return Optional.empty();
        }
    }

    private void fail(final PullRequestScan scan, final String reason) {
        github.completeCheckRun(scan.installation(), scan.repository(), scan.checkRun(), "failure", new GitHubClient.Output(
            "The scan failed",
            reason == null ? "The scan failed; its steps in Graphnous say why." : reason
        ));
    }

    private void failReport(final PullRequestScan scan, final RuntimeException e) {
        log.warn("Reporting scan {} on {}#{} failed", scan.scanId().id(), scan.repository(), scan.pullRequest(), e);
        github.completeCheckRun(scan.installation(), scan.repository(), scan.checkRun(), "failure",
            new GitHubClient.Output("The impact report failed", e.getMessage() == null ? e.toString() : e.getMessage()));
    }

    private void report(final PullRequestScan scan, final Base base) {
        try {
            if (pullRequestScans.isSuperseded(scan)) {
                github.completeCheckRun(scan.installation(), scan.repository(), scan.checkRun(), "neutral",
                    new GitHubClient.Output("A newer push replaced this scan", "The pull request's newer commit has its own report."));
                return;
            }

            if (base.scan() == null) {
                github.completeCheckRun(scan.installation(), scan.repository(), scan.checkRun(), "neutral", new GitHubClient.Output(
                    "No scan of " + scan.baseBranch() + " to compare with",
                    "Graphnous compares a pull request with the commit its branch started from, or else the newest completed scan "
                        + "of the branch it merges into. Scan `" + scan.baseBranch() + "` first; with the App, a push to the "
                        + "default branch scans it."
                ));
                return;
            }

            final var context = context(scan.installation());
            final var report = impactAnalysisService.analyze(context, base.scan().id(), scan.scanId(), null);
            final var comment = ImpactComment.render(
                report,
                projectNames(context, scan, report),
                assessment(context, scan, report),
                impactAnalysisService.sources(context, report),
                base.note()
            );
            final var marker = marker(scan.projectId());

            github.findComment(scan.installation(), scan.repository(), scan.pullRequest(), marker).ifPresentOrElse(
                id -> github.editComment(scan.installation(), scan.repository(), id, marker + "\n" + comment),
                () -> github.comment(scan.installation(), scan.repository(), scan.pullRequest(), marker + "\n" + comment)
            );

            github.completeCheckRun(
                scan.installation(),
                scan.repository(),
                scan.checkRun(),
                // Reported, not judged: a broken call may be meant, as when its caller changes too
                report.brokenCalls().isEmpty() ? "success" : "neutral",
                new GitHubClient.Output(title(report), comment)
            );
        } catch (final RuntimeException e) {
            failReport(scan, e);
        }
    }

    /**
     * What the assessor makes of the report, or what kept it from it; none
     * without an assessor.
     */
    private String assessment(final RequestContext context, final PullRequestScan scan, final ImpactReport report) {
        if (assessor == null) {
            return null;
        }

        try {
            return assessor.assess(context, report);
        } catch (final RuntimeException e) {
            // The report still holds without it
            log.warn("Assessing scan {} of {}#{} failed", scan.scanId().id(), scan.repository(), scan.pullRequest(), e);
            return "_The assessment could not be written: " + e.getMessage() + "_";
        }
    }

    /**
     * What marks the comment of a project's reports on a pull request, as a
     * repository's projects each have one.
     */
    static String marker(final Project.ProjectId projectId) {
        return "<!-- graphnous-impact project=" + projectId.id() + " -->";
    }

    static String checkRunName(final Project project) {
        return "Graphnous impact: " + project.name();
    }

    static String title(final ImpactReport report) {
        final var broken = report.brokenCalls().size();
        final var used = report.changes().stream().filter(change -> change.usageCount() > 0).count();

        if (broken == 0 && used == 0) {
            return "Nothing else uses what changed";
        }

        final var updated = report.changes().stream().mapToInt(ImpactReport.Change::updatedUsageCount).sum();
        final var open = report.changes().stream().mapToInt(ImpactReport.Change::openUsageCount).sum();

        return (broken == 1 ? "1 broken call" : broken + " broken calls")
            + ", " + (used == 1 ? "1 change in use" : used + " changes in use")
            + (updated == 0 ? "" : open == 0 ? ", all places updated" : ", " + (open == 1 ? "1 place" : open + " places") + " not updated");
    }

    private HashMap<Project.ProjectId, String> projectNames(
        final RequestContext context,
        final PullRequestScan scan,
        final ImpactReport report
    ) {
        final var names = new HashMap<Project.ProjectId, String>();

        names.put(scan.projectId(), projectService.getProject(context, scan.projectId()).name());
        report.projects().forEach(project -> names.computeIfAbsent(
            project.projectId(),
            id -> projectService.getProject(context, id).name()
        ));

        return names;
    }

    private List<Project> projects(final RequestContext context, final JsonNode repository) {
        final var projects = scanSourceService.getRepositoryProjects(context, repository.path("clone_url").asString());

        if (projects.isEmpty()) {
            log.info("No project is repository {}", repository.path("full_name").asString());
        }

        return projects;
    }

    private RequestContext context(final JsonNode payload) {
        return context(payload.path("installation").path("id").asLong());
    }

    private RequestContext context(final long installation) {
        return new RequestContext(new UserContext(null), new OrganizationContext(organizations.organizationOf(installation)));
    }
}
