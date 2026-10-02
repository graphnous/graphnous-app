package dev.graphnous.persistence.scan;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.system.SystemEntity;

public class ScanMapper {

    public ScanEntity fromDomain(final dev.graphnous.domain.scan.Scan domain) {
        final var entity = new ScanEntity();

        entity.setId(domain.id().id());

        entity.setProjectId(domain.projectId().id());

        entity.setStatus(domain.status());

        entity.setBranch(domain.revision().branch());
        entity.setRevision(domain.revision().revision());

        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        entity.setStartedAt(domain.startedAt());

        return entity;
    }

    public dev.graphnous.domain.scan.Scan toDomain(final ScanEntity entity) {
        return new dev.graphnous.domain.scan.Scan(
            new Scan.ScanId(entity.getId()),
            new Project.ProjectId(entity.getProjectId()),
            entity.getStatus(),
            new Scan.SourceRevision(
                entity.getRevision(),
                entity.getBranch()
            ),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getStartedAt()
        );
    }
}
