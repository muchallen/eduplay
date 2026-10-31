package com.eduplay.backend.security;

import com.eduplay.backend.common.exception.RateLimitExceededException;
import com.eduplay.backend.config.EduplayProperties;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * In-memory token-bucket rate limiter for abuse-prone endpoints (login, registration, password
 * reset). State is per application instance; when running several replicas, also rate limit at
 * the reverse proxy or swap this class for a shared store such as Redis.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final EduplayProperties properties;
    private final Clock clock;

    /**
     * Consumes one token from the bucket identified by {@code name} and {@code key}.
     *
     * @throws RateLimitExceededException when the bucket is empty
     */
    public void consume(String name, String key, EduplayProperties.Rule rule) {
        if (!properties.rateLimit().enabled()) {
            return;
        }
        long now = clock.millis();
        double capacity = rule.capacity();
        double refillPerMs = capacity / rule.window().toMillis();
        Bucket bucket = buckets.compute(name + ':' + key, (k, existing) -> {
            Bucket current = existing == null ? new Bucket(capacity, now, false) : existing.refill(now, capacity, refillPerMs);
            return current.tokens >= 1
                    ? new Bucket(current.tokens - 1, now, false)
                    : new Bucket(current.tokens, now, true);
        });
        if (bucket.rejected) {
            long retryAfterSeconds = Math.max(1, (long) Math.ceil((1 - bucket.tokens) / refillPerMs / 1000));
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    /** Drops buckets that have refilled completely so memory does not grow unbounded. */
    @Scheduled(fixedDelay = 300_000)
    public void evictIdleBuckets() {
        long cutoff = clock.millis() - 24 * 60 * 60 * 1000L;
        buckets.entrySet().removeIf(entry -> entry.getValue().updatedAtMs < cutoff);
    }

    void reset() {
        buckets.clear();
    }

    private record Bucket(double tokens, long updatedAtMs, boolean rejected) {

        Bucket refill(long now, double capacity, double refillPerMs) {
            double refilled = Math.min(capacity, tokens + (now - updatedAtMs) * refillPerMs);
            return new Bucket(refilled, now, false);
        }
    }
}
