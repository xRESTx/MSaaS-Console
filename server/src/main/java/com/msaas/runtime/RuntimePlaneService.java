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
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    @Scheduled(fixedDelayString = "${APP_RUNTIME_WORKER_CLEANUP_DELAY_MS:86400000}")
    void cleanupDownWorkers() {
        workerRepository.findAll().forEach(this::effectiveWorker);
        workerRepository.deleteByStatusAndLastHeartbeatAtBefore(
                RuntimeWorkerStatus.DOWN,
                Instant.now().minusSeconds(properties.getRuntime().getWorkerCleanupAfterSeconds())
        );
        workerRepository.deleteByStatusAndLastHeartbeatAtIsNull(RuntimeWorkerStatus.DOWN);
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
        cleanupDownWorkers();
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

    public RebalanceResult rebalanceRunningInstances() {
        if (properties.getRuntime().isEmbedded()) {
            return new RebalanceResult(0, 0, 0, Map.of());
        }
        List<RuntimeWorker> liveWorkers = workers().stream()
                .filter(this::live)
                .filter(worker -> !LOCAL_WORKER_KEY.equals(worker.getWorkerKey()))
                .sorted(Comparator.comparing(RuntimeWorker::getWorkerKey))
                .toList();
        if (liveWorkers.isEmpty()) {
            return new RebalanceResult(0, 0, 0, Map.of());
        }

        List<MockInstance> instances = instanceRepository.findByStatus(InstanceStatus.RUNNING)
                .stream()
                .sorted(Comparator.comparing(MockInstance::getId, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
        Instant now = Instant.now();
        int changed = 0;
        Map<String, Integer> distribution = liveWorkers.stream()
                .collect(Collectors.toMap(RuntimeWorker::getWorkerKey, RuntimeWorker::getSlotCount, (left, right) -> left, LinkedHashMap::new));
        Map<String, Deque<MockInstance>> instancesByWorker = new HashMap<>();
        liveWorkers.forEach(worker -> instancesByWorker.put(worker.getWorkerKey(), new ArrayDeque<>()));
        for (MockInstance instance : instances) {
            if (instancesByWorker.containsKey(instance.getWorkerKey())) {
                instancesByWorker.get(instance.getWorkerKey()).add(instance);
            }
        }

        while (true) {
            String sourceWorkerKey = hottestWorker(distribution);
            String targetWorkerKey = coldestWorker(distribution);
            if (sourceWorkerKey == null || targetWorkerKey == null || sourceWorkerKey.equals(targetWorkerKey)) {
                break;
            }
            int sourceSlots = distribution.getOrDefault(sourceWorkerKey, 0);
            int targetSlots = distribution.getOrDefault(targetWorkerKey, 0);
            if (sourceSlots - targetSlots <= 1) {
                break;
            }
            Deque<MockInstance> sourceInstances = instancesByWorker.get(sourceWorkerKey);
            if (sourceInstances == null || sourceInstances.isEmpty()) {
                distribution.put(sourceWorkerKey, targetSlots);
                continue;
            }
            MockInstance instance = sourceInstances.removeFirst();
            if (!targetWorkerKey.equals(instance.getWorkerKey())) {
                instance.setWorkerKey(targetWorkerKey);
                instance.setAssignedAt(now);
                instance.setUpdatedAt(now);
                instanceRepository.save(instance);
                instancesByWorker.computeIfAbsent(targetWorkerKey, ignored -> new ArrayDeque<>()).add(instance);
                changed += 1;
            }
            distribution.put(sourceWorkerKey, sourceSlots - 1);
            distribution.put(targetWorkerKey, targetSlots + 1);
        }

        return new RebalanceResult(liveWorkers.size(), instances.size(), changed, distribution);
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
        if (configured != null && !configured.isBlank() && (properties.getRuntime().isEmbedded() || !"local".equalsIgnoreCase(configured))) {
            return configured;
        }
        if (properties.getRuntime().isEmbedded()) {
            return "local";
        }
        String hostname = System.getenv("HOSTNAME");
        String port = System.getenv("SERVER_PORT");
        if (hostname == null || hostname.isBlank()) {
            hostname = "localhost";
        }
        if (port == null || port.isBlank()) {
            port = "8082";
        }
        return "http://" + hostname + ":" + port;
    }

    private String hottestWorker(Map<String, Integer> distribution) {
        return distribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String coldestWorker(Map<String, Integer> distribution) {
        return distribution.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public record RebalanceResult(
            int workerCount,
            int instanceCount,
            int reassignedCount,
            Map<String, Integer> distribution
    ) {
    }
}
