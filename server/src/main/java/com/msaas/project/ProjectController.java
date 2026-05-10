package com.msaas.project;

import com.msaas.audit.AuditEvent;
import com.msaas.audit.AuditService;
import com.msaas.instance.InstanceController.InstanceView;
import com.msaas.instance.InstanceService;
import com.msaas.security.AuthenticatedUser;
import com.msaas.spec.SpecController.SpecVersionView;
import com.msaas.spec.SpecService;
import com.msaas.user.AppUser;
import com.msaas.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;
    private final SpecService specService;
    private final InstanceService instanceService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public ProjectController(ProjectService projectService, SpecService specService, InstanceService instanceService, AuditService auditService, UserRepository userRepository) {
        this.projectService = projectService;
        this.specService = specService;
        this.instanceService = instanceService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ProjectView create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody CreateProjectRequest request) {
        return ProjectView.from(projectService.create(user.id(), request.name(), request.description()), ProjectRole.OWNER);
    }

    @GetMapping
    public List<ProjectView> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return projectService.listForUser(user.id()).stream()
                .map(project -> ProjectView.from(project, projectService.roleForRequiredProject(project, user.id())))
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        Project project = projectService.requireProjectAccess(projectId, user.id());
        return ProjectView.from(project, projectService.roleForRequiredProject(project, user.id()));
    }

    @DeleteMapping("/{projectId}")
    public void delete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String projectId,
            @Valid @RequestBody DeleteProjectRequest request
    ) {
        projectService.deleteProject(user.id(), projectId, request.confirmName());
    }

    @GetMapping("/{projectId}/members")
    public List<ProjectMemberView> members(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        Project project = projectService.requireProjectAccess(projectId, user.id());
        List<ProjectMemberView> members = projectService.members(projectId, user.id())
                .stream()
                .map(ProjectMemberView::from)
                .toList();
        return appendOwner(project, members);
    }

    @PostMapping("/{projectId}/members")
    public List<ProjectMemberView> addMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String projectId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        Project project = projectService.addMember(projectId, user.id(), request.resolvedIdentifier(), request.role());
        return appendOwner(project, project.getMembers().stream().map(ProjectMemberView::from).toList());
    }

    @DeleteMapping("/{projectId}/members/{memberUserId}")
    public List<ProjectMemberView> removeMember(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String projectId,
            @PathVariable String memberUserId
    ) {
        Project project = projectService.removeMember(projectId, user.id(), memberUserId);
        return appendOwner(project, project.getMembers().stream().map(ProjectMemberView::from).toList());
    }

    @GetMapping("/{projectId}/audit")
    public List<AuditEventView> audit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String projectId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        projectService.requireProjectAccess(projectId, user.id());
        return auditService.list(projectId, limit).stream().map(AuditEventView::from).toList();
    }

    @GetMapping("/{projectId}/spec-versions")
    public List<SpecVersionView> specVersions(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        projectService.requireProjectAccess(projectId, user.id());
        return specService.list(projectId).stream().map(SpecVersionView::from).toList();
    }

    @GetMapping("/{projectId}/instances")
    public List<InstanceView> instances(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String projectId) {
        projectService.requireProjectAccess(projectId, user.id());
        return instanceService.listForProject(projectId).stream().map(InstanceView::from).toList();
    }

    private List<ProjectMemberView> appendOwner(Project project, List<ProjectMemberView> members) {
        List<ProjectMemberView> all = new java.util.ArrayList<>();
        AppUser owner = userRepository.findById(project.getOwnerId()).orElse(null);
        all.add(new ProjectMemberView(
                project.getOwnerId(),
                owner == null ? "" : owner.getEmail(),
                owner == null ? "owner" : owner.getUsername(),
                ProjectRole.OWNER,
                project.getCreatedAt()
        ));
        all.addAll(members);
        return all;
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description
    ) {
    }

    public record DeleteProjectRequest(@NotBlank String confirmName) {
    }

    public record AddMemberRequest(
            String identifier,
            String email,
            ProjectRole role
    ) {
        public String resolvedIdentifier() {
            return identifier == null || identifier.isBlank() ? email : identifier;
        }
    }

    public record ProjectView(
            String id,
            String ownerId,
            String name,
            String description,
            ProjectRole role,
            int memberCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ProjectView from(Project project, ProjectRole role) {
            return new ProjectView(
                    project.getId(),
                    project.getOwnerId(),
                    project.getName(),
                    project.getDescription(),
                    role,
                    project.getMembers().size() + 1,
                    project.getCreatedAt(),
                    project.getUpdatedAt()
            );
        }
    }

    public record ProjectMemberView(
            String userId,
            String email,
            String username,
            ProjectRole role,
            Instant addedAt
    ) {
        public static ProjectMemberView from(ProjectMember member) {
            return new ProjectMemberView(member.getUserId(), member.getEmail(), member.getUsername(), member.getRole(), member.getAddedAt());
        }
    }

    public record AuditEventView(
            String id,
            String actorId,
            String action,
            String targetType,
            String targetId,
            String message,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
        public static AuditEventView from(AuditEvent event) {
            return new AuditEventView(
                    event.getId(),
                    event.getActorId(),
                    event.getAction(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getMessage(),
                    event.getMetadata(),
                    event.getCreatedAt()
            );
        }
    }
}
