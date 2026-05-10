package com.msaas.runtime;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RuntimeWorkerRepository extends MongoRepository<RuntimeWorker, String> {
    Optional<RuntimeWorker> findByWorkerKey(String workerKey);
}
