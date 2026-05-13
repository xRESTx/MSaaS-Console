package com.msaas.runtime;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockRateLimiter {
    private final Clock clock;
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public MockRateLimiter() {
        this(Clock.systemUTC());
    }

    MockRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public RateLimitResult tryAcquire(RuntimeSlot slot, String publicToken, String clientIp) {
        if (!slot.isRateLimitEnabled()) {
            return RateLimitResult.allow();
        }
        String key = publicToken + "|" + clientIp;
        Instant now = Instant.now(clock);
        Instant cutoff = now.minusSeconds(slot.getRateLimitWindowSeconds());
        Deque<Instant> bucket = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(cutoff)) {
                bucket.removeFirst();
            }
            if (bucket.size() >= slot.getRateLimitRequests()) {
                long retryAfter = Math.max(1, slot.getRateLimitWindowSeconds() - java.time.Duration.between(bucket.peekFirst(), now).toSeconds());
                return RateLimitResult.rejected(retryAfter);
            }
            bucket.addLast(now);
            return RateLimitResult.allow();
        }
    }

    public record RateLimitResult(boolean allowed, long retryAfterSeconds) {
        static RateLimitResult allow() {
            return new RateLimitResult(true, 0);
        }

        static RateLimitResult rejected(long retryAfterSeconds) {
            return new RateLimitResult(false, retryAfterSeconds);
        }
    }
}
