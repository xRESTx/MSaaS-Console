package com.msaas.instance;

import com.msaas.spec.contract.NormalizedContract;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("mock_instances")
public class MockInstance {
    @Id
    private String id;

    @Indexed
    private String projectId;

    @Indexed
    private String specVersionId;

    @Indexed(unique = true, sparse = true)
    private String publicTokenHash;

    @Field("publicToken")
    private String legacyPublicTokenHash;

    private String publicTokenPreview;
    private boolean requireApiKey;
    private String apiKeyHash;
    private String apiKeyPreview;
    private InstanceMode mode;
    private InstanceStatus status;
    private NormalizedContract contract;
    private boolean rateLimitEnabled = true;
    private int rateLimitRequests = 120;
    private int rateLimitWindowSeconds = 60;
    private Boolean smartResponsesEnabled = true;
    private String smartSeedMode = "STABLE";
    private List<MockScenario> scenarios = new ArrayList<>();
    private List<ResponseRule> responseRules = new ArrayList<>();
    private boolean faultProfileEnabled;
    private int faultErrorRate;
    private int faultStatusCode = 500;
    private int latencyMinMs;
    private int latencyMaxMs;
    private String activeProfile = "dev";
    private List<MockProfile> profiles;
    private String workerKey;
    private Instant assignedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Transient
    private String publicUrl;

    @Transient
    private String mockApiKey;

    public MockInstance() {
    }

