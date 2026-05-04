package com.msaas.project;

import com.msaas.instance.InstanceController.InstanceView;
import com.msaas.instance.InstanceService;
import com.msaas.security.AuthenticatedUser;
import com.msaas.spec.SpecController.SpecVersionView;
import com.msaas.spec.SpecService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final SpecService specService;
    private final InstanceService instanceService;

    public ProjectController(ProjectService projectService, SpecService specService, InstanceService instanceService) {
        this.projectService = projectService;
        this.specService = specService;
        this.instanceService = instanceService;
    }

    @PostMapping
    public ProjectView create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateProjectRequest request) {
        return ProjectView.from(projectService.create(user.id(), request.name(), request.description()));
    }

    @GetMapping
    public List<ProjectView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return projectService.listForOwner(user.id()).stream().map(ProjectView::from).toList();
    }

    @GetMapping("/{projectId}")
    public ProjectView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        return ProjectView.from(projectService.requireOwnedProject(projectId, user.id()));
    }

    @DeleteMapping("/{projectId}")
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String projectId,
            @Valid @RequestBody DeleteProjectRequest request
    ) {
        projectService.deleteProject(user.id(), projectId, request.confirmName());
    }

    @GetMapping("/{projectId}/spec-versions")
    public List<SpecVersionView> specVersions(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        projectService.requireOwnedProject(projectId, user.id());
        return specService.list(projectId).stream().map(SpecVersionView::from).toList();
    }

    @GetMapping("/{projectId}/instances")
    public List<InstanceView> instances(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        projectService.requireOwnedProject(projectId, user.id());
        return instanceService.listForProject(projectId).stream().map(InstanceView::from).toList();
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description
    ) {
    }

    public record DeleteProjectRequest(@NotBlank String confirmName) {
    }

    public record ProjectView(
            String id,
            String ownerId,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ProjectView from(Project project) {
            return new ProjectView(
                    project.getId(),
                    project.getOwnerId(),
                    project.getName(),
                    project.getDescription(),
                    project.getCreatedAt(),
                    project.getUpdatedAt()
            );
        }
    }
}
