package dev.graphnous.persistence.scan;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.persistence.system.SystemEntity;

import java.util.Set;

public class ScanMapper {

    public ScanEntity fromDomain(final dev.graphnous.domain.scan.Scan domain) {
        final var entity = new ScanEntity();

        entity.setId(domain.id().id());

        entity.setProjectId(domain.projectId().id());

        entity.setStatus(domain.status());

        entity.setBranch(domain.revision().branch());
        entity.setRevision(domain.revision().revision());
        entity.setRequestedRevision(domain.revision().requestedRevision());

        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        entity.setStartedAt(domain.startedAt());

        entity.setEnhancers(domain.enhancers());
        // Nothing rather than an empty list, as for the scans before there were any
        entity.setExcludes(domain.excludes().isEmpty() ? null : domain.excludes());
        entity.setLanguages(domain.languages().isEmpty() ? null : domain.languages().stream().sorted().toList());

        return entity;
    }

    public dev.graphnous.domain.scan.Scan toDomain(final ScanEntity entity) {
        return new dev.graphnous.domain.scan.Scan(
            new Scan.ScanId(entity.getId()),
            new Project.ProjectId(entity.getProjectId()),
            entity.getStatus(),
            new Scan.SourceRevision(
                entity.getRevision(),
                entity.getBranch(),
                entity.getRequestedRevision()
            ),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getStartedAt(),
            entity.getEnhancers(),
            entity.getExcludes(),
            entity.getLanguages() == null ? null : Set.copyOf(entity.getLanguages())
        );
    }
}
