package dev.graphnous.application.project;

import dev.graphnous.application.pagination.Page;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;

import java.util.List;
import java.util.Set;
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

    /**
     * The patterns of the files the project leaves out of its scans, in
     * their order; none when it keeps every file.
     */
    List<String> findScanExcludes(
        final Project.ProjectId id
    );

    void saveScanExcludes(
        final Project.ProjectId id,
        final List<String> excludes
    );

    /**
     * The languages the project scans, such as {@code JAVA}; none when it
     * scans every language its scans find.
     */
    Set<String> findScanLanguages(
        final Project.ProjectId id
    );

    void saveScanLanguages(
        final Project.ProjectId id,
        final Set<String> languages
    );

    UUID getOrCreateSnapshot(
        final Project.ProjectId id
    );

}
