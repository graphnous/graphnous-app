package dev.graphnous.api.system.v1;

import dev.graphnous.api.v1.generated.system.SystemPage;
import dev.graphnous.application.pagination.Page;

import java.time.ZoneId;

class SystemMapper {

    public dev.graphnous.api.v1.generated.system.System fromDomain(
        final dev.graphnous.domain.system.System domain
    ) {
        final var system = new dev.graphnous.api.v1.generated.system.System();

        system.setId(domain.id().id());

        system.setName(domain.name());
        system.setDescription(domain.description());

        system.setCreatedAt(domain.createdAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        system.setUpdatedAt(domain.updatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());

        return system;
    }

    public SystemPage toPage(final Page<dev.graphnous.domain.system.System> domain) {
        final var page = new SystemPage();

        page.setPage(domain.page());
        page.setContent(domain.content().stream().map(this::fromDomain).toList());
        page.setSize(domain.size());
        page.setTotalPages(domain.totalPages());
        page.setTotalElements(domain.totalElements());

        return page;
    }
}


