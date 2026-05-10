package com.msaas.instance;

import com.msaas.spec.contract.NormalizedContract;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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
