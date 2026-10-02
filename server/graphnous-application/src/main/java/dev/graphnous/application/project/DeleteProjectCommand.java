package dev.graphnous.application.project;

import dev.graphnous.domain.project.Project;

public record DeleteProjectCommand(Project.ProjectId projectId)
{ }
