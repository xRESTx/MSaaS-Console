package com.msaas.log;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface RequestLogRepository extends MongoRepository<RequestLog, String> {
    List<RequestLog> findByInstanceIdOrderByReceivedAtDesc(String instanceId, Pageable pageable);

    void deleteByInstanceId(String instanceId);

    void deleteByProjectId(String projectId);
}
