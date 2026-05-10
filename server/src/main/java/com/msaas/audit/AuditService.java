package com.msaas.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuditService {
    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(String projectId, String actorId, String action, String targetType, String targetId, String message) {
        record(projectId, actorId, action, targetType, targetId, message, Map.of());
    }

    public void record(String projectId, String actorId, String action, String targetType, String targetId, String message, Map<String, Object> metadata) {
        repository.save(new AuditEvent(projectId, actorId, action, targetType, targetId, message, metadata));
    }

    public List<AuditEvent> list(String projectId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, boundedLimit));
    }

    public void deleteProjectEvents(String projectId) {
        repository.deleteByProjectId(projectId);
    }
}
