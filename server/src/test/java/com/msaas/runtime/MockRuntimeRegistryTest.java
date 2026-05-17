package com.msaas.runtime;

import com.msaas.common.ApiException;
import com.msaas.config.AppProperties;
import com.msaas.instance.InstanceMode;
import com.msaas.instance.InstanceStatus;
import com.msaas.instance.MockInstance;
import com.msaas.instance.MockInstanceRepository;
import com.msaas.security.SecretHashService;
import com.msaas.spec.contract.NormalizedContract;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockRuntimeRegistryTest {
    private final SecretHashService secretHashService = new SecretHashService();

    @Test
    void evictsIdleSlotsAfterTtl() {
        MockInstanceRepository repository = mock(MockInstanceRepository.class);
        AppProperties properties = runtimeProperties(RuntimeRole.RUNTIME, 10, 30);
        MockRuntimeRegistry registry = new MockRuntimeRegistry(repository, secretHashService, properties);
        RuntimeSlot slot = registry.register(instance("one", "token-one", InstanceMode.STATELESS));
        Instant now = Instant.now();
        slot.setLastAccessedAt(now.minusSeconds(31));

        registry.cleanup(now);

        assertThat(registry.slotCount()).isZero();
    }

    @Test
    void keepsInFlightSlotsDuringCleanup() {
        MockInstanceRepository repository = mock(MockInstanceRepository.class);
        AppProperties properties = runtimeProperties(RuntimeRole.RUNTIME, 10, 30);
        MockRuntimeRegistry registry = new MockRuntimeRegistry(repository, secretHashService, properties);
        RuntimeSlot slot = registry.register(instance("one", "token-one", InstanceMode.STATELESS));
        Instant now = Instant.now();
        slot.setLastAccessedAt(now.minusSeconds(300));
        slot.beginRequest();

        registry.cleanup(now);

        assertThat(registry.slotCount()).isEqualTo(1);
        slot.endRequest();
    }

    @Test
    void evictsLeastRecentlyUsedSlotWhenCapacityIsReached() {
        MockInstanceRepository repository = mock(MockInstanceRepository.class);
        AppProperties properties = runtimeProperties(RuntimeRole.RUNTIME, 2, 1800);
        MockRuntimeRegistry registry = new MockRuntimeRegistry(repository, secretHashService, properties);
        Instant now = Instant.now();
        RuntimeSlot oldSlot = registry.register(instance("one", "token-one", InstanceMode.STATELESS));
        RuntimeSlot recentSlot = registry.register(instance("two", "token-two", InstanceMode.STATELESS));
        oldSlot.setLastAccessedAt(now.minusSeconds(20));
        recentSlot.setLastAccessedAt(now.minusSeconds(5));

        registry.register(instance("three", "token-three", InstanceMode.STATELESS));

        assertThat(registry.slotCount()).isEqualTo(2);
        assertThat(registry.slots()).extracting(MockRuntimeRegistry.RuntimeSlotInfo::instanceId)
                .containsExactlyInAnyOrder("two", "three")
                .doesNotContain("one");
    }

    @Test
    void refusesCapacityWhenEmbeddedStatefulSlotsCannotBeEvicted() {
        MockInstanceRepository repository = mock(MockInstanceRepository.class);
        AppProperties properties = runtimeProperties(RuntimeRole.EMBEDDED, 1, 1);
        MockRuntimeRegistry registry = new MockRuntimeRegistry(repository, secretHashService, properties);
        RuntimeSlot slot = registry.register(instance("one", "token-one", InstanceMode.STATEFUL));
        slot.setLastAccessedAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> registry.register(instance("two", "token-two", InstanceMode.STATEFUL)))
                .isInstanceOf(ApiException.class)
                .hasMessage("Runtime slot capacity reached");
        assertThat(registry.slotCount()).isEqualTo(1);
    }

    @Test
    void reloadsEvictedSlotFromRepository() {
        MockInstanceRepository repository = mock(MockInstanceRepository.class);
        AppProperties properties = runtimeProperties(RuntimeRole.RUNTIME, 1, 1800);
        MockRuntimeRegistry registry = new MockRuntimeRegistry(repository, secretHashService, properties);
        MockInstance first = instance("one", "token-one", InstanceMode.STATELESS);
        registry.register(first);
        registry.register(instance("two", "token-two", InstanceMode.STATELESS));
        when(repository.findByPublicTokenHashAndStatus(first.getPublicTokenHash(), InstanceStatus.RUNNING)).thenReturn(Optional.of(first));

        Optional<RuntimeSlot> reloaded = registry.findByToken("token-one");

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getInstanceId()).isEqualTo("one");
        assertThat(registry.slotCount()).isEqualTo(1);
    }

    private AppProperties runtimeProperties(RuntimeRole role, int maxSlots, long ttlSeconds) {
        AppProperties properties = new AppProperties();
        properties.getRuntime().setRole(role);
        properties.getRuntime().setMaxSlotsPerWorker(maxSlots);
        properties.getRuntime().setSlotIdleTtlSeconds(ttlSeconds);
        return properties;
    }

    private MockInstance instance(String id, String token, InstanceMode mode) {
        MockInstance instance = new MockInstance(
                "project",
                "spec",
                secretHashService.hash(token),
                secretHashService.preview(token),
                mode,
                new NormalizedContract("Demo", "1", List.of()),
                false,
                null,
                null
        );
        instance.setId(id);
        return instance;
    }
}
