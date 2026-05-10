package com.msaas.runtime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Document("runtime_workers")
public class RuntimeWorker {
    @Id
    private String id;

    @Indexed(unique = true)
    private String workerKey;

    private String baseUrl;
    private RuntimeWorkerStatus status;
    private int slotCount;
    private Map<String, String> labels = new LinkedHashMap<>();
    private Instant lastHeartbeatAt;

    public RuntimeWorker() {
    }

    public RuntimeWorker(String workerKey, String baseUrl, RuntimeWorkerStatus status, int slotCount, Map<String, String> labels) {
        this.workerKey = workerKey;
        this.baseUrl = baseUrl;
        this.status = status;
        this.slotCount = slotCount;
        this.labels = labels == null ? new LinkedHashMap<>() : labels;
        this.lastHeartbeatAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getWorkerKey() {
        return workerKey;
    }

    public void setWorkerKey(String workerKey) {
        this.workerKey = workerKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public RuntimeWorkerStatus getStatus() {
        return status;
    }

    public void setStatus(RuntimeWorkerStatus status) {
        this.status = status;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public void setSlotCount(int slotCount) {
        this.slotCount = slotCount;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels == null ? new LinkedHashMap<>() : labels;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public void setLastHeartbeatAt(Instant lastHeartbeatAt) {
        this.lastHeartbeatAt = lastHeartbeatAt;
    }
}
