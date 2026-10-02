package dev.graphnous.persistence.project;

import dev.graphnous.application.exception.GraphnousException;
import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.project.ProjectRepository;
import dev.graphnous.application.project.exception.ProjectNotFoundException;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;
    private final ProjectNeo4jRepository neo4jRepository;

    private final ProjectMapper projectMapper;

    public ProjectRepositoryImpl(
        final ProjectJpaRepository jpaRepository,
        final ProjectNeo4jRepository neo4jRepository
    ) {
        this.jpaRepository = jpaRepository;
        this.neo4jRepository = neo4jRepository;

        this.projectMapper = new ProjectMapper();
    }

    @Override
    public Project save(final Project project) {
        final var entity = this.jpaRepository.save(
            this.projectMapper.fromDomain(project)
        );

        this.neo4jRepository.saveProject(
            project.id().id(),
            project.name(),
            project.systemId().id()
        );

        return this.projectMapper.toDomain(entity);
    }

    @Override
    public Page<Project> findAll(
        final System.SystemId systemId,
        final PageQuery pageQuery
    ) {
        final var sort = switch (pageQuery.sort().direction()) {
            case ASC -> org.springframework.data.domain.Sort
                .by(pageQuery.sort().property())
                .ascending();

            case DESC -> org.springframework.data.domain.Sort
                .by(pageQuery.sort().property())
                .descending();
        };


        final var pageable = org.springframework.data.domain.PageRequest.of(
            pageQuery.page(),
            pageQuery.size(),
            sort
        );

        final var result = this.jpaRepository.findAllBySystemId(
            systemId.id(),
            pageable
        );

        return new Page<>(
            result
                .stream()
                .map(this.projectMapper::toDomain)
                .toList(),
            result.getNumber(),
            result.getSize(),
            Math.toIntExact(result.getTotalElements()),
            result.getTotalPages()
        );
    }

    @Override
    public Project findById(
        final Project.ProjectId id
    ) {
        final var entity = this.jpaRepository.findById(id.id())
            .orElseThrow(() -> new ProjectNotFoundException(id));

        return this.projectMapper.toDomain(entity);
    }

    @Override
    public void delete(
        final System.SystemId systemId,
        final Project.ProjectId id
    ) {
        final var entity = this.jpaRepository.findBySystemIdAndId(systemId.id(), id.id())
            .orElseThrow(() -> new ProjectNotFoundException(id));

        this.neo4jRepository.deleteProject(id.id());

        this.jpaRepository.delete(entity);
    }

    @Override
    public List<Project.ProjectId> findIdsBySystemId(final System.SystemId systemId) {
        return this.jpaRepository.findIdsBySystemId(systemId.id())
            .stream()
            .map(Project.ProjectId::new)
            .toList();
    }

    @Override
    public int count(
        final System.SystemId systemId
    ) {
        return this.jpaRepository.countBySystemId(systemId.id());
    }

    @Override
    public UUID getOrCreateSnapshot(
        final Project.ProjectId projectId
    ) {
        final var snapshotId = UUID.randomUUID();

        final var project = this.jpaRepository.findById(projectId.id())
            .orElseThrow(() -> new GraphnousException("No project found"));

        final var path = Objects.requireNonNullElse(project.getPath(), "");

        return this.neo4jRepository.getOrCreateSnapshot(
            projectId.id(),
            project.getGitUrl(),
            path,
            snapshotId,
            Instant.now()
        );
    }
}
