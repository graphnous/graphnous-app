package dev.graphnous.persistence.scan;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "scans")
public class ScanEntity {

    @Id
    private UUID id;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column()
    @Enumerated(EnumType.STRING)
    private Scan.ScanStatus status;

    @Column(name = "git_revision")
    private String revision;

    @Column(name = "git_branch")
    private String branch;

    @Column(name = "git_requested_revision")
    private String requestedRevision;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * The names of the enhancers chosen when the scan was created; null
     * when none were chosen.
     */
    @Convert(converter = EnhancerNamesConverter.class)
    @Column(name = "enhancers", updatable = false, columnDefinition = "TEXT")
    private Set<String> enhancers;

    /**
     * The patterns of the files left out of the scan; null for none.
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "excludes", updatable = false, columnDefinition = "TEXT")
    private List<String> excludes;

    public void setExcludes(final List<String> excludes) {
        this.excludes = excludes;
    }

    public List<String> getExcludes() {
        return this.excludes;
    }

    public void setEnhancers(final Set<String> enhancers) {
        this.enhancers = enhancers;
    }

    public Set<String> getEnhancers() {
        return this.enhancers;
    }

    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getStartedAt() {
        return this.startedAt;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return this.id;
    }

    public void setProjectId(final UUID projectId) {
        this.projectId = projectId;
    }

    public UUID getProjectId() {
        return this.projectId;
    }

    public void setStatus(final Scan.ScanStatus status) {
        this.status = status;
    }

    public Scan.ScanStatus getStatus() {
        return this.status;
    }

    public void setRevision(final String revision) {
        this.revision = revision;
    }

    public String getRevision() {
        return this.revision;
    }

    public void setRequestedRevision(final String requestedRevision) {
        this.requestedRevision = requestedRevision;
    }

    public String getRequestedRevision() {
        return this.requestedRevision;
    }

    public void setBranch(final String branch) {
        this.branch = branch;
    }

    public String getBranch() {
        return this.branch;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setUpdatedAt(final Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

}
