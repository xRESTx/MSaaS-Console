package com.msaas.runtime;

import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.instance.InstanceStatus;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RuntimePlaneService {
    public static final String LOCAL_WORKER_KEY = "local-java-warm-slots";

    private final RuntimeWorkerRepository workerRepository;
    private final MockInstanceRepository instanceRepository;
    private final MockRuntimeRegistry runtimeRegistry;
    private final MockStateStore stateStore;
    private final AppProperties properties;
    private final String resolvedWorkerKey;

    public RuntimePlaneService(
            RuntimeWorkerRepository workerRepository,
            MockInstanceRepository instanceRepository,
            MockRuntimeRegistry runtimeRegistry,
            MockStateStore stateStore,
            AppProperties properties
    ) {
        this.workerRepository = workerRepository;
        this.instanceRepository = instanceRepository;
        this.runtimeRegistry = runtimeRegistry;
        this.stateStore = stateStore;
        this.properties = properties;
        this.resolvedWorkerKey = resolveWorkerKey();
    }

    @PostConstruct
    void registerWorker() {
        heartbeatLocalWorker();
    }

    @Scheduled(fixedDelayString = "${APP_RUNTIME_HEARTBEAT_DELAY_MS:5000}")
    void heartbeatLocalWorker() {
        if (properties.getRuntime().isControl()) {
            return;
        }
        heartbeat(workerKey(), baseUrl(), RuntimeWorkerStatus.UP, runtimeRegistry.slotCount(), Map.of("kind", properties.getRuntime().getRole().name().toLowerCase()));
    }

    public RuntimeWorker heartbeat(String workerKey, String baseUrl, RuntimeWorkerStatus status, int slotCount, Map<String, String> labels) {
        RuntimeWorker worker = workerRepository.findByWorkerKey(workerKey)
                .orElseGet(() -> new RuntimeWorker(workerKey, baseUrl, status, slotCount, labels));
        worker.setBaseUrl(baseUrl);
        worker.setStatus(status == null ? RuntimeWorkerStatus.UP : status);
        worker.setSlotCount(slotCount);
        worker.setLabels(labels);
        worker.setLastHeartbeatAt(Instant.now());
        return workerRepository.save(worker);
    }

    public List<RuntimeWorker> workers() {
        heartbeatLocalWorker();
        return workerRepository.findAll().stream().map(this::effectiveWorker).toList();
    }

    public List<MockRuntimeRegistry.RuntimeSlotInfo> slots() {
        if (properties.getRuntime().isControl()) {
            return instanceRepository.findByStatus(InstanceStatus.RUNNING)
                    .stream()
                    .map(instance -> new MockRuntimeRegistry.RuntimeSlotInfo(
                            instance.getId(),
                            instance.getProjectId(),
                            instance.getPublicTokenPreview(),
                            instance.getWorkerKey(),
                            instance.getMode().name(),
                            instance.getAssignedAt(),
                            instance.getAssignedAt(),
                            0,
                            stateStore.snapshot(new RuntimeSlot(instance)).size()
                    ))
                    .toList();
        }
        return runtimeRegistry.slots();
    }

    public String assignWorkerKey() {
        if (properties.getRuntime().isEmbedded()) {
            return LOCAL_WORKER_KEY;
        }
        return selectLiveWorker()
                .map(RuntimeWorker::getWorkerKey)
                .orElseThrow(() -> ApiException.serviceUnavailable("No runtime worker is available"));
    }

    public RuntimeWorker workerFor(MockInstance instance) {
        if (properties.getRuntime().isEmbedded()) {
            return new RuntimeWorker(LOCAL_WORKER_KEY, "local", RuntimeWorkerStatus.UP, runtimeRegistry.slotCount(), Map.of("kind", "embedded-java"));
        }
        if (instance.getWorkerKey() == null || instance.getWorkerKey().isBlank()) {
            return reassign(instance);
        }
        Optional<RuntimeWorker> existing = workerRepository.findByWorkerKey(instance.getWorkerKey())
                .map(this::effectiveWorker)
                .filter(this::live);
        return existing.orElseGet(() -> reassign(instance));
    }

    public RuntimeWorker reassign(MockInstance instance) {
        RuntimeWorker worker = selectLiveWorker()
                .orElseThrow(() -> ApiException.serviceUnavailable("No runtime worker is available"));
        instance.setWorkerKey(worker.getWorkerKey());
        instance.setAssignedAt(Instant.now());
        instance.setUpdatedAt(Instant.now());
        instanceRepository.save(instance);
        return worker;
    }

    public void markDown(String workerKey) {
        workerRepository.findByWorkerKey(workerKey).ifPresent(worker -> {
            worker.setStatus(RuntimeWorkerStatus.DOWN);
            workerRepository.save(worker);
        });
    }

    public boolean localExecutionEnabled() {
        return properties.getRuntime().isEmbedded() || properties.getRuntime().isRuntime();
    }

    public boolean gatewayEnabled() {
        return properties.getRuntime().isControl();
    }

    public boolean internalSecretMatches(String value) {
        String expected = properties.getRuntime().getInternalSecret();
        return expected != null && !expected.isBlank() && expected.equals(value);
    }

    private Optional<RuntimeWorker> selectLiveWorker() {
        return workers().stream()
                .filter(this::live)
                .filter(worker -> !LOCAL_WORKER_KEY.equals(worker.getWorkerKey()))
                .min(Comparator
                        .comparingLong((RuntimeWorker worker) -> instanceRepository.countByWorkerKeyAndStatus(worker.getWorkerKey(), InstanceStatus.RUNNING))
                        .thenComparing(RuntimeWorker::getWorkerKey));
    }

    private RuntimeWorker effectiveWorker(RuntimeWorker worker) {
        if (stale(worker)) {
            worker.setStatus(RuntimeWorkerStatus.DOWN);
            return workerRepository.save(worker);
        }
        return worker;
    }

    private boolean live(RuntimeWorker worker) {
        return worker.getStatus() == RuntimeWorkerStatus.UP || worker.getStatus() == RuntimeWorkerStatus.DEGRADED;
    }

    private boolean stale(RuntimeWorker worker) {
        return worker.getLastHeartbeatAt() == null
                || worker.getLastHeartbeatAt().isBefore(Instant.now().minusSeconds(properties.getRuntime().getStaleAfterSeconds()));
    }

    private String workerKey() {
        return resolvedWorkerKey;
    }

    private String resolveWorkerKey() {
        String configured = properties.getRuntime().getWorkerKey();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (properties.getRuntime().isEmbedded()) {
            return LOCAL_WORKER_KEY;
        }
        String hostname = System.getenv("HOSTNAME");
        return hostname == null || hostname.isBlank() ? "runtime-" + java.util.UUID.randomUUID() : "runtime-" + hostname;
    }

    private String baseUrl() {
        String configured = properties.getRuntime().getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return properties.getRuntime().isEmbedded() ? "local" : "http://localhost:8082";
    }
}
