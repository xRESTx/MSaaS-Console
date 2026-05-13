package com.msaas.spec;

import com.msaas.common.ApiException;
import com.msaas.audit.AuditService;
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
    private final AuditService auditService;

    public SpecService(
            SpecVersionRepository specVersionRepository,
            ProjectService projectService,
            OpenApiContractParser parser,
            MockInstanceRepository instanceRepository,
            RequestLogRepository requestLogRepository,
            MockRuntimeRegistry runtimeRegistry,
            AuditService auditService
    ) {
        this.specVersionRepository = specVersionRepository;
        this.projectService = projectService;
        this.parser = parser;
        this.instanceRepository = instanceRepository;
        this.requestLogRepository = requestLogRepository;
        this.runtimeRegistry = runtimeRegistry;
        this.auditService = auditService;
    }

    public SpecVersion create(String actorId, String projectId, String name, String source) {
        projectService.requireProjectWriteAccess(projectId, actorId);
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
        SpecVersion saved = specVersionRepository.save(version);
        auditService.record(projectId, actorId, "SPEC_UPLOADED", "specVersion", saved.getId(), "Specification version uploaded");
        return saved;
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

    public SpecVersion requireWritableVersion(String versionId, String actorId) {
        SpecVersion version = specVersionRepository.findById(versionId)
                .orElseThrow(() -> ApiException.notFound("Specification version not found"));
        projectService.requireProjectWriteAccess(version.getProjectId(), actorId);
        return version;
    }

    public SpecVersion requireAccessibleVersion(String versionId, String actorId) {
        SpecVersion version = specVersionRepository.findById(versionId)
                .orElseThrow(() -> ApiException.notFound("Specification version not found"));
        projectService.requireProjectAccess(version.getProjectId(), actorId);
        return version;
    }

    public SpecVersion requireValidWritableVersion(String versionId, String actorId) {
        SpecVersion version = requireWritableVersion(versionId, actorId);
        if (version.getStatus() != ValidationStatus.VALID || version.getNormalizedContract() == null) {
            throw ApiException.badRequest("Specification version is not valid");
        }
        return version;
    }

    public void deleteVersion(String versionId, String actorId) {
        SpecVersion version = requireWritableVersion(versionId, actorId);
        List<MockInstance> instances = instanceRepository.findBySpecVersionIdOrderByCreatedAtDesc(versionId);
        instances.forEach(instance -> {
            runtimeRegistry.unregister(instance.getPublicTokenHash());
            requestLogRepository.deleteByInstanceId(instance.getId());
        });
        instanceRepository.deleteBySpecVersionId(versionId);
        specVersionRepository.delete(version);
        auditService.record(version.getProjectId(), actorId, "SPEC_DELETED", "specVersion", version.getId(), "Specification version deleted");
    }
}
