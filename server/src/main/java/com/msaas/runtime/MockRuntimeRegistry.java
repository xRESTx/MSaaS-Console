package com.msaas.runtime;

import com.msaas.instance.InstanceStatus;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.security.SecretHashService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MockRuntimeRegistry {
    private final MockInstanceRepository instanceRepository;
    private final SecretHashService secretHashService;
    private final ConcurrentMap<String, RuntimeSlot> slotsByTokenHash = new ConcurrentHashMap<>();

    public MockRuntimeRegistry(MockInstanceRepository instanceRepository, SecretHashService secretHashService) {
        this.instanceRepository = instanceRepository;
        this.secretHashService = secretHashService;
    }

    @PostConstruct
    void loadRunningInstances() {
        instanceRepository.findByStatus(InstanceStatus.RUNNING).forEach(this::register);
    }

    public RuntimeSlot register(MockInstance instance) {
        RuntimeSlot slot = new RuntimeSlot(instance);
        if (instance.getPublicTokenHash() != null && !instance.getPublicTokenHash().isBlank()) {
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
        if (existing != null) {
            return Optional.of(existing);
        }
        return instanceRepository.findByPublicTokenHashAndStatus(tokenHash, InstanceStatus.RUNNING)
                .map(this::register);
    }

    public void reset(String publicTokenHash) {
        if (publicTokenHash == null || publicTokenHash.isBlank()) {
            return;
        }
        RuntimeSlot slot = slotsByTokenHash.get(publicTokenHash);
        if (slot != null) {
            slot.resetState();
        }
    }

    public Map<String, Object> snapshot(String publicTokenHash) {
        if (publicTokenHash == null || publicTokenHash.isBlank()) {
            return Map.of();
        }
        RuntimeSlot slot = slotsByTokenHash.get(publicTokenHash);
        return slot == null ? Map.of() : slot.snapshot();
    }

    public List<RuntimeSlotInfo> slots() {
        return slotsByTokenHash.values()
                .stream()
                .map(slot -> new RuntimeSlotInfo(
                        slot.getInstanceId(),
                        slot.getProjectId(),
                        slot.getPublicTokenPreview(),
                        slot.getMode().name(),
                        slot.getLoadedAt(),
                        slot.snapshot().size()
                ))
                .toList();
    }

    public record RuntimeSlotInfo(
            String instanceId,
            String projectId,
            String tokenPreview,
            String mode,
            java.time.Instant loadedAt,
            int stateCollectionCount
    ) {
    }
}
