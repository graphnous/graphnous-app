package dev.graphnous.application.project;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository {

    Project save(
        final Project project
    );

    Page<Project> findAll(
        final System.SystemId systemId,
        final PageQuery pageQuery
    );

    Project findById(
        final Project.ProjectId id
    );

    void delete(
        final System.SystemId systemId,
        final Project.ProjectId id
    );

    int count(
        final System.SystemId systemId
    );

    List<Project.ProjectId> findIdsBySystemId(
        final System.SystemId systemId
    );

    UUID getOrCreateSnapshot(
        final Project.ProjectId id
    );

}
