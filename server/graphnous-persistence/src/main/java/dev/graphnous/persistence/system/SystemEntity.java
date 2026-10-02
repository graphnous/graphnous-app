package dev.graphnous.persistence.system;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "systems")
public class SystemEntity {

    @Id
    private UUID id;

    @Column
    private String name;

    @Column()
    private String description;

    @Column(name = "organization_id", updatable = false)
    private UUID organizationId;

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

    public void setOrganizationId(final UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getOrganizationId() {
        return this.organizationId;
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