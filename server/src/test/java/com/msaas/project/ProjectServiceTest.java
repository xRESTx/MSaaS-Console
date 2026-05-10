package com.msaas.project;

import com.msaas.audit.AuditService;
import com.msaas.common.ApiException;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.log.RequestLogRepository;
import com.msaas.runtime.MockRuntimeRegistry;
import com.msaas.spec.SpecVersionRepository;
import com.msaas.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private final ProjectRepository repository = mock(ProjectRepository.class);
    private final ProjectService service = new ProjectService(
            repository,
            mock(SpecVersionRepository.class),
            mock(MockInstanceRepository.class),
            mock(RequestLogRepository.class),
            mock(MockRuntimeRegistry.class),
            mock(UserRepository.class),
            mock(AuditService.class)
    );

    @Test
    void requiresProjectToBelongToCurrentOwner() {
        Project project = new Project("user-1", "Demo", "");
        when(repository.findById("project-1")).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.requireOwnedProject("project-1", "user-2"))
                .isInstanceOf(ApiException.class)
                .hasMessage("Project not found");

        verify(repository).findById("project-1");
    }
}
