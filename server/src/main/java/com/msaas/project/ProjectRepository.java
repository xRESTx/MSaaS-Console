package com.msaas.project;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    List<Project> findByMembersUserId(String userId);

    Optional<Project> findByIdAndOwnerId(String id, String ownerId);

    @Query("{ '$or': [ { 'ownerId': ?0 }, { 'members.userId': ?0 } ] }")
    List<Project> findAccessibleByUserId(String userId, Sort sort);

    long countByOwnerId(String ownerId);

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findByNameContainingIgnoreCaseOrderByCreatedAtDesc(String name);
}
