package com.msaas.runtime;

import com.msaas.instance.InstanceMode;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockScenario;
import com.msaas.spec.contract.NormalizedContract;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class RuntimeSlot {
    private final String instanceId;
    private final String projectId;
    private final String publicTokenHash;
    private final String publicTokenPreview;
    private final String workerKey;
    private final boolean requireApiKey;
    private final String apiKeyHash;
    private final InstanceMode mode;
    private final NormalizedContract contract;
    private final boolean rateLimitEnabled;
    private final int rateLimitRequests;
    private final int rateLimitWindowSeconds;
    private final List<MockScenario> scenarios;
    private final Instant loadedAt;
    private final Instant instanceUpdatedAt;
    private final AtomicReference<Instant> lastAccessedAt;
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final ConcurrentMap<String, ConcurrentMap<String, Object>> state = new ConcurrentHashMap<>();

    public RuntimeSlot(MockInstance instance) {
        this.instanceId = instance.getId();
        this.projectId = instance.getProjectId();
        this.publicTokenHash = instance.getPublicTokenHash();
        this.publicTokenPreview = instance.getPublicTokenPreview();
        this.workerKey = instance.getWorkerKey();
        this.requireApiKey = instance.isRequireApiKey();
        this.apiKeyHash = instance.getApiKeyHash();
        this.mode = instance.getMode();
        this.contract = instance.getContract();
        this.rateLimitEnabled = instance.isRateLimitEnabled();
        this.rateLimitRequests = instance.getRateLimitRequests();
        this.rateLimitWindowSeconds = instance.getRateLimitWindowSeconds();
        this.scenarios = new ArrayList<>(instance.getScenarios());
        this.loadedAt = Instant.now();
        this.lastAccessedAt = new AtomicReference<>(loadedAt);
        this.instanceUpdatedAt = instance.getUpdatedAt();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getPublicTokenHash() {
        return publicTokenHash;
    }

    public String getPublicTokenPreview() {
        return publicTokenPreview;
    }

    public String getWorkerKey() {
        return workerKey;
    }

    public boolean isRequireApiKey() {
        return requireApiKey;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public InstanceMode getMode() {
        return mode;
    }

    public NormalizedContract getContract() {
        return contract;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public int getRateLimitRequests() {
        return rateLimitRequests;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public List<MockScenario> getScenarios() {
        return scenarios;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt.get();
    }

    public int getActiveRequests() {
        return activeRequests.get();
    }

    public void touch() {
        lastAccessedAt.set(Instant.now());
    }

    public void beginRequest() {
        activeRequests.incrementAndGet();
        touch();
    }

    public void endRequest() {
        activeRequests.updateAndGet(current -> Math.max(0, current - 1));
        touch();
    }

    public boolean isEvictable() {
        return activeRequests.get() == 0;
    }

    public boolean isIdleExpired(Instant now, long ttlSeconds) {
        return isEvictable() && getLastAccessedAt().plusSeconds(ttlSeconds).isBefore(now);
    }

    void setLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt.set(lastAccessedAt);
    }

    public Instant getInstanceUpdatedAt() {
        return instanceUpdatedAt;
    }

    public ConcurrentMap<String, Object> collection(String key) {
        return state.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        state.forEach((collection, items) -> snapshot.put(collection, new LinkedHashMap<>(items)));
        return snapshot;
    }

    public void resetState() {
        state.clear();
    }
}
