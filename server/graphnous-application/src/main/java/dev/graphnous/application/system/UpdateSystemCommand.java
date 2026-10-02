package dev.graphnous.application.system;

import dev.graphnous.domain.system.System;

public record UpdateSystemCommand(System.SystemId id, String name, String description) {
}
