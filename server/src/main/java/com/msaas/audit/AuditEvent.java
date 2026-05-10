package com.msaas.audit;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Document("audit_events")
public class AuditEvent {
    @Id
    private String id;

    @Indexed
    private String projectId;

    @Indexed
    private String actorId;

    private String action;
    private String targetType;
    private String targetId;
    private String message;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Instant createdAt;

    public AuditEvent() {
    }

    public AuditEvent(String projectId, String actorId, String action, String targetType, String targetId, String message, Map<String, Object> metadata) {
        this.projectId = projectId;
        this.actorId = actorId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.message = message;
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
