package dev.graphnous.api.project.v1;

import dev.graphnous.api.v1.generated.project.Project;
import dev.graphnous.api.v1.generated.project.ProjectPage;
import dev.graphnous.application.pagination.Page;

import java.time.ZoneId;

public class ProjectMapper {

    public Project fromDomain(
        final dev.graphnous.domain.project.Project domain
    ) {
        final var project = new Project();

        project.setId(domain.id().id());

        project.setName(domain.name());
        project.setDescription(domain.description());

        project.setGitUrl(domain.gitUrl());
        project.setPath(domain.path());

        project.setSystemId(domain.systemId().id());

        project.setCreatedAt(domain.createdAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        project.setUpdatedAt(domain.updatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());

        return project;
    }

    public ProjectPage toPage(final Page<dev.graphnous.domain.project.Project> domain) {
        final var page = new ProjectPage();

        page.setPage(domain.page());
        page.setContent(domain.content().stream().map(this::fromDomain).toList());
        page.setSize(domain.size());
        page.setTotalPages(domain.totalPages());
        page.setTotalElements(domain.totalElements());

        return page;
    }

}


