package com.msaas.runtime;

import com.msaas.instance.InstanceMode;
import com.msaas.instance.MockInstance;
import com.msaas.spec.contract.NormalizedContract;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockRateLimiterTest {

    @Test
    void rejectsRequestsOverInstanceLimit() {
        MockInstance instance = new MockInstance("project", "spec", "hash", "preview", InstanceMode.STATELESS, new NormalizedContract("Demo", "1", List.of()), false, null, null);
        instance.setRateLimitEnabled(true);
        instance.setRateLimitRequests(2);
        instance.setRateLimitWindowSeconds(60);
        RuntimeSlot slot = new RuntimeSlot(instance);
        MockRateLimiter limiter = new MockRateLimiter();

        assertThat(limiter.tryAcquire(slot, "token", "127.0.0.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire(slot, "token", "127.0.0.1").allowed()).isTrue();

        MockRateLimiter.RateLimitResult third = limiter.tryAcquire(slot, "token", "127.0.0.1");
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isPositive();
    }
}
