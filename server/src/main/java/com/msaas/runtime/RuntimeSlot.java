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

public class RuntimeSlot {
    private final String instanceId;
    private final String projectId;
    private final String publicTokenHash;
    private final String publicTokenPreview;
    private final boolean requireApiKey;
    private final String apiKeyHash;
    private final InstanceMode mode;
    private final NormalizedContract contract;
    private final boolean rateLimitEnabled;
    private final int rateLimitRequests;
    private final int rateLimitWindowSeconds;
    private final List<MockScenario> scenarios;
    private final Instant loadedAt;
    private final ConcurrentMap<String, ConcurrentMap<String, Object>> state = new ConcurrentHashMap<>();

    public RuntimeSlot(MockInstance instance) {
        this.instanceId = instance.getId();
        this.projectId = instance.getProjectId();
        this.publicTokenHash = instance.getPublicTokenHash();
        this.publicTokenPreview = instance.getPublicTokenPreview();
        this.requireApiKey = instance.isRequireApiKey();
        this.apiKeyHash = instance.getApiKeyHash();
        this.mode = instance.getMode();
        this.contract = instance.getContract();
        this.rateLimitEnabled = instance.isRateLimitEnabled();
        this.rateLimitRequests = instance.getRateLimitRequests();
        this.rateLimitWindowSeconds = instance.getRateLimitWindowSeconds();
        this.scenarios = new ArrayList<>(instance.getScenarios());
        this.loadedAt = Instant.now();
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
