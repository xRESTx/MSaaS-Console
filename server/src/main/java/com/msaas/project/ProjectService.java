package com.msaas.project;

import com.msaas.audit.AuditService;
import com.msaas.common.ApiException;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.log.RequestLogRepository;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.spec.SpecVersionRepository;
import com.msaas.user.AppUser;
import com.msaas.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final SpecVersionRepository specVersionRepository;
    private final MockInstanceRepository instanceRepository;
    private final RequestLogRepository requestLogRepository;
    private final MockRuntimeRegistry runtimeRegistry;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public ProjectService(
            ProjectRepository projectRepository,
            SpecVersionRepository specVersionRepository,
            MockInstanceRepository instanceRepository,
            RequestLogRepository requestLogRepository,
            MockRuntimeRegistry runtimeRegistry,
            UserRepository userRepository,
            AuditService auditService
    ) {
        this.projectRepository = projectRepository;
        this.specVersionRepository = specVersionRepository;
        this.instanceRepository = instanceRepository;
        this.requestLogRepository = requestLogRepository;
        this.runtimeRegistry = runtimeRegistry;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public Project create(String ownerId, String name, String description) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isBlank()) {
            throw ApiException.badRequest("Project name is required");
        }
        if (hasAccessibleProjectNamed(ownerId, normalizedName, null)) {
            throw ApiException.conflict("Project name already exists for this user");
        }
        Project project = projectRepository.save(new Project(ownerId, normalizedName, description == null ? "" : description.trim()));
        auditService.record(project.getId(), ownerId, "PROJECT_CREATED", "project", project.getId(), "Project created");
        return project;
    }

    public List<Project> listForUser(String userId) {
        return projectRepository.findAccessibleByUserId(userId, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<Project> listForOwner(String ownerId) {
        return listForUser(ownerId);
    }

    public Project requireProjectAccess(String projectId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (roleFor(project, userId).isEmpty()) {
            throw ApiException.notFound("Project not found");
        }
        return project;
    }

    public Project requireProjectWriteAccess(String projectId, String userId) {
        Project project = requireProjectAccess(projectId, userId);
        ProjectRole role = roleFor(project, userId).orElseThrow();
        if (!role.atLeast(ProjectRole.MEMBER)) {
            throw ApiException.forbidden("Project write access is required");
        }
        return project;
    }

    public Project requireOwnedProject(String projectId, String ownerId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (!project.getOwnerId().equals(ownerId)) {
            throw ApiException.notFound("Project not found");
        }
        return project;
    }

    public ProjectRole roleForRequiredProject(Project project, String userId) {
        return roleFor(project, userId).orElseThrow(() -> ApiException.notFound("Project not found"));
    }

    public List<ProjectMember> members(String projectId, String actorId) {
        Project project = requireProjectAccess(projectId, actorId);
        return project.getMembers()
                .stream()
                .sorted(Comparator.comparing(ProjectMember::getAddedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public Project addMember(String projectId, String ownerId, String identifier, ProjectRole role) {
        Project project = requireOwnedProject(projectId, ownerId);
        AppUser user = findUserByIdentifier(identifier)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getId().equals(project.getOwnerId())) {
            throw ApiException.badRequest("Project owner is already the owner");
        }
        if (hasAccessibleProjectNamed(user.getId(), project.getName(), project.getId())) {
            throw ApiException.conflict("User already has access to a project with this name");
        }
        ProjectRole safeRole = role == null || role == ProjectRole.OWNER ? ProjectRole.VIEWER : role;
        ProjectMember member = project.getMembers()
                .stream()
                .filter(item -> item.getUserId().equals(user.getId()))
                .findFirst()
                .orElse(null);
        if (member == null) {
            project.getMembers().add(new ProjectMember(user.getId(), user.getEmail(), user.getUsername(), safeRole));
        } else {
            member.setEmail(user.getEmail());
            member.setUsername(user.getUsername());
            member.setRole(safeRole);
            member.setAddedAt(member.getAddedAt() == null ? Instant.now() : member.getAddedAt());
        }
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        auditService.record(projectId, ownerId, "MEMBER_UPSERTED", "user", user.getId(), "Project member upserted", Map.of("role", safeRole.name()));
        return saved;
    }

    public Project removeMember(String projectId, String ownerId, String memberUserId) {
        Project project = requireOwnedProject(projectId, ownerId);
        boolean removed = project.getMembers().removeIf(member -> member.getUserId().equals(memberUserId));
        if (!removed) {
            throw ApiException.notFound("Project member not found");
        }
        project.setUpdatedAt(Instant.now());
        Project saved = projectRepository.save(project);
        auditService.record(projectId, ownerId, "MEMBER_REMOVED", "user", memberUserId, "Project member removed");
        return saved;
    }

    public void deleteProject(String ownerId, String projectId, String confirmName) {
        Project project = requireOwnedProject(projectId, ownerId);
        deleteProjectData(project, confirmName);
    }

    public Project deleteProjectAsAdmin(String actorId, String projectId, String confirmName) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        deleteProjectData(project, confirmName);
        auditService.record(null, actorId, "ADMIN_PROJECT_DELETED", "project", project.getId(), "Project deleted by admin", Map.of(
                "name", project.getName(),
                "ownerId", project.getOwnerId()
        ));
        return project;
    }

    public void removeUserFromMemberships(String userId) {
        List<Project> projects = projectRepository.findByMembersUserId(userId);
        for (Project project : projects) {
            boolean changed = project.getMembers().removeIf(member -> member.getUserId().equals(userId));
            if (changed) {
                project.setUpdatedAt(Instant.now());
                projectRepository.save(project);
            }
        }
    }

    private void deleteProjectData(Project project, String confirmName) {
        String projectId = project.getId();
        String expected = project.getName() == null ? "" : project.getName();
        String actual = confirmName == null ? "" : confirmName.trim();
        if (!expected.equals(actual)) {
            throw ApiException.badRequest("Project name confirmation does not match");
        }

        List<MockInstance> instances = instanceRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        instances.forEach(instance -> runtimeRegistry.unregister(instance.getPublicTokenHash()));
        requestLogRepository.deleteByProjectId(projectId);
        instanceRepository.deleteByProjectId(projectId);
        specVersionRepository.deleteByProjectId(projectId);
        auditService.deleteProjectEvents(projectId);
        projectRepository.delete(project);
    }

    private Optional<AppUser> findUserByIdentifier(String identifier) {
        String normalized = identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (normalized.contains("@")) {
            return userRepository.findByEmail(normalized);
        }
        return userRepository.findByUsername(normalized);
    }

    private boolean hasAccessibleProjectNamed(String userId, String name, String excludedProjectId) {
        String normalizedName = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalizedName.isBlank()) {
            return false;
        }
        return projectRepository.findAccessibleByUserId(userId, Sort.unsorted())
                .stream()
                .anyMatch(project -> (excludedProjectId == null || !Objects.equals(project.getId(), excludedProjectId))
                        && project.getName() != null
                        && project.getName().trim().toLowerCase(Locale.ROOT).equals(normalizedName));
    }

    private Optional<ProjectRole> roleFor(Project project, String userId) {
        if (project.getOwnerId().equals(userId)) {
            return Optional.of(ProjectRole.OWNER);
        }
        return project.getMembers()
                .stream()
                .filter(member -> member.getUserId().equals(userId))
                .map(ProjectMember::getRole)
                .findFirst();
    }

}
