package dev.graphnous.api.scan.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.api.v1.generated.scan.ScanExecution;
import dev.graphnous.api.v1.generated.scanlog.ScanLogPage;
import dev.graphnous.application.project.scanner.logs.ScanLogListener;
import dev.graphnous.application.project.scanner.logs.ScanLogSubscriber;
import dev.graphnous.application.scan.DeleteScanCommand;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.log.ScanLogService;
import dev.graphnous.domain.scan.Scan;
import dev.graphnous.domain.scan.log.ScanLog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scans")
public class ScanController {

    private final RequestContextProvider contextProvider;

    private final ScanService scanService;
    private final ScanMapper scanMapper = new ScanMapper();
    private final ScanLogSubscriber scanLogSubscriber;
    private final ScanLogService scanLogService;

    public ScanController(
        final RequestContextProvider contextProvider,
        final ScanService scanService,
        final ScanLogSubscriber scanLogSubscriber,
        final ScanLogService scanLogService
    ) {
        this.contextProvider = contextProvider;

        this.scanService = scanService;
        this.scanLogSubscriber = scanLogSubscriber;

        this.scanLogService = scanLogService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScan(
        @PathVariable("id") final UUID id
    ) {
        this.scanService.delete(
            this.contextProvider.get(),
            new DeleteScanCommand(
                new Scan.ScanId(id)
            )
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/steps")
    public ResponseEntity<ScanExecution> getScanExecution(
        @PathVariable("id") final UUID scanId
    ) {
        final var steps = this.scanService.getSteps(
            this.contextProvider.get(),
            new Scan.ScanId(scanId)
        );

        return ResponseEntity.ok(this.scanMapper.toExecution(scanId, steps));
    }

    @GetMapping(
        value = "/{id}/logs"
    )
    public ResponseEntity<ScanLogPage> getScanLogs(
        @PathVariable("id") final UUID scanId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "100") @Min(1) @Max(1000) final int size
    ) {
        final var logs = this.scanLogService.getLogs(
            this.contextProvider.get(),
            new Scan.ScanId(scanId),
            pageNumber,
            size
        );

        final var page = new ScanLogPage();
        page.setPage(logs.page());
        page.setSize(logs.size());
        page.setTotalElements(logs.totalElements());
        page.setTotalPages(logs.totalPages());
        page.setContent(
            logs.content().stream().map((l) -> {
                final var log = new dev.graphnous.api.v1.generated.scanlog.ScanLog();
                log.setId(l.id().id());
                log.setScanId(l.scanId().id());
                log.setLevel(dev.graphnous.api.v1.generated.scanlog.ScanLog.LevelEnum.valueOf(l.level().name()));
                log.setMessage(l.message());
                log.setSequence(l.sequence());
                log.setTimestamp(l.timestamp().atZone(ZoneId.systemDefault()).toOffsetDateTime());
                return log;
            }).toList()
        );
        return ResponseEntity.ok(page);
    }

    @GetMapping(
        value = "/{id}/logs/stream",
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamLogs(
        @PathVariable("id") final UUID id
    ) {
        // Checks the caller may read the scan, and that it exists
        this.scanService.getScan(
            this.contextProvider.get(),
            new Scan.ScanId(id)
        );

        final var emitter = new SseEmitter(0L);

        final var listener = new ScanLogListener() {
            @Override
            public void onLog(final ScanLog log) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("scanLog")
                            .data(log)
                    );
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }
        };

        scanLogSubscriber.subscribe(new Scan.ScanId(id), listener);

        emitter.onCompletion(
            () -> scanLogSubscriber.unsubscribe(new Scan.ScanId(id), listener)
        );

        emitter.onTimeout(
            () -> scanLogSubscriber.unsubscribe(new Scan.ScanId(id), listener)
        );

        emitter.onError(
            error -> scanLogSubscriber.unsubscribe(new Scan.ScanId(id), listener)
        );

        return emitter;
    }
}
