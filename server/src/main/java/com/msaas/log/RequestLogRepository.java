package com.msaas.log;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface RequestLogRepository extends MongoRepository<RequestLog, String> {
    List<RequestLog> findByInstanceIdOrderByReceivedAtDesc(String instanceId, Pageable pageable);

    Page<RequestLog> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<RequestLog> findByProjectIdInOrderByReceivedAtDesc(Collection<String> projectIds, Pageable pageable);

    long countByResponseStatusGreaterThanEqual(int responseStatus);

    long countByMatchedFalse();

    void deleteByInstanceId(String instanceId);

    void deleteByProjectId(String projectId);
}
