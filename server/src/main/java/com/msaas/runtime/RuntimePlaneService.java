package com.msaas.runtime;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class RuntimePlaneService {
    public static final String LOCAL_WORKER_KEY = "local-java-warm-slots";

    private final RuntimeWorkerRepository workerRepository;
    private final MockRuntimeRegistry runtimeRegistry;

    public RuntimePlaneService(RuntimeWorkerRepository workerRepository, MockRuntimeRegistry runtimeRegistry) {
        this.workerRepository = workerRepository;
        this.runtimeRegistry = runtimeRegistry;
    }

    @PostConstruct
    void registerLocalWorker() {
        heartbeat(LOCAL_WORKER_KEY, "local", RuntimeWorkerStatus.UP, runtimeRegistry.slots().size(), Map.of("kind", "embedded-java"));
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
        heartbeat(LOCAL_WORKER_KEY, "local", RuntimeWorkerStatus.UP, runtimeRegistry.slots().size(), Map.of("kind", "embedded-java"));
        return workerRepository.findAll();
    }

    public List<MockRuntimeRegistry.RuntimeSlotInfo> slots() {
        return runtimeRegistry.slots();
    }
}
