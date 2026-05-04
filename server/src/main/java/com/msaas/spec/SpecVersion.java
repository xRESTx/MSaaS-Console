package com.msaas.spec;

import com.msaas.spec.contract.NormalizedContract;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("spec_versions")
public class SpecVersion {
    @Id
    private String id;

    @Indexed
    private String projectId;

    private int versionNumber;
    private String name;
    private String source;
    private ValidationStatus status;
    private List<String> validationErrors = new ArrayList<>();
    private NormalizedContract normalizedContract;
    private Instant createdAt;

    public SpecVersion() {
    }

    public SpecVersion(String projectId, int versionNumber, String name, String source, ValidationStatus status) {
        this.projectId = projectId;
        this.versionNumber = versionNumber;
        this.name = name;
        this.source = source;
        this.status = status;
        this.createdAt = Instant.now();
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

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public void setStatus(ValidationStatus status) {
        this.status = status;
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
        this.validationErrors = validationErrors;
    }

    public NormalizedContract getNormalizedContract() {
        return normalizedContract;
    }

    public void setNormalizedContract(NormalizedContract normalizedContract) {
        this.normalizedContract = normalizedContract;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
