package dev.graphnous.application.scan;

import dev.graphnous.domain.project.Project;

public record CreateScanCommand(Project.ProjectId projectId, String revision, String branch) {
}
