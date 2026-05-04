package com.msaas.runtime;

import com.msaas.instance.InstanceMode;
import com.msaas.instance.MockInstance;
import com.msaas.spec.contract.NormalizedContract;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RuntimeSlot {
    private final String instanceId;
    private final String projectId;
    private final String publicToken;
    private final InstanceMode mode;
    private final NormalizedContract contract;
    private final ConcurrentMap<String, ConcurrentMap<String, Object>> state = new ConcurrentHashMap<>();

    public RuntimeSlot(MockInstance instance) {
        this.instanceId = instance.getId();
        this.projectId = instance.getProjectId();
        this.publicToken = instance.getPublicToken();
        this.mode = instance.getMode();
        this.contract = instance.getContract();
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getPublicToken() {
        return publicToken;
    }

    public InstanceMode getMode() {
        return mode;
    }

    public NormalizedContract getContract() {
        return contract;
    }

    public ConcurrentMap<String, Object> collection(String key) {
        return state.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
    }

    public Map<String, ConcurrentMap<String, Object>> state() {
        return state;
    }

    public void resetState() {
        state.clear();
    }
}
