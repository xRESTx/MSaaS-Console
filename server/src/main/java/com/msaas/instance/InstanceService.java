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
        if (settings.rateLimitEnabled() != null) {
            instance.setRateLimitEnabled(settings.rateLimitEnabled());
        }
        if (settings.rateLimitRequests() != null) {
            instance.setRateLimitRequests(settings.rateLimitRequests());
        }
        if (settings.rateLimitWindowSeconds() != null) {
            instance.setRateLimitWindowSeconds(settings.rateLimitWindowSeconds());
        }
        if (settings.smartResponsesEnabled() != null) {
            instance.setSmartResponsesEnabled(settings.smartResponsesEnabled());
        }
        instance.setSmartSeedMode(settings.smartSeedMode());
        if (settings.activeProfile() != null && !settings.activeProfile().isBlank()) {
            MockProfile profile = requireProfile(instance, settings.activeProfile());
            instance.activateProfileSettings(profile);
        }
        if (settings.faultProfileEnabled() != null) {
            instance.setFaultProfileEnabled(settings.faultProfileEnabled());
        }
        if (settings.faultErrorRate() != null) {
            instance.setFaultErrorRate(settings.faultErrorRate());
        }
        if (settings.faultStatusCode() != null) {
            instance.setFaultStatusCode(settings.faultStatusCode());
        }
        if (settings.latencyMinMs() != null) {
            instance.setLatencyMinMs(settings.latencyMinMs());
        }
        if (settings.latencyMaxMs() != null) {
            instance.setLatencyMaxMs(settings.latencyMaxMs());
        }
        syncActiveProfileSettings(instance);
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        registerLocal(saved);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_SETTINGS_UPDATED", "mockInstance", instance.getId(), "Mock instance settings updated", Map.ofEntries(
                Map.entry("rateLimitEnabled", saved.isRateLimitEnabled()),
                Map.entry("rateLimitRequests", saved.getRateLimitRequests()),
                Map.entry("rateLimitWindowSeconds", saved.getRateLimitWindowSeconds()),
                Map.entry("smartResponsesEnabled", saved.isSmartResponsesEnabled()),
                Map.entry("smartSeedMode", saved.getSmartSeedMode()),
                Map.entry("activeProfile", saved.getActiveProfile()),
                Map.entry("faultProfileEnabled", saved.isFaultProfileEnabled()),
                Map.entry("faultErrorRate", saved.getFaultErrorRate()),
                Map.entry("faultStatusCode", saved.getFaultStatusCode()),
                Map.entry("latencyMinMs", saved.getLatencyMinMs()),
                Map.entry("latencyMaxMs", saved.getLatencyMaxMs())
        ));
        return saved;
    }

    public List<MockProfile> listProfiles(String instanceId, String actorId) {
        MockInstance instance = requireAccessibleInstance(instanceId, actorId);
        ensureProfiles(instance);
        return instance.getProfiles();
    }

    public MockProfile createProfile(String instanceId, String actorId, ProfileRequest request) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        ensureProfiles(instance);
        MockProfile profile = profileFromRequest(new MockProfile(), request);
        profile.setId(UUID.randomUUID().toString());
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(profile.getCreatedAt());
        List<MockProfile> profiles = new ArrayList<>(instance.getProfiles());
        profiles.add(profile);
        instance.setProfiles(profiles);
        saveProfiles(instance, actorId, "PROFILE_CREATED", "Runtime profile created");
        return profile;
    }

    public MockProfile updateProfile(String instanceId, String profileId, String actorId, ProfileRequest request) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        ensureProfiles(instance);
        List<MockProfile> profiles = new ArrayList<>(instance.getProfiles());
        MockProfile profile = profiles.stream()
                .filter(item -> item.getId().equals(profileId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Runtime profile not found"));
        profileFromRequest(profile, request);
        profile.setUpdatedAt(Instant.now());
        instance.setProfiles(profiles);
        if (instance.getActiveProfile().equals(profile.getId())) {
            instance.activateProfileSettings(profile);
        }
        saveProfiles(instance, actorId, "PROFILE_UPDATED", "Runtime profile updated");
        return profile;
    }

    public void deleteProfile(String instanceId, String profileId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        ensureProfiles(instance);
        List<MockProfile> profiles = new ArrayList<>(instance.getProfiles());
        boolean removed = profiles.removeIf(item -> item.getId().equals(profileId));
        if (!removed) {
            throw ApiException.notFound("Runtime profile not found");
        }
        instance.setProfiles(profiles);
        if (instance.getActiveProfile().equals(profileId)) {
            instance.setActiveProfile(profiles.isEmpty() ? "custom" : profiles.getFirst().getId());
            instance.activateProfileSettings(instance.activeProfile());
        }
        saveProfiles(instance, actorId, "PROFILE_DELETED", "Runtime profile deleted");
    }

    public MockInstance activateProfile(String instanceId, String profileId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        ensureProfiles(instance);
        MockProfile profile = requireProfile(instance, profileId);
        instance.activateProfileSettings(profile);
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        registerLocal(saved);
        auditService.record(instance.getProjectId(), actorId, "PROFILE_ACTIVATED", "mockInstance", instance.getId(), "Runtime profile activated", Map.of(
                "profileId", profile.getId(),
                "profileName", profile.getName()
        ));
        return saved;
    }

    public List<ResponseRule> listResponseRules(String instanceId, String actorId) {
        MockInstance instance = requireAccessibleInstance(instanceId, actorId);
        return orderedResponseRules(instance);
    }

    public ResponseRule createResponseRule(String instanceId, String actorId, ResponseRuleRequest request) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        ResponseRule rule = responseRuleFromRequest(new ResponseRule(), request);
        ensureRuleTargetsExistingRoute(instance, rule);
        rule.setId(UUID.randomUUID().toString());
        rule.setCreatedAt(Instant.now());
        rule.setUpdatedAt(rule.getCreatedAt());
        List<ResponseRule> rules = new ArrayList<>(instance.getResponseRules());
        rules.add(rule);
        saveResponseRules(instance, actorId, rules, "RESPONSE_RULE_CREATED", "Response rule created");
        return rule;
    }

    public ResponseRule updateResponseRule(String instanceId, String ruleId, String actorId, ResponseRuleRequest request) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        List<ResponseRule> rules = new ArrayList<>(instance.getResponseRules());
        ResponseRule rule = rules.stream()
                .filter(item -> item.getId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Response rule not found"));
        responseRuleFromRequest(rule, request);
        ensureRuleTargetsExistingRoute(instance, rule);
        rule.setUpdatedAt(Instant.now());
        saveResponseRules(instance, actorId, rules, "RESPONSE_RULE_UPDATED", "Response rule updated");
        return rule;
    }

    public void deleteResponseRule(String instanceId, String ruleId, String actorId) {
        MockInstance instance = requireWritableInstance(instanceId, actorId);
        List<ResponseRule> rules = new ArrayList<>(instance.getResponseRules());
        boolean removed = rules.removeIf(item -> item.getId().equals(ruleId));
        if (!removed) {
            throw ApiException.notFound("Response rule not found");
        }
        saveResponseRules(instance, actorId, rules, "RESPONSE_RULE_DELETED", "Response rule deleted");
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

    private List<ResponseRule> orderedResponseRules(MockInstance instance) {
        return instance.getResponseRules().stream()
                .sorted(Comparator.comparingInt(ResponseRule::getPriority).reversed())
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

    private ResponseRule responseRuleFromRequest(ResponseRule rule, ResponseRuleRequest request) {
        rule.setName(request.name());
        rule.setEnabled(request.enabled());
        rule.setPriority(request.priority());
        rule.setOperationId(request.operationId());
        rule.setMethod(request.method());
        rule.setPathTemplate(request.pathTemplate());
        rule.setFieldPath(request.fieldPath());
        rule.setType(request.type());
        rule.setFixedValue(request.fixedValue());
        rule.setMinValue(request.minValue());
        rule.setMaxValue(request.maxValue());
        rule.setEnumValues(request.enumValues());
        rule.setTemplate(request.template());
        return rule;
    }

    private void saveResponseRules(MockInstance instance, String actorId, List<ResponseRule> rules, String action, String message) {
        instance.setResponseRules(rules);
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        registerLocal(saved);
        auditService.record(saved.getProjectId(), actorId, action, "responseRule", saved.getId(), message);
    }

    private void ensureRuleTargetsExistingRoute(MockInstance instance, ResponseRule rule) {
        if (rule.getFieldPath() == null || rule.getFieldPath().isBlank()) {
            throw ApiException.badRequest("Response rule field path is required");
        }
        if (instance.getContract() == null || instance.getContract().getRoutes() == null) {
            throw ApiException.badRequest("Instance contract is not loaded");
        }
        boolean exists = instance.getContract().getRoutes().stream().anyMatch(route -> {
            if (rule.getOperationId() != null && !rule.getOperationId().isBlank() && route.getOperationId() != null) {
                return rule.getOperationId().equals(route.getOperationId());
            }
            return rule.getMethod() != null
                    && rule.getPathTemplate() != null
                    && rule.getMethod().equalsIgnoreCase(route.getMethod())
                    && rule.getPathTemplate().equals(route.getPathTemplate());
        });
        if (!exists) {
            throw ApiException.badRequest("Response rule must target an existing OpenAPI operation");
        }
    }

    private void ensureProfiles(MockInstance instance) {
        if (instance.getProfiles().isEmpty()) {
            instance.setProfiles(MockInstance.defaultProfiles());
        }
    }

    private MockProfile requireProfile(MockInstance instance, String profileId) {
        return instance.getProfiles().stream()
                .filter(profile -> profile.getId().equals(profileId) || profile.getName().equalsIgnoreCase(profileId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Runtime profile not found"));
    }

    private MockProfile profileFromRequest(MockProfile profile, ProfileRequest request) {
        profile.setName(request.name());
        profile.setFaultProfileEnabled(request.faultProfileEnabled());
        profile.setFaultErrorRate(request.faultErrorRate());
        profile.setFaultStatusCode(request.faultStatusCode());
        profile.setLatencyMinMs(request.latencyMinMs());
        profile.setLatencyMaxMs(request.latencyMaxMs());
        return profile;
    }

    private void syncActiveProfileSettings(MockInstance instance) {
        for (MockProfile profile : instance.getProfiles()) {
            if (instance.getActiveProfile().equals(profile.getId())) {
                profile.setFaultProfileEnabled(instance.isFaultProfileEnabled());
                profile.setFaultErrorRate(instance.getFaultErrorRate());
                profile.setFaultStatusCode(instance.getFaultStatusCode());
                profile.setLatencyMinMs(instance.getLatencyMinMs());
                profile.setLatencyMaxMs(instance.getLatencyMaxMs());
                profile.setUpdatedAt(Instant.now());
                return;
            }
        }
    }

    private void saveProfiles(MockInstance instance, String actorId, String action, String message) {
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        registerLocal(saved);
        auditService.record(saved.getProjectId(), actorId, action, "runtimeProfile", saved.getId(), message);
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

    public record InstanceSettings(
            Boolean rateLimitEnabled,
            Integer rateLimitRequests,
            Integer rateLimitWindowSeconds,
            Boolean smartResponsesEnabled,
            String smartSeedMode,
            Boolean faultProfileEnabled,
            Integer faultErrorRate,
            Integer faultStatusCode,
            Integer latencyMinMs,
            Integer latencyMaxMs,
            String activeProfile
    ) {
    }

    public record ProfileRequest(
            String name,
            boolean faultProfileEnabled,
            int faultErrorRate,
            int faultStatusCode,
            int latencyMinMs,
            int latencyMaxMs
    ) {
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

    public record ResponseRuleRequest(
            String name,
            boolean enabled,
            int priority,
            String operationId,
            String method,
            String pathTemplate,
            String fieldPath,
            String type,
            Object fixedValue,
            Integer minValue,
            Integer maxValue,
            List<Object> enumValues,
            String template
    ) {
    }
}
