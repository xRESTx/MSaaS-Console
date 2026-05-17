package com.msaas.instance;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface MockInstanceRepository extends MongoRepository<MockInstance, String> {
    List<MockInstance> findByProjectIdOrderByCreatedAtDesc(String projectId);

    Optional<MockInstance> findByIdAndProjectId(String id, String projectId);

    Optional<MockInstance> findByPublicTokenHashAndStatus(String publicTokenHash, InstanceStatus status);

    List<MockInstance> findByStatus(InstanceStatus status);

    long countByWorkerKeyAndStatus(String workerKey, InstanceStatus status);

    List<MockInstance> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<MockInstance> findBySpecVersionIdOrderByCreatedAtDesc(String specVersionId);

    void deleteByProjectId(String projectId);

    void deleteBySpecVersionId(String specVersionId);
}
