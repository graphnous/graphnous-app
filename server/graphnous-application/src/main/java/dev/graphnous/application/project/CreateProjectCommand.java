package dev.graphnous.application.project;

import dev.graphnous.domain.system.System;

public record CreateProjectCommand(String name, String description, String gitUrl, String path, System.SystemId systemId)
{ }