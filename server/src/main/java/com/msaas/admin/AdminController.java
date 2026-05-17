package com.msaas.admin;

import com.msaas.audit.AuditEvent;
import com.msaas.audit.AuditEventRepository;
import com.msaas.audit.AuditService;
import com.msaas.common.ApiException;
import com.msaas.instance.InstanceMode;
import com.msaas.instance.InstanceService;
import com.msaas.instance.InstanceStatus;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.log.RequestLog;
import com.msaas.log.RequestLogRepository;
import com.msaas.project.Project;
import com.msaas.project.ProjectRepository;
import com.msaas.project.ProjectService;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.runtime.RuntimePlaneService;
import com.msaas.runtime.RuntimeWorker;
import com.msaas.security.AuthenticatedUser;
import com.msaas.spec.SpecVersionRepository;
import com.msaas.spec.ValidationStatus;
import com.msaas.user.AppUser;
import com.msaas.user.SystemRole;
import com.msaas.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SpecVersionRepository specVersionRepository;
    private final MockInstanceRepository instanceRepository;
    private final RequestLogRepository requestLogRepository;
    private final AuditEventRepository auditEventRepository;
    private final RuntimePlaneService runtimePlaneService;
    private final ProjectService projectService;
    private final InstanceService instanceService;
    private final AuditService auditService;

    public AdminController(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            SpecVersionRepository specVersionRepository,
            MockInstanceRepository instanceRepository,
            RequestLogRepository requestLogRepository,
            AuditEventRepository auditEventRepository,
            RuntimePlaneService runtimePlaneService,
            ProjectService projectService,
            InstanceService instanceService,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.specVersionRepository = specVersionRepository;
        this.instanceRepository = instanceRepository;
        this.requestLogRepository = requestLogRepository;
        this.auditEventRepository = auditEventRepository;
        this.runtimePlaneService = runtimePlaneService;
        this.projectService = projectService;
        this.instanceService = instanceService;
        this.auditService = auditService;
    }

    @GetMapping("/summary")
    public AdminSummary summary() {
        long logCount = requestLogRepository.count();
        double averageLatencyMs = logCount == 0
                ? 0
                : requestLogRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 500))
                .stream()
                .mapToLong(RequestLog::getLatencyMs)
                .average()
                .orElse(0);
        return new AdminSummary(
                userRepository.count(),
                projectRepository.count(),
                specVersionRepository.count(),
                specVersionRepository.countByStatus(ValidationStatus.INVALID),
                instanceRepository.count(),
                instanceRepository.findByStatus(InstanceStatus.RUNNING).size(),
                runtimePlaneService.workers().size(),
                runtimePlaneService.slots().size(),
                logCount,
                requestLogRepository.countByResponseStatusGreaterThanEqual(500),
                requestLogRepository.countByMatchedFalse(),
                requestLogRepository.countByError("Rate limit exceeded"),
                logCount == 0 ? 0 : Math.round((requestLogRepository.countByMatchedFalse() * 1000.0) / logCount) / 10.0,
                Math.round(averageLatencyMs)
        );
    }

    @GetMapping("/users")
    public List<AdminUserView> users(@RequestParam(defaultValue = "") String query) {
        List<AppUser> users = query == null || query.isBlank()
                ? userRepository.findAll()
                : userRepository.findByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByCreatedAtDesc(query, query);
        return users.stream()
                .map(this::ensureUsername)
                .sorted((left, right) -> nullSafeInstant(right.getCreatedAt()).compareTo(nullSafeInstant(left.getCreatedAt())))
                .map(user -> AdminUserView.from(user, projectRepository.countByOwnerId(user.getId())))
                .toList();
    }

    @GetMapping("/projects")
    public List<AdminProjectView> projects(@RequestParam(defaultValue = "") String query) {
        List<Project> projects = query == null || query.isBlank()
                ? projectRepository.findAllByOrderByCreatedAtDesc()
                : projectRepository.findByNameContainingIgnoreCaseOrderByCreatedAtDesc(query);
        Map<String, AppUser> users = userRepository.findAllById(projects.stream().map(Project::getOwnerId).toList())
                .stream()
                .map(this::ensureUsername)
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return projects.stream()
                .map(project -> AdminProjectView.from(project, owner(project, users), specVersionRepository.countByProjectId(project.getId()), instanceRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).size()))
                .toList();
    }

    @GetMapping("/instances")
    public List<AdminInstanceView> instances(@RequestParam(defaultValue = "500") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        return instanceRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, boundedLimit))
                .stream()
                .map(AdminInstanceView::from)
                .toList();
    }

    @GetMapping("/logs")
    public AdminPage<AdminLogView> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String userId
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), boundedPageSize(size), Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<RequestLog> logsPage;
        if (userId == null || userId.isBlank()) {
            logsPage = requestLogRepository.findAllByOrderByReceivedAtDesc(pageRequest);
        } else {
            List<String> projectIds = projectRepository.findAccessibleByUserId(userId, Sort.by(Sort.Direction.DESC, "createdAt"))
                    .stream()
                    .map(Project::getId)
                    .toList();
            logsPage = projectIds.isEmpty()
                    ? Page.empty(pageRequest)
                    : requestLogRepository.findByProjectIdInOrderByReceivedAtDesc(projectIds, pageRequest);
        }
        Map<String, Project> projects = projectsById(logsPage.getContent().stream().map(RequestLog::getProjectId).toList());
        Map<String, AppUser> owners = usersById(projects.values().stream().map(Project::getOwnerId).toList());
        return AdminPage.from(logsPage, log -> AdminLogView.from(log, projects.get(log.getProjectId()), owner(projects.get(log.getProjectId()), owners)));
    }

    @GetMapping("/audit")
    public AdminPage<AdminAuditView> audit(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String actorId
    ) {
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), boundedPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AuditEvent> auditPage = actorId == null || actorId.isBlank()
                ? auditEventRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                : auditEventRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageRequest);
        Map<String, AppUser> actors = usersById(auditPage.getContent().stream().map(AuditEvent::getActorId).toList());
        return AdminPage.from(auditPage, event -> AdminAuditView.from(event, actors.get(event.getActorId())));
    }

    @GetMapping("/runtime/workers")
    public List<AdminRuntimeWorkerView> workers() {
        return runtimePlaneService.workers().stream().map(AdminRuntimeWorkerView::from).toList();
    }

    @GetMapping("/runtime/slots")
    public List<MockRuntimeRegistry.RuntimeSlotInfo> slots() {
        return runtimePlaneService.slots();
    }

    @PostMapping("/users/{userId}/disable")
    public AdminUserView disableUser(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String userId) {
        if (actor.id().equals(userId)) {
            throw ApiException.badRequest("Admin cannot disable the current account");
        }
        AppUser user = ensureUsername(userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
        user.setDisabled(true);
        AppUser saved = userRepository.save(user);
        auditService.record(null, actor.id(), "ADMIN_USER_DISABLED", "user", saved.getId(), "User disabled by admin", Map.of("username", safeUsername(saved), "email", saved.getEmail()));
        return AdminUserView.from(saved, projectRepository.countByOwnerId(saved.getId()));
    }

    @PostMapping("/users/{userId}/enable")
    public AdminUserView enableUser(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String userId) {
        AppUser user = ensureUsername(userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
        user.setDisabled(false);
        AppUser saved = userRepository.save(user);
        auditService.record(null, actor.id(), "ADMIN_USER_ENABLED", "user", saved.getId(), "User enabled by admin", Map.of("username", safeUsername(saved), "email", saved.getEmail()));
        return AdminUserView.from(saved, projectRepository.countByOwnerId(saved.getId()));
    }

    @PostMapping("/users/{userId}/make-admin")
    public AdminUserView makeAdmin(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String userId) {
        AppUser user = ensureUsername(userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
        user.setSystemRole(SystemRole.ADMIN);
        AppUser saved = userRepository.save(user);
        auditService.record(null, actor.id(), "ADMIN_USER_ROLE_CHANGED", "user", saved.getId(), "User promoted to admin", Map.of("systemRole", saved.getSystemRole().name(), "username", safeUsername(saved)));
        return AdminUserView.from(saved, projectRepository.countByOwnerId(saved.getId()));
    }

    @PostMapping("/users/{userId}/revoke-admin")
    public AdminUserView revokeAdmin(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String userId) {
        if (actor.id().equals(userId)) {
            throw ApiException.badRequest("Admin cannot revoke the current account");
        }
        AppUser user = ensureUsername(userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
        user.setSystemRole(SystemRole.USER);
        AppUser saved = userRepository.save(user);
        auditService.record(null, actor.id(), "ADMIN_USER_ROLE_CHANGED", "user", saved.getId(), "Admin role revoked", Map.of("systemRole", saved.getSystemRole().name(), "username", safeUsername(saved)));
        return AdminUserView.from(saved, projectRepository.countByOwnerId(saved.getId()));
    }

    @DeleteMapping("/users/{userId}")
    public void deleteUser(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String userId, @RequestBody(required = false) AdminDeleteRequest request) {
        if (actor.id().equals(userId)) {
            throw ApiException.badRequest("Admin cannot delete the current account");
        }
        AppUser user = ensureUsername(userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found")));
        requireConfirmation(safeUsername(user), request);
        List<Project> ownedProjects = projectRepository.findByOwnerIdOrderByCreatedAtDesc(user.getId());
        for (Project project : ownedProjects) {
            projectService.deleteProjectAsAdmin(actor.id(), project.getId(), project.getName());
        }
        projectService.removeUserFromMemberships(user.getId());
        userRepository.delete(user);
        auditService.record(null, actor.id(), "ADMIN_USER_DELETED", "user", user.getId(), "User deleted by admin", Map.of(
                "username", safeUsername(user),
                "email", user.getEmail(),
                "ownedProjects", ownedProjects.size()
        ));
    }

    @DeleteMapping("/projects/{projectId}")
    public void deleteProject(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String projectId, @RequestBody(required = false) AdminDeleteRequest request) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> ApiException.notFound("Project not found"));
        requireConfirmation(project.getName(), request);
        projectService.deleteProjectAsAdmin(actor.id(), project.getId(), project.getName());
    }

    @DeleteMapping("/instances/{instanceId}")
    public void deleteInstance(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String instanceId) {
        instanceService.deleteInstanceAsAdmin(instanceId, actor.id());
    }

    private Instant nullSafeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private int boundedPageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }

    private Map<String, Project> projectsById(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(Project::getId, Function.identity()));
    }

    private Map<String, AppUser> usersById(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findAllById(ids)
                .stream()
                .map(this::ensureUsername)
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private AppUser owner(Project project, Map<String, AppUser> users) {
        if (project == null) {
            return null;
        }
        return users.get(project.getOwnerId());
    }

    private void requireConfirmation(String expected, AdminDeleteRequest request) {
        String actual = request == null || request.confirmName() == null ? "" : request.confirmName().trim();
        String safeExpected = expected == null ? "" : expected;
        if (!safeExpected.equals(actual)) {
            throw ApiException.badRequest("Confirmation does not match");
        }
    }

    private String safeUsername(AppUser user) {
        if (user == null) {
            return "unknown";
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return user.getEmail() == null ? user.getId() : user.getEmail();
    }

    private AppUser ensureUsername(AppUser user) {
        if (user == null || (user.getUsername() != null && !user.getUsername().isBlank())) {
            return user;
        }
        user.setUsername(issueUsername(user.getEmail(), user.getId()));
        return userRepository.save(user);
    }

    private String issueUsername(String email, String userId) {
        String base = usernameBaseFromEmail(email);
        if (base.length() < 3) {
            base = (base + "user").substring(0, 4);
        }
        if (base.length() > 28) {
            base = base.substring(0, 28);
        }
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            String tail = "-" + suffix++;
            String prefix = base.length() + tail.length() > 32 ? base.substring(0, 32 - tail.length()) : base;
            candidate = prefix + tail;
        }
        return candidate == null || candidate.isBlank() ? userId : candidate;
    }

    private String usernameBaseFromEmail(String email) {
        String local = email == null ? "user" : email.split("@", 2)[0];
        String normalized = local.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
        normalized = normalized.replaceAll("^[._-]+|[._-]+$", "");
        return normalized.isBlank() ? "user" : normalized;
    }

    public record AdminDeleteRequest(String confirmName) {
    }

    public record AdminPage<T>(
            List<T> items,
            long totalElements,
            int page,
            int size,
            int totalPages
    ) {
        public static <S, T> AdminPage<T> from(Page<S> source, Function<S, T> mapper) {
            return new AdminPage<>(
                    source.getContent().stream().map(mapper).toList(),
                    source.getTotalElements(),
                    source.getNumber(),
                    source.getSize(),
                    source.getTotalPages()
            );
        }
    }

    public record AdminSummary(
            long users,
            long projects,
            long specVersions,
            long invalidSpecVersions,
            long instances,
            long runningInstances,
            long runtimeWorkers,
            long runtimeSlots,
            long requestLogs,
            long serverErrors,
            long unmatchedRequests,
            long rateLimitEvents,
            double unmatchedRatio,
            long averageLatencyMs
    ) {
    }

    public record AdminUserView(
            String id,
            String email,
            String username,
            SystemRole systemRole,
            boolean disabled,
            long ownedProjects,
            Instant createdAt
    ) {
        public static AdminUserView from(AppUser user, long ownedProjects) {
            return new AdminUserView(user.getId(), user.getEmail(), user.getUsername(), user.getSystemRole(), user.isDisabled(), ownedProjects, user.getCreatedAt());
        }
    }

    public record AdminProjectView(
            String id,
            String ownerId,
            String ownerEmail,
            String ownerUsername,
            String name,
            String description,
            int memberCount,
            long specVersionCount,
            long instanceCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AdminProjectView from(Project project, AppUser owner, long specVersionCount, long instanceCount) {
            return new AdminProjectView(
                    project.getId(),
                    project.getOwnerId(),
                    owner == null ? "unknown" : owner.getEmail(),
                    owner == null ? "unknown" : owner.getUsername(),
                    project.getName(),
                    project.getDescription(),
                    project.getMembers().size() + 1,
                    specVersionCount,
                    instanceCount,
                    project.getCreatedAt(),
                    project.getUpdatedAt()
            );
        }
    }

    public record AdminInstanceView(
            String id,
            String projectId,
            String specVersionId,
            String tokenPreview,
            String apiKeyPreview,
            boolean requireApiKey,
            InstanceMode mode,
            InstanceStatus status,
            int routeCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AdminInstanceView from(MockInstance instance) {
            int routeCount = instance.getContract() == null ? 0 : instance.getContract().getRoutes().size();
            return new AdminInstanceView(
                    instance.getId(),
                    instance.getProjectId(),
                    instance.getSpecVersionId(),
                    instance.getPublicTokenPreview(),
                    instance.getApiKeyPreview(),
                    instance.isRequireApiKey(),
                    instance.getMode(),
                    instance.getStatus(),
                    routeCount,
                    instance.getCreatedAt(),
                    instance.getUpdatedAt()
            );
        }
    }

    public record AdminLogView(
            String id,
            String projectId,
            String projectName,
            String ownerId,
            String ownerEmail,
            String ownerUsername,
            String instanceId,
            String method,
            String path,
            String queryString,
            int responseStatus,
            boolean matched,
            String error,
            String responseSource,
            String profileName,
            List<String> appliedRuleIds,
            long latencyMs,
            Instant receivedAt
    ) {
        public static AdminLogView from(RequestLog log, Project project, AppUser owner) {
            return new AdminLogView(
                    log.getId(),
                    log.getProjectId(),
                    project == null ? "unknown" : project.getName(),
                    project == null ? null : project.getOwnerId(),
                    owner == null ? "unknown" : owner.getEmail(),
                    owner == null ? "unknown" : owner.getUsername(),
                    log.getInstanceId(),
                    log.getMethod(),
                    log.getPath(),
                    log.getQueryString(),
                    log.getResponseStatus(),
                    log.isMatched(),
                    log.getError(),
                    log.getResponseSource(),
                    log.getProfileName(),
                    log.getAppliedRuleIds(),
                    log.getLatencyMs(),
                    log.getReceivedAt()
            );
        }
    }

    public record AdminAuditView(
            String id,
            String projectId,
            String actorId,
            String actorEmail,
            String actorUsername,
            String action,
            String targetType,
            String targetId,
            String message,
            Map<String, Object> metadata,
            Instant createdAt
    ) {
        public static AdminAuditView from(AuditEvent event, AppUser actor) {
            return new AdminAuditView(
                    event.getId(),
                    event.getProjectId(),
                    event.getActorId(),
                    actor == null ? "unknown" : actor.getEmail(),
                    actor == null ? "unknown" : actor.getUsername(),
                    event.getAction(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getMessage(),
                    event.getMetadata(),
                    event.getCreatedAt()
            );
        }
    }

    public record AdminRuntimeWorkerView(
            String id,
            String workerKey,
            String baseUrl,
            String status,
            int slotCount,
            Map<String, String> labels,
            Instant lastHeartbeatAt
    ) {
        public static AdminRuntimeWorkerView from(RuntimeWorker worker) {
            return new AdminRuntimeWorkerView(
                    worker.getId(),
                    worker.getWorkerKey(),
                    worker.getBaseUrl(),
                    worker.getStatus().name(),
                    worker.getSlotCount(),
                    worker.getLabels(),
                    worker.getLastHeartbeatAt()
            );
        }
    }
}
