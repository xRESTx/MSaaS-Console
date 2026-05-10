package com.msaas.spec;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SpecVersionRepository extends MongoRepository<SpecVersion, String> {
    List<SpecVersion> findByProjectIdOrderByVersionNumberDesc(String projectId);

    long countByProjectId(String projectId);

    long countByStatus(ValidationStatus status);

    Optional<SpecVersion> findByIdAndProjectId(String id, String projectId);

    void deleteByProjectId(String projectId);
}
