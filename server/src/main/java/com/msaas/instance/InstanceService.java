package com.msaas.instance;

import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.log.RequestLogRepository;
import com.msaas.project.ProjectService;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.spec.SpecService;
import com.msaas.spec.SpecVersion;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class InstanceService {
    private final MockInstanceRepository instanceRepository;
    private final SpecService specService;
    private final ProjectService projectService;
    private final PublicTokenGenerator tokenGenerator;
    private final MockRuntimeRegistry runtimeRegistry;
    private final AppProperties properties;
    private final RequestLogRepository requestLogRepository;

    public InstanceService(
            MockInstanceRepository instanceRepository,
            SpecService specService,
            ProjectService projectService,
            PublicTokenGenerator tokenGenerator,
            MockRuntimeRegistry runtimeRegistry,
            AppProperties properties,
            RequestLogRepository requestLogRepository
    ) {
        this.instanceRepository = instanceRepository;
        this.specService = specService;
        this.projectService = projectService;
        this.tokenGenerator = tokenGenerator;
        this.runtimeRegistry = runtimeRegistry;
        this.properties = properties;
        this.requestLogRepository = requestLogRepository;
    }

    public MockInstance publish(String ownerId, String specVersionId, InstanceMode mode) {
        SpecVersion version = specService.requireValidOwnedVersion(specVersionId, ownerId);
        String token = uniqueToken();
        MockInstance instance = new MockInstance(
                version.getProjectId(),
                version.getId(),
                token,
                publicUrl(token),
                mode == null ? InstanceMode.STATELESS : mode,
                version.getNormalizedContract()
        );
        MockInstance saved = instanceRepository.save(instance);
        runtimeRegistry.register(saved);
        return saved;
    }

    public void deleteInstance(String instanceId, String ownerId) {
        MockInstance instance = requireOwnedInstance(instanceId, ownerId);
        runtimeRegistry.unregister(instance.getPublicToken());
        requestLogRepository.deleteByInstanceId(instance.getId());
        instanceRepository.delete(instance);
    }

    public List<MockInstance> listForProject(String projectId) {
        return instanceRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public MockInstance requireOwnedInstance(String instanceId, String ownerId) {
        MockInstance instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> ApiException.notFound("Mock instance not found"));
        projectService.requireOwnedProject(instance.getProjectId(), ownerId);
        return instance;
    }

    public MockInstance resetState(String instanceId, String ownerId) {
        MockInstance instance = requireOwnedInstance(instanceId, ownerId);
        runtimeRegistry.reset(instance.getPublicToken());
        instance.setUpdatedAt(Instant.now());
        return instanceRepository.save(instance);
    }

    public MockInstance rotateToken(String instanceId, String ownerId) {
        MockInstance instance = requireOwnedInstance(instanceId, ownerId);
        String oldToken = instance.getPublicToken();
        String newToken = uniqueToken();
        instance.setPublicToken(newToken);
        instance.setPublicUrl(publicUrl(newToken));
        instance.setUpdatedAt(Instant.now());
        MockInstance saved = instanceRepository.save(instance);
        runtimeRegistry.unregister(oldToken);
        runtimeRegistry.register(saved);
        return saved;
    }

    private String uniqueToken() {
        String token;
        do {
            token = tokenGenerator.generate();
        } while (instanceRepository.findByPublicTokenAndStatus(token, InstanceStatus.RUNNING).isPresent());
        return token;
    }

    private String publicUrl(String token) {
        String base = properties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/mock/" + token;
    }
}
