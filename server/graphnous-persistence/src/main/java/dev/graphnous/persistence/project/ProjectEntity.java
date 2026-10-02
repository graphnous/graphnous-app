package dev.graphnous.persistence.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectEntity {

    @Id
    private UUID id;

    @Column
    private String name;

    @Column()
    private String description;

    @Column(name = "git_url", updatable = false)
    private String gitUrl;

    @Column()
    private String path;

    @Column(name = "system_id", updatable = false)
    private UUID systemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void setId(final UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return this.id;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setGitUrl(final String gitUrl) {
        this.gitUrl = gitUrl;
    }

    public String getGitUrl() {
        return this.gitUrl;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }

    public void setSystemId(final UUID systemId) {
        this.systemId = systemId;
    }

    public UUID getSystemId() {
        return this.systemId;
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