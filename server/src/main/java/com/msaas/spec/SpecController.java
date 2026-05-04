package com.msaas.spec;

import com.msaas.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class SpecController {
    private final SpecService specService;

    public SpecController(SpecService specService) {
        this.specService = specService;
    }

    @PostMapping("/projects/{projectId}/spec-versions")
    public SpecVersionView create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String projectId,
            @Valid @RequestBody CreateSpecVersionRequest request
    ) {
        return SpecVersionView.from(specService.create(user.id(), projectId, request.name(), request.source()));
    }

    @DeleteMapping("/spec-versions/{versionId}")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String versionId) {
        specService.deleteVersion(versionId, user.id());
    }

    public record CreateSpecVersionRequest(
            @Size(max = 120) String name,
            @NotBlank String source
    ) {
    }

    public record SpecVersionView(
            String id,
            String projectId,
            int versionNumber,
            String name,
            ValidationStatus status,
            List<String> validationErrors,
            int routeCount,
            Instant createdAt
    ) {
        public static SpecVersionView from(SpecVersion version) {
            int routeCount = version.getNormalizedContract() == null ? 0 : version.getNormalizedContract().getRoutes().size();
            return new SpecVersionView(
                    version.getId(),
                    version.getProjectId(),
                    version.getVersionNumber(),
                    version.getName(),
                    version.getStatus(),
                    version.getValidationErrors(),
                    routeCount,
                    version.getCreatedAt()
            );
        }
    }
}
