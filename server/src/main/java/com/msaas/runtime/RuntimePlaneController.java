package com.msaas.runtime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/runtime")
public class RuntimePlaneController {
    private final RuntimePlaneService runtimePlaneService;

    public RuntimePlaneController(RuntimePlaneService runtimePlaneService) {
        this.runtimePlaneService = runtimePlaneService;
    }

    @GetMapping("/workers")
    public List<RuntimeWorkerView> workers() {
        return runtimePlaneService.workers().stream().map(RuntimeWorkerView::from).toList();
    }

    @PostMapping("/workers/heartbeat")
    public RuntimeWorkerView heartbeat(@Valid @RequestBody RuntimeHeartbeatRequest request, HttpServletRequest httpRequest) {
        if (!runtimePlaneService.internalSecretMatches(httpRequest.getHeader("X-MSaaS-Internal-Secret"))) {
            throw com.msaas.common.ApiException.forbidden("Invalid runtime heartbeat secret");
        }
        return RuntimeWorkerView.from(runtimePlaneService.heartbeat(
                request.workerKey(),
                request.baseUrl(),
                request.status(),
                request.slotCount(),
                request.labels() == null ? Map.of() : request.labels()
        ));
    }

    @GetMapping("/slots")
    public List<MockRuntimeRegistry.RuntimeSlotInfo> slots() {
        return runtimePlaneService.slots();
    }

    @PostMapping("/rebalance")
    public RuntimePlaneService.RebalanceResult rebalance(HttpServletRequest httpRequest) {
        if (!runtimePlaneService.internalSecretMatches(httpRequest.getHeader("X-MSaaS-Internal-Secret"))) {
            throw com.msaas.common.ApiException.forbidden("Invalid runtime rebalance secret");
        }
        return runtimePlaneService.rebalanceRunningInstances();
    }

    public record RuntimeHeartbeatRequest(
            @NotBlank String workerKey,
            @NotBlank String baseUrl,
            RuntimeWorkerStatus status,
            int slotCount,
            Map<String, String> labels
    ) {
    }

    public record RuntimeWorkerView(
            String id,
            String workerKey,
            String baseUrl,
            RuntimeWorkerStatus status,
            int slotCount,
            Map<String, String> labels,
            Instant lastHeartbeatAt
    ) {
        public static RuntimeWorkerView from(RuntimeWorker worker) {
            return new RuntimeWorkerView(
                    worker.getId(),
                    worker.getWorkerKey(),
                    worker.getBaseUrl(),
                    worker.getStatus(),
                    worker.getSlotCount(),
                    worker.getLabels(),
                    worker.getLastHeartbeatAt()
            );
        }
    }
}
