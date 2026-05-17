package com.msaas.instance;

import java.time.Instant;

public class MockProfile {
    private String id;
    private String name;
    private boolean faultProfileEnabled;
    private int faultErrorRate;
    private int faultStatusCode = 500;
    private int latencyMinMs;
    private int latencyMaxMs;
    private Instant createdAt;
    private Instant updatedAt;

    public MockProfile() {
    }

    public MockProfile(String id, String name, boolean faultProfileEnabled, int faultErrorRate, int faultStatusCode, int latencyMinMs, int latencyMaxMs) {
        this.id = id;
        setName(name);
        this.faultProfileEnabled = faultProfileEnabled;
        setFaultErrorRate(faultErrorRate);
        setFaultStatusCode(faultStatusCode);
        setLatencyMinMs(latencyMinMs);
        setLatencyMaxMs(latencyMaxMs);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null || name.isBlank() ? "profile" : name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? "profile" : name.trim();
    }

    public boolean isFaultProfileEnabled() {
        return faultProfileEnabled;
    }

    public void setFaultProfileEnabled(boolean faultProfileEnabled) {
        this.faultProfileEnabled = faultProfileEnabled;
    }

    public int getFaultErrorRate() {
        return Math.max(0, Math.min(100, faultErrorRate));
    }

    public void setFaultErrorRate(int faultErrorRate) {
        this.faultErrorRate = Math.max(0, Math.min(100, faultErrorRate));
    }

    public int getFaultStatusCode() {
        return faultStatusCode < 400 || faultStatusCode > 599 ? 500 : faultStatusCode;
    }

    public void setFaultStatusCode(int faultStatusCode) {
        this.faultStatusCode = faultStatusCode < 400 || faultStatusCode > 599 ? 500 : faultStatusCode;
    }

    public int getLatencyMinMs() {
        return Math.max(0, latencyMinMs);
    }

    public void setLatencyMinMs(int latencyMinMs) {
        this.latencyMinMs = Math.max(0, latencyMinMs);
    }

    public int getLatencyMaxMs() {
        return Math.max(getLatencyMinMs(), latencyMaxMs);
    }

    public void setLatencyMaxMs(int latencyMaxMs) {
        this.latencyMaxMs = Math.max(0, latencyMaxMs);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
