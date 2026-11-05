package in.ruhmans.alliancetrading.common.web;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import org.springframework.stereotype.Component;

@Component
public class IpRateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String key, long capacity, Duration period) {
        Bucket bucket = buckets.computeIfAbsent(key,
                k -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(capacity)
                                .refillGreedy(capacity, period)
                                .build())
                        .build());
        return bucket.tryConsume(1);
    }
}
