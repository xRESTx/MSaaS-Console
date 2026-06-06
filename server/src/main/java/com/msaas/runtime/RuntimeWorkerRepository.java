package com.msaas.runtime;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Optional;

public interface RuntimeWorkerRepository extends MongoRepository<RuntimeWorker, String> {
    Optional<RuntimeWorker> findByWorkerKey(String workerKey);

    long deleteByStatusAndLastHeartbeatAtBefore(RuntimeWorkerStatus status, Instant lastHeartbeatAt);

    long deleteByStatusAndLastHeartbeatAtIsNull(RuntimeWorkerStatus status);
}
