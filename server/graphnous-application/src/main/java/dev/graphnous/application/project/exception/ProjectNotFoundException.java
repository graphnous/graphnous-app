package dev.graphnous.application.project.exception;

import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.domain.project.Project;

public class ProjectNotFoundException extends NotFoundException {

    public ProjectNotFoundException(Project.ProjectId id) {
        super("Project with id %s not found".formatted(id.id()));
    }

}
