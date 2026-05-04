package com.msaas.project;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends MongoRepository<Project, String> {
    List<Project> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    Optional<Project> findByIdAndOwnerId(String id, String ownerId);
}
