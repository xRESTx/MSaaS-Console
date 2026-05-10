package com.msaas.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<AppUser> findByEmailContainingIgnoreCaseOrderByCreatedAtDesc(String email);

    List<AppUser> findByEmailContainingIgnoreCaseOrUsernameContainingIgnoreCaseOrderByCreatedAtDesc(String email, String username);
}
