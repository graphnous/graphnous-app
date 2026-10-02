package dev.graphnous.persistence.system;

import dev.graphnous.domain.system.System;

class SystemMapper {

    public SystemEntity fromDomain(final System domain) {
        final var entity = new SystemEntity();

        entity.setId(domain.id().id());

        entity.setName(domain.name());
        entity.setDescription(domain.description());

        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());

        return entity;
    }

    public System toDomain(final SystemEntity entity) {
        return new System(new System.SystemId(entity.getId()), entity.getName(), entity.getDescription(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
