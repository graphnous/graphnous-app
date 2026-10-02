package dev.graphnous.persistence.project;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;

class ProjectMapper {

    public ProjectEntity fromDomain(final Project domain) {
        final var entity = new ProjectEntity();

        entity.setId(domain.id().id());

        entity.setName(domain.name());
        entity.setDescription(domain.description());

        entity.setGitUrl(domain.gitUrl());
        entity.setPath(domain.path());

        entity.setSystemId(domain.systemId().id());

        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());

        return entity;
    }

    public Project toDomain(final ProjectEntity entity) {
        return new Project(
            new Project.ProjectId(entity.getId()),
            entity.getName(),
            entity.getDescription(),
            entity.getGitUrl(),
            entity.getPath(),
            new System.SystemId(entity.getSystemId()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
