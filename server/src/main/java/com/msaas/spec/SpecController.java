package com.msaas.spec;

import com.msaas.security.AuthenticatedUser;
import com.msaas.spec.contract.MockResponseDefinition;
import com.msaas.spec.contract.MockRoute;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
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

    @GetMapping("/spec-versions/{versionId}/routes")
    public List<RouteView> routes(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String versionId) {
        SpecVersion version = specService.requireAccessibleVersion(versionId, user.id());
        if (version.getNormalizedContract() == null) {
            return List.of();
        }
        return version.getNormalizedContract().getRoutes().stream().map(RouteView::from).toList();
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

    public record RouteView(
            String method,
            String pathTemplate,
            String operationId,
            List<String> requiredQueryParameters,
            List<String> requiredHeaderParameters,
            boolean requestBodyRequired,
            List<ResponseView> responses
    ) {
        static RouteView from(MockRoute route) {
            return new RouteView(
                    route.getMethod(),
                    route.getPathTemplate(),
                    route.getOperationId(),
                    route.getRequiredQueryParameters(),
                    route.getRequiredHeaderParameters(),
                    route.isRequestBodyRequired(),
                    route.getResponses().values().stream().map(ResponseView::from).toList()
            );
        }
    }

    public record ResponseView(int statusCode, String contentType, List<String> examples) {
        static ResponseView from(MockResponseDefinition definition) {
            Map<String, Object> examples = definition.getExamples();
            return new ResponseView(definition.getStatusCode(), definition.getContentType(), List.copyOf(examples.keySet()));
        }
    }
}
