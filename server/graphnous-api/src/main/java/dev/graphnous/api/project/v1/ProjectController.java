package dev.graphnous.api.project.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.api.scan.v1.ScanMapper;
import dev.graphnous.api.v1.generated.project.Project;
import dev.graphnous.api.v1.generated.project.UpdateProjectRequest;
import dev.graphnous.api.v1.generated.scan.CreateScanRequest;
import dev.graphnous.api.v1.generated.scan.ScanPage;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.DeleteProjectCommand;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.project.UpdateProjectCommand;
import dev.graphnous.application.exception.ValidationException;
import dev.graphnous.application.scan.CreateScanCommand;
import dev.graphnous.application.scan.ScanService;
import dev.graphnous.application.scan.UploadScanCommand;
import dev.graphnous.domain.project.Project.ProjectId;
import dev.graphnous.domain.system.System;
import dev.graphnous.scanner.model.ScanResultSchema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    /**
     * Jackson 2, as the scan result model uses.
     */
    private static final ObjectMapper SCAN_RESULTS = new ObjectMapper();

    private final ProjectService projectService;
    private final RequestContextProvider contextProvider;

    private final ProjectMapper projectMapper;

    private final ScanService scanService;

    private final ScanMapper scanMapper;

    public ProjectController(
        final ProjectService projectService,
        final RequestContextProvider contextProvider,
        final ScanService scanService
    ) {
        this.projectService = projectService;

        this.contextProvider = contextProvider;

        this.scanService = scanService;

        this.projectMapper = new ProjectMapper();
        this.scanMapper = new ScanMapper();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(
        @PathVariable("id") final UUID id
    ) {
        final var project = this.projectService.getProject(
            this.contextProvider.get(),
            new ProjectId(id)
        );

        return ResponseEntity.ok(
            this.projectMapper.fromDomain(project)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<Project> updateProject(
        @PathVariable("id") final UUID id,
        @RequestBody @Valid final UpdateProjectRequest projectRequest
    ) {

        final var system = this.projectService.update(
            contextProvider.get(),
            new UpdateProjectCommand(
                new ProjectId(id),
                projectRequest.getName(),
                projectRequest.getDescription(),
                projectRequest.getPath(),
                new System.SystemId(projectRequest.getSystemId())
            )
        );


        return ResponseEntity
            .ok()
            .body(this.projectMapper.fromDomain(system));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
        @PathVariable("id") final UUID id
    ) {
        this.projectService.delete(
            this.contextProvider.get(),
            new DeleteProjectCommand(
                new ProjectId(id)
            )
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/scans")
    public ResponseEntity<ScanPage> getScans(
        @PathVariable("id") final UUID projectId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "createdAt") @Pattern(regexp = "createdAt|updatedAt") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var response = this.scanService.getScans(
            this.contextProvider.get(),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            ),
            new ProjectId(projectId)
        );

        return ResponseEntity.ok(
            this.scanMapper.toPage(response)
        );
    }

    @PostMapping("/{id}/scans")
    public ResponseEntity<dev.graphnous.api.v1.generated.scan.Scan> createScan(
        @PathVariable("id") final UUID projectId,
        @RequestBody final CreateScanRequest createScanRequest
    ) {
        final var scan = this.scanService.create(
            this.contextProvider.get(),
            new CreateScanCommand(
                new ProjectId(projectId),
                createScanRequest.getRevision(),
                createScanRequest.getBranch()
            )
        );
        // Scans run in the background; their steps show how they go
        final var location = fromCurrentContextPath()
            .path("/api/v1/scans/{id}/steps")
            .buildAndExpand(scan.id().id())
            .toUri();

        return ResponseEntity.created(location).body(
            this.scanMapper.fromDomain(scan)
        );
    }


    /**
     * Creates a scan from result files scanned elsewhere, such as by a CI
     * job or a build plugin: one file per scanned target. Storing and
     * enhancing them continue in the background; the location is where to
     * follow the scan's steps.
     */
    @PostMapping(value = "/{id}/scan-results", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<dev.graphnous.api.v1.generated.scan.Scan> uploadScanResults(
        @PathVariable("id") final UUID projectId,
        @RequestParam("branch") @NotBlank final String branch,
        @RequestParam("revision") @NotBlank final String revision,
        @RequestPart("files") final List<MultipartFile> files
    ) {
        final var results = new ArrayList<ScanResultSchema>();

        for (final var file : files) {
            results.add(read(file));
        }

        final var scan = this.scanService.upload(
            this.contextProvider.get(),
            new UploadScanCommand(new ProjectId(projectId), revision, branch, results)
        );

        final var location = fromCurrentContextPath()
            .path("/api/v1/scans/{id}/steps")
            .buildAndExpand(scan.id().id())
            .toUri();

        return ResponseEntity.created(location).body(
            this.scanMapper.fromDomain(scan)
        );
    }

    private static ScanResultSchema read(final MultipartFile file) {
        try (final var input = file.getInputStream()) {
            return SCAN_RESULTS.readValue(input, ScanResultSchema.class);
        } catch (final JsonProcessingException e) {
            throw new ValidationException(
                "File " + file.getOriginalFilename() + " is not a valid scan result: " + e.getOriginalMessage()
            );
        } catch (final IOException e) {
            throw new UncheckedIOException("Reading " + file.getOriginalFilename() + " failed", e);
        }
    }
}