    public MockInstance(
            String projectId,
            String specVersionId,
            String publicTokenHash,
            String publicTokenPreview,
            InstanceMode mode,
            NormalizedContract contract,
            boolean requireApiKey,
            String apiKeyHash,
            String apiKeyPreview
    ) {
        this.projectId = projectId;
        this.specVersionId = specVersionId;
        this.publicTokenHash = publicTokenHash;
        this.legacyPublicTokenHash = publicTokenHash;
        this.publicTokenPreview = publicTokenPreview;
        this.mode = mode;
        this.status = InstanceStatus.RUNNING;
        this.contract = contract;
        this.requireApiKey = requireApiKey;
        this.apiKeyHash = apiKeyHash;
        this.apiKeyPreview = apiKeyPreview;
        this.rateLimitEnabled = true;
        this.rateLimitRequests = 120;
        this.rateLimitWindowSeconds = 60;
        this.smartResponsesEnabled = true;
        this.smartSeedMode = "STABLE";
        this.profiles = defaultProfiles();
        activateProfileSettings(activeProfile());
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getSpecVersionId() {
        return specVersionId;
    }

    public void setSpecVersionId(String specVersionId) {
        this.specVersionId = specVersionId;
    }

    public String getPublicTokenHash() {
        return publicTokenHash;
    }

    public void setPublicTokenHash(String publicTokenHash) {
        this.publicTokenHash = publicTokenHash;
        this.legacyPublicTokenHash = publicTokenHash;
    }

    public String getLegacyPublicTokenHash() {
        return legacyPublicTokenHash;
    }

    public void setLegacyPublicTokenHash(String legacyPublicTokenHash) {
        this.legacyPublicTokenHash = legacyPublicTokenHash;
    }

    public String getPublicTokenPreview() {
        return publicTokenPreview;
    }

    public void setPublicTokenPreview(String publicTokenPreview) {
        this.publicTokenPreview = publicTokenPreview;
    }

    public boolean isRequireApiKey() {
        return requireApiKey;
    }

    public void setRequireApiKey(boolean requireApiKey) {
        this.requireApiKey = requireApiKey;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public String getApiKeyPreview() {
        return apiKeyPreview;
    }

    public void setApiKeyPreview(String apiKeyPreview) {
        this.apiKeyPreview = apiKeyPreview;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public String getMockApiKey() {
        return mockApiKey;
    }

    public void setMockApiKey(String mockApiKey) {
        this.mockApiKey = mockApiKey;
    }

    public InstanceMode getMode() {
        return mode;
    }

    public void setMode(InstanceMode mode) {
        this.mode = mode;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public void setStatus(InstanceStatus status) {
        this.status = status;
    }

    public NormalizedContract getContract() {
        return contract;
    }

    public void setContract(NormalizedContract contract) {
        this.contract = contract;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public int getRateLimitRequests() {
        return rateLimitRequests <= 0 ? 120 : rateLimitRequests;
    }

    public void setRateLimitRequests(int rateLimitRequests) {
        this.rateLimitRequests = rateLimitRequests <= 0 ? 120 : rateLimitRequests;
    }

    public int getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds <= 0 ? 60 : rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds <= 0 ? 60 : rateLimitWindowSeconds;
    }

    public boolean isSmartResponsesEnabled() {
        return smartResponsesEnabled == null || smartResponsesEnabled;
    }

    public void setSmartResponsesEnabled(boolean smartResponsesEnabled) {
        this.smartResponsesEnabled = smartResponsesEnabled;
    }

    public String getSmartSeedMode() {
        return smartSeedMode == null || smartSeedMode.isBlank() ? "STABLE" : smartSeedMode;
    }

    public void setSmartSeedMode(String smartSeedMode) {
        this.smartSeedMode = smartSeedMode == null || smartSeedMode.isBlank() ? "STABLE" : smartSeedMode;
    }

    public List<MockScenario> getScenarios() {
        if (scenarios == null) {
            scenarios = new ArrayList<>();
        }
        return scenarios;
    }

    public void setScenarios(List<MockScenario> scenarios) {
        this.scenarios = scenarios == null ? new ArrayList<>() : scenarios;
    }

    public List<ResponseRule> getResponseRules() {
        if (responseRules == null) {
            responseRules = new ArrayList<>();
        }
        return responseRules;
    }

    public void setResponseRules(List<ResponseRule> responseRules) {
        this.responseRules = responseRules == null ? new ArrayList<>() : responseRules;
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

    public String getActiveProfile() {
        return activeProfile == null || activeProfile.isBlank() ? "dev" : activeProfile;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile == null || activeProfile.isBlank() ? "dev" : activeProfile;
    }

    public String getActiveProfileName() {
        return activeProfile().getName();
    }

    public List<MockProfile> getProfiles() {
        if (profiles == null) {
            profiles = defaultProfiles();
        }
        return profiles;
    }

    public void setProfiles(List<MockProfile> profiles) {
        this.profiles = profiles == null ? new ArrayList<>() : profiles;
    }

    public MockProfile activeProfile() {
        return getProfiles().stream()
                .filter(profile -> getActiveProfile().equals(profile.getId()) || getActiveProfile().equalsIgnoreCase(profile.getName()))
                .findFirst()
                .orElseGet(() -> new MockProfile(getActiveProfile(), getActiveProfile(), isFaultProfileEnabled(), getFaultErrorRate(), getFaultStatusCode(), getLatencyMinMs(), getLatencyMaxMs()));
    }

    public void activateProfileSettings(MockProfile profile) {
        if (profile == null) {
            return;
        }
        setActiveProfile(profile.getId());
        setFaultProfileEnabled(profile.isFaultProfileEnabled());
        setFaultErrorRate(profile.getFaultErrorRate());
        setFaultStatusCode(profile.getFaultStatusCode());
        setLatencyMinMs(profile.getLatencyMinMs());
        setLatencyMaxMs(profile.getLatencyMaxMs());
    }

    public static List<MockProfile> defaultProfiles() {
        List<MockProfile> defaults = new ArrayList<>();
        defaults.add(new MockProfile("dev", "dev", false, 0, 500, 0, 0));
        defaults.add(new MockProfile("qa", "qa", true, 10, 500, 150, 600));
        defaults.add(new MockProfile("demo", "demo", false, 0, 500, 80, 220));
        return defaults;
    }

    public String getWorkerKey() {
        return workerKey;
    }

    public void setWorkerKey(String workerKey) {
        this.workerKey = workerKey;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
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
