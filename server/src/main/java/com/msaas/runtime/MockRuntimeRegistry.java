package com.msaas.runtime;

import com.msaas.instance.InstanceStatus;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class MockRuntimeRegistry {
    private final MockInstanceRepository instanceRepository;
    private final ConcurrentMap<String, RuntimeSlot> slotsByToken = new ConcurrentHashMap<>();

    public MockRuntimeRegistry(MockInstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    @PostConstruct
    void loadRunningInstances() {
        instanceRepository.findByStatus(InstanceStatus.RUNNING).forEach(this::register);
    }

    public RuntimeSlot register(MockInstance instance) {
        RuntimeSlot slot = new RuntimeSlot(instance);
        slotsByToken.put(instance.getPublicToken(), slot);
        return slot;
    }

    public void unregister(String publicToken) {
        slotsByToken.remove(publicToken);
    }

    public Optional<RuntimeSlot> findByToken(String publicToken) {
        RuntimeSlot existing = slotsByToken.get(publicToken);
        if (existing != null) {
            return Optional.of(existing);
        }
        return instanceRepository.findByPublicTokenAndStatus(publicToken, InstanceStatus.RUNNING)
                .map(this::register);
    }

    public void reset(String publicToken) {
        RuntimeSlot slot = slotsByToken.get(publicToken);
        if (slot != null) {
            slot.resetState();
        }
    }
}
