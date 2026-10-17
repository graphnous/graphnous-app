package dev.graphnous.api.scan.v1;

import dev.graphnous.api.ai.ImpactAgent;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.impact.ImpactAnalysisService;
import dev.graphnous.application.scan.impact.ImpactComment;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * What a scan's change affects, as a comment for its pull request, such as
 * a CI job posts: compared with a base scan, by default the newest
 * completed scan of main.
 * <p>
 * Not in the published OpenAPI spec yet: it answers in Markdown.
 */
@RestController
@RequestMapping("/api/v1/scans")
public class ScanImpactController {

    static final String MARKDOWN = "text/markdown;charset=UTF-8";

    private static final Logger log = LoggerFactory.getLogger(ScanImpactController.class);

    private final RequestContextProvider contextProvider;
    private final ScanService scanService;
    private final ProjectService projectService;
    private final ImpactAnalysisService impactAnalysisService;
    private final ImpactAgent impactAgent;

    public ScanImpactController(
        final RequestContextProvider contextProvider,
        final ScanService scanService,
        final ProjectService projectService,
        final ImpactAnalysisService impactAnalysisService,
        final ImpactAgent impactAgent
    ) {
        this.contextProvider = contextProvider;
        this.scanService = scanService;
        this.projectService = projectService;
        this.impactAnalysisService = impactAnalysisService;
        this.impactAgent = impactAgent;
    }

    /**
     * The impact report of the head scan, from the base scan, as a comment
     * in GitHub's Markdown.
     *
     * @param base         the scan to compare from; the newest completed
     *                     scan of the head's project on the base branch
     *                     when left out
     * @param baseRevision the commit to compare from, such as where a pull
     *                     request's branch started: its completed scan; the
     *                     newest completed scan of the base branch, with a
     *                     note, when it has none
     * @param baseBranch   the branch whose newest completed scan is the base
     * @param assess     whether the impact agent heads the comment with its
     *                   assessment of the report, which takes a while; the
     *                   comment says when it could not be written
     * @return 404 when the base branch has no completed scan
     */
    @GetMapping(value = "/{head}/impact-comment", produces = MARKDOWN)
    public ResponseEntity<String> impactComment(
        @PathVariable("head") final UUID head,
        @RequestParam(value = "base", required = false) final UUID base,
        @RequestParam(value = "baseRevision", required = false) final String baseRevision,
        @RequestParam(value = "baseBranch", defaultValue = "main") final String baseBranch,
        @RequestParam(value = "assess", defaultValue = "false") final boolean assess
    ) {
        final var context = contextProvider.get();
        final var headScan = scanService.getScan(context, new Scan.ScanId(head));
        final var ofRevision = base != null || baseRevision == null || baseRevision.isBlank()
            ? Optional.<Scan>empty()
            : scanService.getScansOfRevision(context, headScan.projectId(), baseRevision.trim())
                .stream()
                .filter(scan -> scan.status() == Scan.ScanStatus.COMPLETED)
                .findFirst();

        final String note;

        if (ofRevision.isPresent()) {
            note = "Compared with the commit the branch started from, so what `" + baseBranch + "` got since is left out.";
        } else if (base == null && baseRevision != null && !baseRevision.isBlank()) {
            note = "There is no completed scan of `" + abbreviate(baseRevision) + "`, where the branch started, so this compares "
                + "with the newest completed scan of `" + baseBranch + "`: what that has and the branch does not shows as removed.";
        } else {
            note = null;
        }

        final var baseId = base != null
            ? new Scan.ScanId(base)
            : ofRevision.map(Scan::id).or(() -> scanService.getLatestCompletedScan(context, headScan.projectId(), baseBranch)
                    .map(Scan::id))
                .orElseThrow(() -> new NotFoundException(
                    "No completed scan of branch " + baseBranch + " to compare with: scan it first"
                ));

        final var report = impactAnalysisService.analyze(context, baseId, headScan.id(), null);

        final var projects = new HashMap<Project.ProjectId, String>();
        projects.put(headScan.projectId(), projectService.getProject(context, headScan.projectId()).name());
        report.projects().forEach(project -> projects.computeIfAbsent(
            project.projectId(),
            id -> projectService.getProject(context, id).name()
        ));

        String assessment = null;

        if (assess) {
            try {
                assessment = impactAgent.assess(context, report);
            } catch (final RuntimeException e) {
                // The report still holds without it
                log.warn("Assessing the impact of scan {} failed", head, e);
                assessment = "_The assessment could not be written: " + e.getMessage() + "_";
            }
        }

        return ResponseEntity.ok(ImpactComment.render(report, projects, assessment, impactAnalysisService.sources(context, report), note));
    }

    private static String abbreviate(final String revision) {
        final var trimmed = revision.trim();

        return trimmed.substring(0, Math.min(7, trimmed.length()));
    }
}
