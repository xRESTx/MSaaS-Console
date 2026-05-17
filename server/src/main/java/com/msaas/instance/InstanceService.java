package com.msaas.instance;

import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.audit.AuditService;
import com.msaas.log.RequestLogRepository;
import com.msaas.project.ProjectService;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.runtime.MockStateStore;
import com.msaas.runtime.RuntimePlaneService;
import com.msaas.runtime.RuntimeSlot;
import com.msaas.security.SecretHashService;
import com.msaas.spec.SpecService;
import com.msaas.spec.SpecVersion;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InstanceService {
    private final MockInstanceRepository instanceRepository;
    private final SpecService specService;
    private final ProjectService projectService;
    private final PublicTokenGenerator tokenGenerator;
    private final SecretHashService secretHashService;
    private final MockRuntimeRegistry runtimeRegistry;
    private final MockStateStore stateStore;
    private final RuntimePlaneService runtimePlaneService;
    private final AppProperties properties;
    private final RequestLogRepository requestLogRepository;
    private final AuditService auditService;

    public InstanceService(
            MockInstanceRepository instanceRepository,
            SpecService specService,
            ProjectService projectService,
            PublicTokenGenerator tokenGenerator,
            SecretHashService secretHashService,
            MockRuntimeRegistry runtimeRegistry,
            MockStateStore stateStore,
            RuntimePlaneService runtimePlaneService,
            AppProperties properties,
            RequestLogRepository requestLogRepository,
            AuditService auditService
    ) {
        this.instanceRepository = instanceRepository;
        this.specService = specService;
        this.projectService = projectService;
        this.tokenGenerator = tokenGenerator;
        this.secretHashService = secretHashService;
        this.runtimeRegistry = runtimeRegistry;
        this.stateStore = stateStore;
        this.runtimePlaneService = runtimePlaneService;
        this.properties = properties;
        this.requestLogRepository = requestLogRepository;
        this.auditService = auditService;
    }

    public MockInstance publish(String actorId, String specVersionId, InstanceMode mode, boolean requireApiKey) {
        SpecVersion version = specService.requireValidWritableVersion(specVersionId, actorId);
        IssuedSecret publicToken = uniquePublicToken();
        IssuedSecret apiKey = requireApiKey ? issueSecret() : null;
        MockInstance instance = new MockInstance(
                version.getProjectId(),
                version.getId(),
                publicToken.hash(),
                publicToken.preview(),
                mode == null ? InstanceMode.STATELESS : mode,
                version.getNormalizedContract(),
                requireApiKey,
                apiKey == null ? null : apiKey.hash(),
                apiKey == null ? null : apiKey.preview()
        );
        instance.setWorkerKey(runtimePlaneService.assignWorkerKey());
        instance.setAssignedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        saved.setPublicUrl(publicUrl(publicToken.raw()));
        if (apiKey != null) {
            saved.setMockApiKey(apiKey.raw());
        }
        registerLocal(saved);
        auditService.record(version.getProjectId(), actorId, "INSTANCE_PUBLISHED", "mockInstance", saved.getId(), "Mock instance published");
        return saved;
    }

    public void deleteInstance(String instanceId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        deleteInstanceData(instance, actorId, "INSTANCE_DELETED", "Mock instance deleted");
    }

    public MockInstance deleteInstanceAsAdmin(String instanceId, String actorId) {
        MockInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> ApiException.notFound("Mock instance not found"));
        deleteInstanceData(instance, actorId, "ADMIN_INSTANCE_DELETED", "Mock instance deleted by admin");
        return instance;
    }

    private void deleteInstanceData(MockInstance instance, String actorId, String action, String message) {
        runtimeRegistry.unregister(instance.getPublicTokenHash());
        stateStore.reset(new RuntimeSlot(instance));
        requestLogRepository.deleteByInstanceId(instance.getId());
        instanceRepository.delete(instance);
        auditService.record(instance.getProjectId(), actorId, action, "mockInstance", instance.getId(), message);
    }

    public List<MockInstance> listForProject(String projectId) {
        return instanceRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public MockInstance requireAccessibleInstance(String instanceId, String actorId) {
        MockInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> ApiException.notFound("Mock instance not found"));
        projectService.requireProjectAccess(instance.getProjectId(), actorId);
        return instance;
    }

    public MockInstance requireWritableInstance(String instanceId, String actorId) {
        MockInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> ApiException.notFound("Mock instance not found"));
        projectService.requireProjectWriteAccess(instance.getProjectId(), actorId);
        return instance;
    }

    public MockInstance requireOwnedInstance(String instanceId, String ownerId) {
        MockInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> ApiException.notFound("Mock instance not found"));
        projectService.requireOwnedProject(instance.getProjectId(), ownerId);
        return instance;
    }

    public MockInstance resetState(String instanceId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        if (runtimePlaneService.localExecutionEnabled()) {
            runtimeRegistry.reset(instance.getPublicTokenHash());
        } else {
            stateStore.reset(new RuntimeSlot(instance));
        }
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_STATE_RESET", "mockInstance", instance.getId(), "Mock instance state reset");
        return saved;
    }

    public Map<String, Object> state(String instanceId, String actorId) {
        MockInstance instance = requireAccessibleInstance(instanceId, actorId);
        if (runtimePlaneService.localExecutionEnabled()) {
            return runtimeRegistry.snapshot(instance.getPublicTokenHash());
        }
        return stateStore.snapshot(new RuntimeSlot(instance));
    }

    public MockInstance rotateToken(String instanceId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        String oldTokenHash = instance.getPublicTokenHash();
        IssuedSecret publicToken = uniquePublicToken();
        instance.setPublicTokenHash(publicToken.hash());
        instance.setPublicTokenPreview(publicToken.preview());
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        saved.setPublicUrl(publicUrl(publicToken.raw()));
        runtimeRegistry.unregister(oldTokenHash);
        registerLocal(saved);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_TOKEN_ROTATED", "mockInstance", instance.getId(), "Mock instance token rotated");
        return saved;
    }

    public MockInstance rotateApiKey(String instanceId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        IssuedSecret apiKey = issueSecret();
        instance.setRequireApiKey(true);
        instance.setApiKeyHash(apiKey.hash());
        instance.setApiKeyPreview(apiKey.preview());
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        saved.setMockApiKey(apiKey.raw());
        registerLocal(saved);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_API_KEY_ROTATED", "mockInstance", instance.getId(), "Mock instance API key rotated");
        return saved;
    }

    public MockInstance updateSettings(String instanceId, String actorId, InstanceSettings settings) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        instance.setRateLimitEnabled(settings.rateLimitEnabled());
        instance.setRateLimitRequests(settings.rateLimitRequests());
        instance.setRateLimitWindowSeconds(settings.rateLimitWindowSeconds());
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        registerLocal(saved);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_SETTINGS_UPDATED", "mockInstance", instance.getId(), "Mock instance settings updated", Map.of(
                "rateLimitEnabled", saved.isRateLimitEnabled(),
                "rateLimitRequests", saved.getRateLimitRequests(),
                "rateLimitWindowSeconds", saved.getRateLimitWindowSeconds()
        ));
        return saved;
    }

    public List<MockScenario> listScenarios(String instanceId, String actorId) {
        MockInstance instance = requireAccessibleInstance(instanceId, actorId);
        return orderedScenarios(instance);
    }

    public MockScenario createScenario(String instanceId, String actorId, ScenarioRequest request) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        MockScenario scenario = scenarioFromRequest(new MockScenario(), request);
        scenario.setId(UUID.randomUUID().toString());
        scenario.setCreatedAt(Instant.now());
        scenario.setUpdatedAt(scenario.getCreatedAt());
        List<MockScenario> scenarios = new ArrayList<>(instance.getScenarios());
        scenarios.add(scenario);
        saveScenarios(instance, actorId, scenarios, "SCENARIO_CREATED", "Mock scenario created");
        return scenario;
    }

    public MockScenario updateScenario(String instanceId, String scenarioId, String actorId, ScenarioRequest request) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        List<MockScenario> scenarios = new ArrayList<>(instance.getScenarios());
        MockScenario scenario = scenarios.stream()
                .filter(item -> item.getId().equals(scenarioId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Scenario not found"));
        scenarioFromRequest(scenario, request);
        scenario.setUpdatedAt(Instant.now());
        saveScenarios(instance, actorId, scenarios, "SCENARIO_UPDATED", "Mock scenario updated");
        return scenario;
    }

    public void deleteScenario(String instanceId, String scenarioId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        List<MockScenario> scenarios = new ArrayList<>(instance.getScenarios());
        boolean removed = scenarios.removeIf(item -> item.getId().equals(scenarioId));
        if (!removed) {
            throw ApiException.notFound("Scenario not found");
        }
        saveScenarios(instance, actorId, scenarios, "SCENARIO_DELETED", "Mock scenario deleted");
    }

    private List<MockScenario> orderedScenarios(MockInstance instance) {
        return instance.getScenarios().stream()
                .sorted(Comparator.comparingInt(MockScenario::getPriority).reversed())
                .toList();
    }

    private MockScenario scenarioFromRequest(MockScenario scenario, ScenarioRequest request) {
        scenario.setName(request.name());
        scenario.setEnabled(request.enabled());
        scenario.setPriority(request.priority());
        scenario.setOperationId(request.operationId());
        scenario.setMethod(request.method());
        scenario.setPathTemplate(request.pathTemplate());
        scenario.setStatusCode(request.statusCode());
        scenario.setContentType(request.contentType());
        scenario.setBody(request.body());
        scenario.setHeaders(request.headers());
        scenario.setDelayMs(request.delayMs());
        return scenario;
    }

    private void saveScenarios(MockInstance instance, String actorId, List<MockScenario> scenarios, String action, String message) {
        instance.setScenarios(scenarios);
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        registerLocal(saved);
        auditService.record(saved.getProjectId(), actorId, action, "mockScenario", saved.getId(), message);
    }

    private IssuedSecret uniquePublicToken() {
        IssuedSecret token;
        do {
            token = issueSecret();
        } while (instanceRepository.findByPublicTokenHashAndStatus(token.hash(), InstanceStatus.RUNNING).isPresent());
        return token;
    }

    private void registerLocal(MockInstance instance) {
        if (runtimePlaneService.localExecutionEnabled()) {
            runtimeRegistry.register(instance);
        }
    }

    private IssuedSecret issueSecret() {
        String raw = tokenGenerator.generate();
        return new IssuedSecret(raw, secretHashService.hash(raw), secretHashService.preview(raw));
    }

    private String publicUrl(String token) {
        String base = properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/mock/" + token;
    }

    private record IssuedSecret(String raw, String hash, String preview) {
    }

    public record InstanceSettings(boolean rateLimitEnabled, int rateLimitRequests, int rateLimitWindowSeconds) {
    }

    public record ScenarioRequest(
            String name,
            boolean enabled,
            int priority,
            String operationId,
            String method,
            String pathTemplate,
            Integer statusCode,
            String contentType,
            Object body,
            Map<String, String> headers,
            long delayMs
    ) {
    }
}
