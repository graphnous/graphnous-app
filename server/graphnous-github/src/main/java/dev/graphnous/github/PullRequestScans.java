package dev.graphnous.github;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The pull requests' scans that are not done yet, in the server's
 * database, so a restart does not lose them.
 */
class PullRequestScans {

    private final JdbcClient jdbc;

    PullRequestScans(final JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void save(final PullRequestScan scan) {
        jdbc.sql("""
                INSERT INTO github_pull_request_scans
                    (scan_id, project_id, installation_id, repository, pull_request, head_sha, base_branch, check_run_id, created_at,
                     base_scan_id)
                VALUES (:scanId, :projectId, :installation, :repository, :pullRequest, :headSha, :baseBranch, :checkRun, :createdAt,
                        :baseScanId)
                """)
            .param("scanId", scan.scanId().id())
            .param("projectId", scan.projectId().id())
            .param("installation", scan.installation())
            .param("repository", scan.repository())
            .param("pullRequest", scan.pullRequest())
            .param("headSha", scan.headSha())
            .param("baseBranch", scan.baseBranch())
            .param("checkRun", scan.checkRun())
            .param("createdAt", Timestamp.from(scan.createdAt()))
            .param("baseScanId", scan.baseScan() == null ? null : scan.baseScan().id())
            .update();
    }

    /**
     * Those whose head or base the scan is, not reported on yet.
     */
    List<PullRequestScan> findByScan(final Scan.ScanId scanId) {
        return jdbc.sql("SELECT * FROM github_pull_request_scans WHERE scan_id = :scanId OR base_scan_id = :scanId ORDER BY created_at")
            .param("scanId", scanId.id())
            .query(PullRequestScans::read)
            .list();
    }

    Optional<PullRequestScan> find(final Scan.ScanId scanId) {
        return jdbc.sql("SELECT * FROM github_pull_request_scans WHERE scan_id = :scanId")
            .param("scanId", scanId.id())
            .query(PullRequestScans::read)
            .optional();
    }

    /**
     * Whether a scan of the same project for the same pull request came
     * after it, of a newer push, whose report replaces its own.
     */
    boolean isSuperseded(final PullRequestScan scan) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM github_pull_request_scans
                WHERE repository = :repository AND pull_request = :pullRequest AND project_id = :projectId
                  AND created_at > :createdAt
                """)
            .param("repository", scan.repository())
            .param("pullRequest", scan.pullRequest())
            .param("projectId", scan.projectId().id())
            .param("createdAt", Timestamp.from(scan.createdAt()))
            .query(Long.class)
            .single() > 0;
    }

    /**
     * Every scan not reported on yet, oldest first.
     */
    List<PullRequestScan> findAll() {
        return jdbc.sql("SELECT * FROM github_pull_request_scans ORDER BY created_at")
            .query(PullRequestScans::read)
            .list();
    }

    /**
     * Takes the scan to report on, once: whether this caller got it, as
     * what reports on scans may run more than once at the same time.
     */
    boolean claim(final Scan.ScanId scanId) {
        return jdbc.sql("DELETE FROM github_pull_request_scans WHERE scan_id = :scanId")
            .param("scanId", scanId.id())
            .update() == 1;
    }

    private static PullRequestScan read(final ResultSet row, final int number) throws SQLException {
        return new PullRequestScan(
            new Scan.ScanId(row.getObject("scan_id", UUID.class)),
            new Project.ProjectId(row.getObject("project_id", UUID.class)),
            row.getLong("installation_id"),
            row.getString("repository"),
            row.getInt("pull_request"),
            row.getString("head_sha"),
            row.getString("base_branch"),
            row.getLong("check_run_id"),
            row.getTimestamp("created_at").toInstant(),
            row.getObject("base_scan_id", UUID.class) == null ? null : new Scan.ScanId(row.getObject("base_scan_id", UUID.class))
        );
    }
}
