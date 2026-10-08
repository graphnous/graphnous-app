package dev.graphnous.api.scan.v1;

import dev.graphnous.api.v1.generated.project.Project;
import dev.graphnous.api.v1.generated.project.ProjectPage;
import dev.graphnous.api.v1.generated.scan.Scan;
import dev.graphnous.api.v1.generated.scan.ScanExecution;
import dev.graphnous.api.v1.generated.scan.ScanPage;
import dev.graphnous.api.v1.generated.scan.ScanStep;
import dev.graphnous.application.pagination.Page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public class ScanMapper {

    public Scan fromDomain(
        final dev.graphnous.domain.scan.Scan domain
    ) {
        final var scan = new Scan();

        scan.setId(domain.id().id());
        scan.setProjectId(domain.projectId().id());

        scan.setStatus(
            Scan.StatusEnum.fromValue(domain.status().name())
        );

        scan.setBranch(
            domain.revision().branch()
        );

        scan.setRevision(
            domain.revision().revision()
        );

        scan.setRequestedRevision(
            domain.revision().requestedRevision()
        );

        scan.setCreatedAt(domain.createdAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        scan.setUpdatedAt(domain.updatedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());

        if (domain.startedAt() != null) {
            scan.setStartedAt(domain.startedAt().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        }

        // A finished scan never changes again, so its last update is when it finished
        if (domain.status().isEndState()) {
            scan.setCompletedAt(scan.getUpdatedAt());
        }

        return scan;
    }

    public ScanPage toPage(final Page<dev.graphnous.domain.scan.Scan> domain) {
        final var page = new ScanPage();

        page.setPage(domain.page());
        page.setContent(domain.content().stream().map(this::fromDomain).toList());
        page.setSize(domain.size());
        page.setTotalPages(domain.totalPages());
        page.setTotalElements(domain.totalElements());

        return page;
    }


    public ScanExecution toExecution(
        final UUID scanId,
        final List<dev.graphnous.domain.scan.ScanStep> steps
    ) {
        final var execution = new ScanExecution();

        execution.setScanId(scanId);
        execution.setSteps(steps.stream().map(ScanMapper::fromDomain).toList());

        return execution;
    }

    private static ScanStep fromDomain(final dev.graphnous.domain.scan.ScanStep domain) {
        final var step = new ScanStep();

        step.setType(ScanStep.TypeEnum.fromValue(domain.type().name()));
        step.setStatus(ScanStep.StatusEnum.fromValue(domain.status().name()));
        step.setStartedAt(offset(domain.startedAt()));
        step.setFinishedAt(offset(domain.finishedAt()));
        step.setError(domain.error());

        return step;
    }

    private static OffsetDateTime offset(final Instant instant) {
        return instant == null ? null : instant.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
