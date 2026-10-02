package dev.graphnous.api.system.v1;

import dev.graphnous.application.context.RequestContextProvider;
import dev.graphnous.api.project.v1.ProjectMapper;
import dev.graphnous.api.v1.generated.project.CreateProjectRequest;
import dev.graphnous.api.v1.generated.project.Project;
import dev.graphnous.api.v1.generated.project.ProjectPage;
import dev.graphnous.api.v1.generated.system.CreateSystemRequest;
import dev.graphnous.api.v1.generated.system.System;
import dev.graphnous.api.v1.generated.system.SystemPage;
import dev.graphnous.api.v1.generated.system.UpdateSystemRequest;
import dev.graphnous.application.pagination.PageQuery;
import dev.graphnous.application.pagination.Sort;
import dev.graphnous.application.project.CreateProjectCommand;
import dev.graphnous.application.project.ProjectService;
import dev.graphnous.application.system.CreateSystemCommand;
import dev.graphnous.application.system.SystemService;
import dev.graphnous.application.system.UpdateSystemCommand;
import dev.graphnous.domain.system.System.SystemId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentContextPath;
import static org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromCurrentRequest;

@RestController
@RequestMapping("/api/v1/systems")
public class SystemController {

    private final SystemService systemService;
    private final ProjectService projectService;

    private final RequestContextProvider contextProvider;

    private final SystemMapper systemMapper;
    private final ProjectMapper projectMapper;

    public SystemController(
        final SystemService systemService,
        final ProjectService projectService,
        final RequestContextProvider contextProvider
    ) {
        this.systemService = systemService;
        this.projectService = projectService;

        this.contextProvider = contextProvider;

        this.systemMapper = new SystemMapper();
        this.projectMapper = new ProjectMapper();
    }

    @GetMapping
    public ResponseEntity<SystemPage> getSystems(
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|createdAt") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var response = this.systemService.getSystems(
            this.contextProvider.get(),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            )
        );

        return ResponseEntity.ok(
            this.systemMapper.toPage(response)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<System> getSystem(
        @PathVariable("id") final UUID id
    ) {
        final var system = this.systemService.getSystem(
            this.contextProvider.get(),
            new SystemId(id)
        );

        return ResponseEntity.ok(
            this.systemMapper.fromDomain(system)
        );
    }

    @PostMapping()
    public ResponseEntity<System> createSystem(
        @RequestBody @Valid final CreateSystemRequest systemRequest
    ) {
        final var system = this.systemService.create(
            contextProvider.get(),
            new CreateSystemCommand(systemRequest.getName(), systemRequest.getDescription())
        );

        final var location = fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(system.id().id())
            .toUri();

        return ResponseEntity
            .created(location)
            .body(this.systemMapper.fromDomain(system));
    }

    @PutMapping("/{id}")
    public ResponseEntity<System> updateSystem(
        @PathVariable("id") final UUID id,
        @RequestBody @Valid final UpdateSystemRequest systemRequest
    ) {

        final var system = this.systemService.update(
            contextProvider.get(),
            new UpdateSystemCommand(new SystemId(id), systemRequest.getName(), systemRequest.getDescription())
        );

        return ResponseEntity
            .ok()
            .body(this.systemMapper.fromDomain(system));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSystem(
        @PathVariable("id") final UUID id
    ) {
        this.systemService.delete(
            this.contextProvider.get(),
            new SystemId(id)
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/projects")
    public ResponseEntity<ProjectPage> getProjects(
        @PathVariable(name = "id") final UUID systemId,
        @RequestParam(name = "page", defaultValue = "0") @Min(0) final int pageNumber,
        @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) final int size,
        @RequestParam(name = "sort", defaultValue = "name") @Pattern(regexp = "name|createdAt") final String sort,
        @RequestParam(name = "direction", defaultValue = "asc") @Pattern(regexp = "asc|desc") final String direction
    ) {
        final var response = this.projectService.getProjects(
            this.contextProvider.get(),
            new PageQuery(
                pageNumber,
                size,
                new Sort(
                    sort,
                    Sort.Direction.fromValue(direction)
                )
            ),
            new SystemId(systemId)
        );

        return ResponseEntity.ok(
            this.projectMapper.toPage(response)
        );
    }

    @PostMapping("/{id}/projects")
    public ResponseEntity<Project> createProject(
        @PathVariable(name = "id") final UUID systemId,
        @RequestBody final CreateProjectRequest request
    ) {
        final var project = this.projectService.create(
            this.contextProvider.get(),
            new CreateProjectCommand(
                request.getName(),
                request.getDescription(),
                request.getGitUrl(),
                request.getPath(),
                new SystemId(systemId)
            )
        );

        // Projects are addressed on their own, not below their system
        final var location = fromCurrentContextPath()
            .path("/api/v1/projects/{id}")
            .buildAndExpand(project.id().id())
            .toUri();

        return ResponseEntity.created(location).body(
            this.projectMapper.fromDomain(project)
        );
    }

}
