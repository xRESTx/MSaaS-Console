package com.msaas.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AuditEventRepository extends MongoRepository<AuditEvent, String> {
    List<AuditEvent> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<AuditEvent> findByActorIdOrderByCreatedAtDesc(String actorId, Pageable pageable);

    void deleteByProjectId(String projectId);
}
