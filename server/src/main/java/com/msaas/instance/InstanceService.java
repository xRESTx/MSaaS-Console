package com.msaas.instance;

import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.audit.AuditService;
import com.msaas.log.RequestLogRepository;
import com.msaas.project.ProjectService;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.security.SecretHashService;
import com.msaas.spec.SpecService;
import com.msaas.spec.SpecVersion;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class InstanceService {
    private final MockInstanceRepository instanceRepository;
    private final SpecService specService;
    private final ProjectService projectService;
    private final PublicTokenGenerator tokenGenerator;
    private final SecretHashService secretHashService;
    private final MockRuntimeRegistry runtimeRegistry;
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
        MockInstance saved = instanceRepository.save(instance);
        saved.setPublicUrl(publicUrl(publicToken.raw()));
        if (apiKey != null) {
            saved.setMockApiKey(apiKey.raw());
        }
        runtimeRegistry.register(saved);
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
        runtimeRegistry.reset(instance.getPublicTokenHash());
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_STATE_RESET", "mockInstance", instance.getId(), "Mock instance state reset");
        return saved;
    }

    public Map<String, Object> state(String instanceId, String actorId) {
        MockInstance instance = requireAccessibleInstance(instanceId, actorId);
        return runtimeRegistry.snapshot(instance.getPublicTokenHash());
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
        runtimeRegistry.register(saved);
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
        runtimeRegistry.register(saved);
        auditService.record(instance.getProjectId(), actorId, "INSTANCE_API_KEY_ROTATED", "mockInstance", instance.getId(), "Mock instance API key rotated");
        return saved;
    }

    private IssuedSecret uniquePublicToken() {
        IssuedSecret token;
        do {
            token = issueSecret();
        } while (instanceRepository.findByPublicTokenHashAndStatus(token.hash(), InstanceStatus.RUNNING).isPresent());
        return token;
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
}
