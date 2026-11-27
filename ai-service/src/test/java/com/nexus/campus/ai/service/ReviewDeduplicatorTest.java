package com.nexus.campus.ai.service;

import com.nexus.campus.ai.api.dto.ReviewRequest;
import com.nexus.campus.ai.api.dto.ReviewResponse;
import com.nexus.campus.ai.config.AiServiceProperties;
import com.nexus.campus.ai.model.AiServiceException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewDeduplicatorTest {

    private final AiServiceProperties properties = new AiServiceProperties();
    private ReviewDeduplicator dedup;

    @BeforeEach
    void setUp() {
        properties.getReview().setCacheMaxEntries(10);
        dedup = new ReviewDeduplicator(properties, new SimpleMeterRegistry());
    }

    @Test
    void aRepeatInsideTheTtlSkipsTheSupplier() {
        AtomicInteger calls = new AtomicInteger();

        ReviewResponse first = dedup.run("k", () -> {
            calls.incrementAndGet();
            return valid();
        });
        ReviewResponse second = dedup.run("k", () -> {
            calls.incrementAndGet();
            return valid();
        });

        assertEquals(1, calls.get());
        assertEquals(first.score(), second.score());
    }

    @Test
    void concurrentDuplicatesCoalesceOnOneCompletion() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Runnable task = () -> dedup.run("k", () -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return valid();
            });
            pool.submit(task);
            entered.await(2, TimeUnit.SECONDS);
            pool.submit(task);
            Thread.sleep(100L);
            release.countDown();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }

        assertEquals(1, calls.get());
    }

    @Test
    void errorsAreNotCached() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(AiServiceException.class, () -> dedup.run("k", () -> {
            calls.incrementAndGet();
            throw new AiServiceException("model_timeout", true, 504, "slow");
        }));
        dedup.run("k", () -> {
            calls.incrementAndGet();
            return valid();
        });

        assertEquals(2, calls.get());
    }

    @Test
    void anInvalidResponseIsNotStored() {
        AtomicInteger calls = new AtomicInteger();
        ReviewResponse invalid = new ReviewResponse(
                8, "low", "", "", "", true, "{}", "m", new ReviewResponse.Usage(1, 1), 1, false);

        dedup.run("k", () -> {
            calls.incrementAndGet();
            return invalid;
        });
        dedup.run("k", () -> {
            calls.incrementAndGet();
            return valid();
        });

        assertEquals(2, calls.get());
        assertEquals(1, dedup.cachedEntries());
    }

    @Test
    void theKeySeparatesContentAndModel() {
        ReviewRequest base = request("one");
        ReviewRequest other = request("two");

        assertNotEquals(
                ReviewDeduplicator.key(base, "m1", "v1"),
                ReviewDeduplicator.key(other, "m1", "v1"));
        assertNotEquals(
                ReviewDeduplicator.key(base, "m1", "v1"),
                ReviewDeduplicator.key(base, "m2", "v1"));
        assertNotEquals(
                ReviewDeduplicator.key(base, "m1", "v1"),
                ReviewDeduplicator.key(base, "m1", "v2"));
    }

    @Test
    void evictionKeepsTheCacheBounded() {
        properties.getReview().setCacheMaxEntries(2);
        ReviewDeduplicator bounded = new ReviewDeduplicator(properties, new SimpleMeterRegistry());

        bounded.run("a", this::valid);
        bounded.run("b", this::valid);
        bounded.run("c", this::valid);

        assertEquals(2, bounded.cachedEntries());
    }

    private ReviewRequest request(String content) {
        return new ReviewRequest("req", "trace", 1L, "code", "t", content,
                List.of(content), new ReviewRequest.Budget(12000, 60000));
    }

    private ReviewResponse valid() {
        return new ReviewResponse(8, "low",
                "Clean structure and readable naming throughout the implementation",
                "No injection or unsafe cast was found in this code",
                "Extract the division logic into a helper and add unit tests",
                true, "{}", "m", new ReviewResponse.Usage(10, 5), 10, false);
    }
}
