package dev.graphnous.application.project;

import dev.graphnous.domain.project.Project;
import dev.graphnous.domain.system.System;

public record UpdateProjectCommand(Project.ProjectId id, String name, String description, String path, System.SystemId systemId)
{ }
