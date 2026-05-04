package com.msaas.spec;

import com.msaas.common.ApiException;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.log.RequestLogRepository;
import com.msaas.project.ProjectService;
import com.msaas.runtime.MockRuntimeRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpecService {
    private final SpecVersionRepository specVersionRepository;
    private final ProjectService projectService;
    private final OpenApiContractParser parser;
    private final MockInstanceRepository instanceRepository;
    private final RequestLogRepository requestLogRepository;
    private final MockRuntimeRegistry runtimeRegistry;

    public SpecService(
            SpecVersionRepository specVersionRepository,
            ProjectService projectService,
            OpenApiContractParser parser,
            MockInstanceRepository instanceRepository,
            RequestLogRepository requestLogRepository,
            MockRuntimeRegistry runtimeRegistry
    ) {
        this.specVersionRepository = specVersionRepository;
        this.projectService = projectService;
        this.parser = parser;
        this.instanceRepository = instanceRepository;
        this.requestLogRepository = requestLogRepository;
        this.runtimeRegistry = runtimeRegistry;
    }

    public SpecVersion create(String ownerId, String projectId, String name, String source) {
        projectService.requireOwnedProject(projectId, ownerId);
        int versionNumber = (int) specVersionRepository.countByProjectId(projectId) + 1;
        OpenApiContractParser.ParsedContract parsed = parser.parse(source);

        SpecVersion version = new SpecVersion(
                projectId,
                versionNumber,
                name == null || name.isBlank() ? "v" + versionNumber : name.trim(),
                source,
                parsed.valid() ? ValidationStatus.VALID : ValidationStatus.INVALID
        );
        version.setValidationErrors(parsed.errors());
        version.setNormalizedContract(parsed.contract());
        return specVersionRepository.save(version);
    }

    public List<SpecVersion> list(String projectId) {
        return specVersionRepository.findByProjectIdOrderByVersionNumberDesc(projectId);
    }

    public SpecVersion requireOwnedVersion(String versionId, String ownerId) {
        SpecVersion version = specVersionRepository.findById(versionId)
                .orElseThrow(() -> ApiException.notFound("Specification version not found"));
        projectService.requireOwnedProject(version.getProjectId(), ownerId);
        return version;
    }

    public SpecVersion requireValidOwnedVersion(String versionId, String ownerId) {
        SpecVersion version = requireOwnedVersion(versionId, ownerId);
        if (version.getStatus() != ValidationStatus.VALID || version.getNormalizedContract() == null) {
            throw ApiException.badRequest("Specification version is not valid");
        }
        return version;
    }

    public void deleteVersion(String versionId, String ownerId) {
        SpecVersion version = requireOwnedVersion(versionId, ownerId);
        List<MockInstance> instances = instanceRepository.findBySpecVersionIdOrderByCreatedAtDesc(versionId);
        instances.forEach(instance -> {
            runtimeRegistry.unregister(instance.getPublicToken());
            requestLogRepository.deleteByInstanceId(instance.getId());
        });
        instanceRepository.deleteBySpecVersionId(versionId);
        specVersionRepository.delete(version);
    }
}
