package com.msaas.instance;

import com.msaas.log.RequestLog;
import com.msaas.log.RequestLogRepository;
import com.msaas.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api")
public class InstanceController {
    private final InstanceService instanceService;
    private final RequestLogRepository requestLogRepository;

    public InstanceController(InstanceService instanceService, RequestLogRepository requestLogRepository) {
        this.instanceService = instanceService;
        this.requestLogRepository = requestLogRepository;
    }

    @PostMapping("/spec-versions/{versionId}/publish")
    public InstanceView publish(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String versionId,
            @Valid @RequestBody PublishRequest request
    ) {
        return InstanceView.from(instanceService.publish(user.id(), versionId, request.mode(), request.requireApiKey()));
    }

    @GetMapping("/instances/{instanceId}")
    public InstanceView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String instanceId) {
        return InstanceView.from(instanceService.requireAccessibleInstance(instanceId, user.id()));
    }

    @DeleteMapping("/instances/{instanceId}")
    public void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String instanceId) {
        instanceService.deleteInstance(instanceId, user.id());
    }

    @PostMapping("/instances/{instanceId}/reset-state")
    public InstanceView resetState(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String instanceId) {
        return InstanceView.from(instanceService.resetState(instanceId, user.id()));
    }

    @GetMapping("/instances/{instanceId}/state")
    public Map<String, Object> state(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String instanceId) {
        return instanceService.state(instanceId, user.id());
    }

    @PostMapping("/instances/{instanceId}/rotate-token")
    public InstanceView rotateToken(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String instanceId) {
        return InstanceView.from(instanceService.rotateToken(instanceId, user.id()));
    }

    @PostMapping("/instances/{instanceId}/rotate-api-key")
    public InstanceView rotateApiKey(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String instanceId) {
        return InstanceView.from(instanceService.rotateApiKey(instanceId, user.id()));
    }

    @GetMapping("/instances/{instanceId}/logs")
    public List<RequestLogView> logs(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable String instanceId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        MockInstance instance = instanceService.requireAccessibleInstance(instanceId, user.id());
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return requestLogRepository.findByInstanceIdOrderByReceivedAtDesc(instance.getId(), PageRequest.of(0, boundedLimit))
                .stream()
                .map(RequestLogView::from)
                .toList();
    }

    public record PublishRequest(InstanceMode mode, boolean requireApiKey) {
    }

    public record InstanceView(
            String id,
            String projectId,
            String specVersionId,
            String publicUrl,
            String tokenPreview,
            String mockApiKey,
            String apiKeyPreview,
            boolean requireApiKey,
            InstanceMode mode,
            InstanceStatus status,
            int routeCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static InstanceView from(MockInstance instance) {
            int routeCount = instance.getContract() == null ? 0 : instance.getContract().getRoutes().size();
            return new InstanceView(
                    instance.getId(),
                    instance.getProjectId(),
                    instance.getSpecVersionId(),
                    instance.getPublicUrl(),
                    instance.getPublicTokenPreview(),
                    instance.getMockApiKey(),
                    instance.getApiKeyPreview(),
                    instance.isRequireApiKey(),
                    instance.getMode(),
                    instance.getStatus(),
                    routeCount,
                    instance.getCreatedAt(),
                    instance.getUpdatedAt()
            );
        }
    }

    public record RequestLogView(
            String id,
            String instanceId,
            String method,
            String path,
            String queryString,
            Map<String, String> requestHeaders,
            String requestBody,
            int responseStatus,
            boolean matched,
            String error,
            long latencyMs,
            Instant receivedAt
    ) {
        public static RequestLogView from(RequestLog log) {
            return new RequestLogView(
                    log.getId(),
                    log.getInstanceId(),
                    log.getMethod(),
                    log.getPath(),
                    log.getQueryString(),
                    log.getRequestHeaders(),
                    log.getRequestBody(),
                    log.getResponseStatus(),
                    log.isMatched(),
                    log.getError(),
                    log.getLatencyMs(),
                    log.getReceivedAt()
            );
        }
    }
}
