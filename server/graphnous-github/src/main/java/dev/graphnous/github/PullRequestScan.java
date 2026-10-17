package dev.graphnous.github;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;

import java.time.Instant;

/**
 * A scan of a pull request's head, for one of the repository's projects,
 * whose impact report goes to the pull request once the scan is done.
 *
 * @param installation the App's installation that asked for it
 * @param repository   the repository's full name, such as acme/shop
 * @param baseBranch   the branch the pull request merges into, whose newest
 *                     completed scan the report compares with
 * @param checkRun     the check run that shows it on the commit
 */
record PullRequestScan(
    Scan.ScanId scanId,
    Project.ProjectId projectId,
    long installation,
    String repository,
    int pullRequest,
    String headSha,
    String baseBranch,
    long checkRun,
    Instant createdAt
) {
}
