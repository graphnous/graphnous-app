package dev.graphnous.domain.project;

import dev.graphnous.domain.system.System;

import java.time.Instant;
import java.util.UUID;

public record Project(ProjectId id, String name, String description, String gitUrl, String path, System.SystemId systemId, Instant createdAt, Instant updatedAt) {

    public record ProjectId(UUID id) {

        public static Project.ProjectId generate() {
            return new Project.ProjectId(UUID.randomUUID());
        }
    }
}
