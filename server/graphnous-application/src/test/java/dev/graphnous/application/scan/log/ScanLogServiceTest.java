package dev.graphnous.application.scan.log;

import dev.graphnous.application.context.OrganizationContext;
import dev.graphnous.application.context.RequestContext;
import dev.graphnous.application.context.UserContext;
import dev.graphnous.application.exception.AuthorizationException;
import dev.graphnous.application.exception.NotFoundException;
import dev.graphnous.application.organization.OrganizationId;
import dev.graphnous.application.project.scanner.logs.ScanLogPublisher;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanLogServiceTest {

    @Mock
    private ScanLogRepository repository;

    @Mock
    private ScanLogPublisher publisher;

    @Mock
    private ScanService scanService;

    private final RequestContext context = new RequestContext(
        new UserContext(UUID.randomUUID()),
        new OrganizationContext(new OrganizationId(UUID.randomUUID()))
    );

    private final Scan.ScanId scanId = Scan.ScanId.generate();

    @Test
    void readsTheLogsOfAScanTheCallerMayRead() {
        final var log = log(1);

        when(repository.findByScanId(scanId)).thenReturn(List.of(log));

        final var page = service().getLogs(context, scanId, 0, 100);

        assertThat(page.content()).containsExactly(log);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        verify(scanService).getScan(context, scanId);
    }

    @Test
    void pagesTheLogs() {
        final var logs = IntStream.rangeClosed(1, 5)
            .mapToObj(sequence -> log(sequence))
            .toList();

        when(repository.findByScanId(scanId)).thenReturn(logs);

        final var second = service().getLogs(context, scanId, 1, 2);

        assertThat(second.content()).extracting(ScanLog::sequence).containsExactly(3L, 4L);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.size()).isEqualTo(2);
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(second.totalPages()).isEqualTo(3);

        assertThat(service().getLogs(context, scanId, 2, 2).content())
            .extracting(ScanLog::sequence).containsExactly(5L);
        assertThat(service().getLogs(context, scanId, 3, 2).content()).isEmpty();
    }

    @Test
    void refusesAnInvalidPage() {
        assertThrows(IllegalArgumentException.class, () -> service().getLogs(context, scanId, -1, 10));
        assertThrows(IllegalArgumentException.class, () -> service().getLogs(context, scanId, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service().getLogs(context, scanId, 0, 1001));
    }

    @Test
    void doesNotReadTheLogsOfAScanTheCallerMayNotRead() {
        when(scanService.getScan(context, scanId)).thenThrow(new AuthorizationException("Not allowed"));

        assertThrows(AuthorizationException.class, () -> service().getLogs(context, scanId, 0, 100));

        verify(repository, never()).findByScanId(any());
    }

    @Test
    void doesNotReadTheLogsOfAnUnknownScan() {
        when(scanService.getScan(context, scanId)).thenThrow(new NotFoundException("Scan not found"));

        assertThrows(NotFoundException.class, () -> service().getLogsAfter(context, scanId, 10));

        verify(repository, never()).findByScanIdAfter(any(), anyLong());
    }

    private ScanLog log(final long sequence) {
        return new ScanLog(
            ScanLog.ScanLogId.generate(),
            scanId,
            sequence,
            Instant.now(),
            ScanLog.ScanLogLevel.INFO,
            "Log " + sequence
        );
    }

    private ScanLogService service() {
        return new ScanLogService(repository, publisher, scanService);
    }
}
