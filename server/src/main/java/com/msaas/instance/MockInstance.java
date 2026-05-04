package com.msaas.instance;

import com.msaas.spec.contract.NormalizedContract;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
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

    @Indexed(unique = true)
    private String publicToken;

    private String publicUrl;
    private InstanceMode mode;
    private InstanceStatus status;
    private NormalizedContract contract;
    private Instant createdAt;
    private Instant updatedAt;

    public MockInstance() {
    }

    public MockInstance(String projectId, String specVersionId, String publicToken, String publicUrl, InstanceMode mode, NormalizedContract contract) {
        this.projectId = projectId;
        this.specVersionId = specVersionId;
        this.publicToken = publicToken;
        this.publicUrl = publicUrl;
        this.mode = mode;
        this.status = InstanceStatus.RUNNING;
        this.contract = contract;
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

    public String getPublicToken() {
        return publicToken;
    }

    public void setPublicToken(String publicToken) {
        this.publicToken = publicToken;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
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
