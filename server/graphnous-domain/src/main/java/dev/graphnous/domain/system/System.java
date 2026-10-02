package dev.graphnous.domain.system;

import java.time.Instant;
import java.util.UUID;

public record System(SystemId id, String name, String description, Instant createdAt, Instant updatedAt) {

    public record SystemId(UUID id) {

        public static SystemId generate() {
            return new SystemId(UUID.randomUUID());
        }
    }
}
