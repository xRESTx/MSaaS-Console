package com.msaas.runtime;

import com.msaas.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockRateLimiter {
    private static final String RATE_PREFIX = "msaas:rate:";
    private static final String RATE_SCRIPT = """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, tonumber(ARGV[1]) - tonumber(ARGV[2]))
            local count = redis.call('ZCARD', KEYS[1])
            if count >= tonumber(ARGV[3]) then
              local first = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
              local retry = 1
              if first[2] then
                retry = math.ceil((tonumber(first[2]) + tonumber(ARGV[2]) - tonumber(ARGV[1])) / 1000)
              end
              if retry < 1 then retry = 1 end
              return 0 - retry
            end
            redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
            redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]) + 1000)
            return 0
            """;

    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;
    private final DefaultRedisScript<Long> redisScript;
    private final Map<String, Deque<Instant>> buckets = new ConcurrentHashMap<>();

    public MockRateLimiter() {
        this(Clock.systemUTC(), null, new AppProperties());
    }

    public MockRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplate, AppProperties properties) {
        this(Clock.systemUTC(), redisTemplate.getIfAvailable(), properties);
    }

    MockRateLimiter(Clock clock) {
        this(clock, null, new AppProperties());
    }

    MockRateLimiter(Clock clock, StringRedisTemplate redisTemplate, AppProperties properties) {
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.redisScript = new DefaultRedisScript<>(RATE_SCRIPT, Long.class);
    }

    public RateLimitResult tryAcquire(RuntimeSlot slot, String publicToken, String clientIp) {
        if (!slot.isRateLimitEnabled()) {
            return RateLimitResult.allow();
        }
        if (useRedis()) {
            return tryAcquireRedis(slot, clientIp);
        }
        return tryAcquireMemory(slot, publicToken, clientIp);
    }

    private RateLimitResult tryAcquireRedis(RuntimeSlot slot, String clientIp) {
        String key = RATE_PREFIX + slot.getPublicTokenHash() + ":" + clientIp;
        long now = clock.millis();
        long windowMs = slot.getRateLimitWindowSeconds() * 1000L;
        String member = now + ":" + UUID.randomUUID();
        try {
            Long result = redisTemplate.execute(redisScript, List.of(key), String.valueOf(now), String.valueOf(windowMs), String.valueOf(slot.getRateLimitRequests()), member);
            if (result != null && result < 0) {
                return RateLimitResult.rejected(Math.abs(result));
            }
            return RateLimitResult.allow();
        } catch (RedisSystemException ex) {
            if (!properties.getRuntime().isEmbedded()) {
                throw ex;
            }
            return tryAcquireMemory(slot, slot.getPublicTokenHash(), clientIp);
        }
    }

    private RateLimitResult tryAcquireMemory(RuntimeSlot slot, String publicToken, String clientIp) {
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

    private boolean useRedis() {
        return redisTemplate != null && !properties.getRuntime().isEmbedded();
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
