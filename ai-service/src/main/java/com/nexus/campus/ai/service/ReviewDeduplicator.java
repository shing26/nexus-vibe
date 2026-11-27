package com.nexus.campus.ai.service;

import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.config.AiServiceProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Content-addressed deduplication for review calls.
 *
 * <p>The application mints a new trace id per dispatch, so {@code requestId} cannot be
 * the key: a lease re-dispatch would never hit. The content key can. Concurrent
 * duplicates coalesce on one in-flight completion; a valid response is cached for a
 * bounded TTL. Errors and invalid verdicts are never cached, because a later attempt may
 * legitimately recover.</p>
 */
@Component
public class ReviewDeduplicator {

    private final long ttlMillis;
    private final int maxEntries;
    private final Map<String, Entry> cache;
    private final ConcurrentHashMap<String, CompletableFuture<ReviewResponse>> inFlight =
            new ConcurrentHashMap<>();
    private final Counter hits;
    private final Counter coalesced;
    private final Counter misses;

    public ReviewDeduplicator(AiServiceProperties properties, MeterRegistry meters) {
        this.ttlMillis = properties.getReview().getCacheTtl().toMillis();
        this.maxEntries = properties.getReview().getCacheMaxEntries();
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxEntries;
            }
        };
        hits = Counter.builder("ai.review.dedup").tag("outcome", "hit").register(meters);
        coalesced = Counter.builder("ai.review.dedup").tag("outcome", "coalesced").register(meters);
        misses = Counter.builder("ai.review.dedup").tag("outcome", "miss").register(meters);
    }

    public ReviewResponse run(String key, Supplier<ReviewResponse> supplier) {
        ReviewResponse cached = lookup(key);
        if (cached != null) {
            hits.increment();
            return cached;
        }

        CompletableFuture<ReviewResponse> mine = new CompletableFuture<>();
        CompletableFuture<ReviewResponse> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            coalesced.increment();
            return join(existing);
        }

        misses.increment();
        try {
            ReviewResponse response = supplier.get();
            if (cacheable(response)) {
                store(key, response);
            }
            mine.complete(response);
            return response;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /** SHA-256 over length-prefixed parts, so {@code ab|c} and {@code a|bc} differ. */
    public static String key(ReviewRequest request, String model, String promptVersion) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, request.kind());
        append(canonical, request.title());
        append(canonical, request.content());
        if (request.snippets() != null) {
            append(canonical, Integer.toString(request.snippets().size()));
            request.snippets().forEach(snippet -> append(canonical, snippet));
        } else {
            append(canonical, "no-snippets");
        }
        append(canonical, model);
        append(canonical, promptVersion);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("cannot hash the review key", e);
        }
    }

    int cachedEntries() {
        synchronized (cache) {
            return cache.size();
        }
    }

    private static void append(StringBuilder target, String part) {
        String value = part == null ? "\0null" : part;
        target.append(value.length()).append(':').append(value).append('|');
    }

    private ReviewResponse lookup(String key) {
        if (ttlMillis <= 0) {
            return null;
        }
        synchronized (cache) {
            Entry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (System.currentTimeMillis() - entry.storedAt > ttlMillis) {
                cache.remove(key);
                return null;
            }
            return entry.response;
        }
    }

    private void store(String key, ReviewResponse response) {
        if (ttlMillis <= 0 || maxEntries <= 0) {
            return;
        }
        synchronized (cache) {
            cache.put(key, new Entry(response, System.currentTimeMillis()));
        }
    }

    private static boolean cacheable(ReviewResponse response) {
        return response != null && ReviewValidator.isValid(
                response.score(), response.severity(), response.quality(), response.suggestions());
    }

    private static ReviewResponse join(CompletableFuture<ReviewResponse> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    private record Entry(ReviewResponse response, long storedAt) {
    }
}
