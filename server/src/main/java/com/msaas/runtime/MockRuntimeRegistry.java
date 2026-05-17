package com.msaas.runtime;

import com.msaas.common.ApiException;
import com.msaas.instance.InstanceStatus;
import com.msaas.instance.InstanceMode;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.config.AppProperties;
import com.msaas.security.SecretHashService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MockRuntimeRegistry {
    private final MockInstanceRepository instanceRepository;
    private final SecretHashService secretHashService;
    private final AppProperties properties;
    private final ConcurrentMap<String, RuntimeSlot> slotsByTokenHash = new ConcurrentHashMap<>();

    public MockRuntimeRegistry(MockInstanceRepository instanceRepository, SecretHashService secretHashService, AppProperties properties) {
        this.instanceRepository = instanceRepository;
        this.secretHashService = secretHashService;
        this.properties = properties;
    }

    public synchronized RuntimeSlot register(MockInstance instance) {
        RuntimeSlot slot = new RuntimeSlot(instance);
        if (instance.getPublicTokenHash() != null && !instance.getPublicTokenHash().isBlank()) {
            if (!slotsByTokenHash.containsKey(instance.getPublicTokenHash())) {
                ensureCapacityForNewSlot();
            }
            slotsByTokenHash.put(instance.getPublicTokenHash(), slot);
        }
        return slot;
    }

    public void unregister(String publicTokenHash) {
        if (publicTokenHash == null || publicTokenHash.isBlank()) {
            return;
        }
        slotsByTokenHash.remove(publicTokenHash);
    }

    public Optional<RuntimeSlot> findByToken(String publicToken) {
        String tokenHash = secretHashService.hash(publicToken);
        RuntimeSlot existing = slotsByTokenHash.get(tokenHash);
        if (existing != null && !stale(existing)) {
            existing.touch();
            return Optional.of(existing);
        }
        if (existing != null) {
            slotsByTokenHash.remove(tokenHash, existing);
        }
        return instanceRepository.findByPublicTokenHashAndStatus(tokenHash, InstanceStatus.RUNNING)
                .map(this::register);
    }

    private boolean stale(RuntimeSlot slot) {
        return instanceRepository.findById(slot.getInstanceId())
                .map(instance -> instance.getUpdatedAt() != null
                        && (slot.getInstanceUpdatedAt() == null || instance.getUpdatedAt().isAfter(slot.getInstanceUpdatedAt())))
                .orElse(true);
    }

    public void reset(String publicTokenHash) {
        if (publicTokenHash == null || publicTokenHash.isBlank()) {
            return;
        }
        RuntimeSlot slot = slotsByTokenHash.get(publicTokenHash);
        if (slot != null) {
            slot.resetState();
            slot.touch();
        }
    }

    public Map<String, Object> snapshot(String publicTokenHash) {
        if (publicTokenHash == null || publicTokenHash.isBlank()) {
            return Map.of();
        }
        RuntimeSlot slot = slotsByTokenHash.get(publicTokenHash);
        if (slot == null) {
            return Map.of();
        }
        slot.touch();
        return slot.snapshot();
    }

    public List<RuntimeSlotInfo> slots() {
        return slotsByTokenHash.values()
                .stream()
                .map(slot -> new RuntimeSlotInfo(
                        slot.getInstanceId(),
                        slot.getProjectId(),
                        slot.getPublicTokenPreview(),
                        slot.getWorkerKey(),
                        slot.getMode().name(),
                        slot.getLoadedAt(),
                        slot.getLastAccessedAt(),
                        slot.getActiveRequests(),
                        slot.snapshot().size()
                ))
                .toList();
    }

    public int slotCount() {
        return slotsByTokenHash.size();
    }

    @Scheduled(fixedDelayString = "${app.runtime.slot-cleanup-delay-ms:60000}")
    void cleanup() {
        cleanup(Instant.now());
    }

    synchronized void cleanup(Instant now) {
        long ttlSeconds = properties.getRuntime().getSlotIdleTtlSeconds();
        slotsByTokenHash.entrySet().removeIf(entry -> canEvict(entry.getValue()) && entry.getValue().isIdleExpired(now, ttlSeconds));
        evictUntilWithinLimit();
    }

    private void ensureCapacityForNewSlot() {
        cleanup(Instant.now());
        evictUntilBelowLimit();
        if (slotsByTokenHash.size() >= properties.getRuntime().getMaxSlotsPerWorker()) {
            throw ApiException.serviceUnavailable("Runtime slot capacity reached");
        }
    }

    private void evictUntilWithinLimit() {
        int maxSlots = properties.getRuntime().getMaxSlotsPerWorker();
        while (slotsByTokenHash.size() > maxSlots) {
            if (!evictLeastRecentlyUsed()) {
                return;
            }
        }
    }

    private void evictUntilBelowLimit() {
        int maxSlots = properties.getRuntime().getMaxSlotsPerWorker();
        while (slotsByTokenHash.size() >= maxSlots) {
            if (!evictLeastRecentlyUsed()) {
                return;
            }
        }
    }

    private boolean evictLeastRecentlyUsed() {
        Optional<Map.Entry<String, RuntimeSlot>> lru = slotsByTokenHash.entrySet()
                .stream()
                .filter(entry -> canEvict(entry.getValue()))
                .min(Comparator.comparing(entry -> entry.getValue().getLastAccessedAt()));
        if (lru.isEmpty()) {
            return false;
        }
        slotsByTokenHash.remove(lru.get().getKey(), lru.get().getValue());
        return true;
    }

    private boolean canEvict(RuntimeSlot slot) {
        if (!slot.isEvictable()) {
            return false;
        }
        return !properties.getRuntime().isEmbedded() || slot.getMode() != InstanceMode.STATEFUL;
    }

    public record RuntimeSlotInfo(
            String instanceId,
            String projectId,
            String tokenPreview,
            String workerKey,
            String mode,
            java.time.Instant loadedAt,
            java.time.Instant lastAccessedAt,
            int activeRequests,
            int stateCollectionCount
    ) {
    }
}
