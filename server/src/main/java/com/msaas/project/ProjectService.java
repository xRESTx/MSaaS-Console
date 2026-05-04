package com.msaas.project;

import com.msaas.common.ApiException;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.log.RequestLogRepository;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.spec.SpecVersionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final SpecVersionRepository specVersionRepository;
    private final MockInstanceRepository instanceRepository;
    private final RequestLogRepository requestLogRepository;
    private final MockRuntimeRegistry runtimeRegistry;

    public ProjectService(
            ProjectRepository projectRepository,
            SpecVersionRepository specVersionRepository,
            MockInstanceRepository instanceRepository,
            RequestLogRepository requestLogRepository,
            MockRuntimeRegistry runtimeRegistry
    ) {
        this.projectRepository = projectRepository;
        this.specVersionRepository = specVersionRepository;
        this.instanceRepository = instanceRepository;
        this.requestLogRepository = requestLogRepository;
        this.runtimeRegistry = runtimeRegistry;
    }

    public Project create(String ownerId, String name, String description) {
        return projectRepository.save(new Project(ownerId, name.trim(), description == null ? "" : description.trim()));
    }

    public List<Project> listForOwner(String ownerId) {
        return projectRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public Project requireOwnedProject(String projectId, String ownerId) {
        return projectRepository.findByIdAndOwnerId(projectId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
    }

    public void deleteProject(String ownerId, String projectId, String confirmName) {
        Project project = requireOwnedProject(projectId, ownerId);
        String expected = project.getName() == null ? "" : project.getName();
        String actual = confirmName == null ? "" : confirmName.trim();
        if (!expected.equals(actual)) {
            throw ApiException.badRequest("Project name confirmation does not match");
        }

        List<MockInstance> instances = instanceRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        instances.forEach(instance -> runtimeRegistry.unregister(instance.getPublicToken()));
        requestLogRepository.deleteByProjectId(projectId);
        instanceRepository.deleteByProjectId(projectId);
        specVersionRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
    }
}
