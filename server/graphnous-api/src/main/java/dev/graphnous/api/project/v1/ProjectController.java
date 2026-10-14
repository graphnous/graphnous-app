package dev.graphnous.api.project.v1;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.api.scan.v1.ScanMapper;
import dev.graphnous.api.v1.generated.project.Project;
import dev.graphnous.api.v1.generated.project.UpdateProjectRequest;
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
import dev.graphnous.application.scan.stats.ScanStatService;
import dev.graphnous.core.model.ScanResult;
import dev.graphnous.domain.project.Project.ProjectId;
import dev.graphnous.domain.system.System;
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
import java.util.Map;
import java.util.Set;
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

    private final ScanStatService scanStatService;

    public ProjectController(
        final ProjectService projectService,
        final RequestContextProvider contextProvider,
        final ScanService scanService,
        final ScanStatService scanStatService
    ) {
        this.projectService = projectService;

        this.contextProvider = contextProvider;

        this.scanService = scanService;
        this.scanStatService = scanStatService;

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

    /**
     * Not in the published OpenAPI spec yet, so its model is its own.
     *
     * @param scanId                 the scan the stats were counted from;
     *                               null when no scan of the project has
     *                               stats yet, and everything else is zero
     * @param numberOfModulesScanned the modules of every target
     * @param languages              the files by the language of their
     *                               target, such as JAVA; each a path from
     *                               the repository's root
     * @param numberOfClassesParsed  the classes, each counted once per module
     * @param numberOfMethodsParsed  the methods of those classes, and the
     *                               functions declared outside any class
     * @param numberOfScans          the project's scans, whatever their
     *                               status
     */
    public record ScanStatsResponse(
        UUID scanId,
        int numberOfModulesScanned,
        Map<String, List<String>> languages,
        int numberOfClassesParsed,
        int numberOfMethodsParsed,
        int numberOfScans
    ) {
    }

    /**
     * The stats of the project's latest scan that has any, with how many
     * scans the project has; all zero but that, as for a system, when none
     * has, such as before its first scan completed.
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ScanStatsResponse> getLatestScanStats(
        @PathVariable("id") final UUID projectId
    ) {
        final var context = this.contextProvider.get();
        final var stats = this.scanStatService.getLatestStats(context, new ProjectId(projectId));
        final var scans = this.scanStatService.countScans(context, new ProjectId(projectId));

        return ResponseEntity.ok(
            stats
                .map(found -> new ScanStatsResponse(
                    found.scanId().id(),
                    found.numberOfModulesScanned(),
                    found.languages(),
                    found.numberOfClassesParsed(),
                    found.numberOfMethodsParsed(),
                    scans
                ))
                .orElseGet(() -> new ScanStatsResponse(null, 0, Map.of(), 0, 0, scans))
        );
    }

    /**
     * The published request to create a scan, and which enhancers to run
     * on it, which is not in the published OpenAPI spec yet.
     *
     * @param enhancers the names of the enhancers to run on the scan's
     *                  results, as GET /enhancers lists them; all of them
     *                  when missing, none when empty
     * @param excludes  the patterns of the files to leave out of the scan,
     *                  such as *.spec.ts, written as in a .gitignore; the
     *                  project's own when missing, none when empty
     * @param languages the languages to scan, as GET /scanners lists them,
     *                  such as JAVA; the project's own when missing, every
     *                  language the scan finds when empty
     */
    public record StartScanRequest(
        String branch,
        String revision,
        List<String> enhancers,
        List<String> excludes,
        List<String> languages
    ) {
    }

    /**
     * Not in the published OpenAPI spec yet, so its model is its own.
     *
     * @param excludes  the patterns of the files the project leaves out of
     *                  its scans, such as *.spec.ts, written as in a
     *                  .gitignore; a scan can be created with other ones
     * @param languages the languages the project scans, as GET /scanners
     *                  lists them, such as JAVA; every language its scans
     *                  find when empty, and a scan can be created with
     *                  other ones
     */
    public record ScanSettings(
        List<String> excludes,
        List<String> languages
    ) {
    }

    @GetMapping("/{id}/scan-settings")
    public ResponseEntity<ScanSettings> getScanSettings(
        @PathVariable("id") final UUID id
    ) {
        final var context = this.contextProvider.get();
        final var projectId = new ProjectId(id);

        return ResponseEntity.ok(new ScanSettings(
            this.projectService.getScanExcludes(context, projectId),
            sorted(this.projectService.getScanLanguages(context, projectId))
        ));
    }

    /**
     * Sets what the project's scans leave out and which languages they
     * scan from now on; a setting that is missing stays as it is, and the
     * scans it has stay as they are. 400 for a pattern that is not well
     * formed, or a language there is no scanner for.
     */
    @PutMapping("/{id}/scan-settings")
    public ResponseEntity<ScanSettings> updateScanSettings(
        @PathVariable("id") final UUID id,
        @RequestBody final ScanSettings settings
    ) {
        final var context = this.contextProvider.get();
        final var projectId = new ProjectId(id);

        final var excludes = settings.excludes() == null
            ? this.projectService.getScanExcludes(context, projectId)
            : this.projectService.updateScanExcludes(context, projectId, settings.excludes());
        final var languages = settings.languages() == null
            ? this.projectService.getScanLanguages(context, projectId)
            : this.projectService.updateScanLanguages(context, projectId, settings.languages());

        return ResponseEntity.ok(new ScanSettings(excludes, sorted(languages)));
    }

    private static List<String> sorted(final Set<String> languages) {
        return languages.stream().sorted().toList();
    }

    /**
     * 400 when an enhancer it names is not installed, a pattern of files
     * to leave out is not well formed, or there is no scanner for a
     * language it names.
     */
    @PostMapping("/{id}/scans")
    public ResponseEntity<dev.graphnous.api.v1.generated.scan.Scan> createScan(
        @PathVariable("id") final UUID projectId,
        @RequestBody final StartScanRequest createScanRequest
    ) {
        final var scan = this.scanService.create(
            this.contextProvider.get(),
            new CreateScanCommand(
                new ProjectId(projectId),
                createScanRequest.revision(),
                createScanRequest.branch(),
                createScanRequest.enhancers() == null ? null : Set.copyOf(createScanRequest.enhancers()),
                createScanRequest.excludes(),
                createScanRequest.languages() == null ? null : Set.copyOf(createScanRequest.languages())
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
        final var results = new ArrayList<ScanResult>();

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

    private static ScanResult read(final MultipartFile file) {
        try (final var input = file.getInputStream()) {
            return SCAN_RESULTS.readValue(input, ScanResult.class);
        } catch (final JsonProcessingException e) {
            throw new ValidationException(
                "File " + file.getOriginalFilename() + " is not a valid scan result: " + e.getOriginalMessage()
            );
        } catch (final IOException e) {
            throw new UncheckedIOException("Reading " + file.getOriginalFilename() + " failed", e);
        }
    }
}
