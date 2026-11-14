package com.supportai.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaConcurrencyLimiterTest {

    @Test
    @DisplayName("Rejects zero or negative maximum concurrent requests")
    void rejectsZeroOrNegativeLimit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaConcurrencyLimiter(0)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new OllamaConcurrencyLimiter(-5)
        );
    }

    @Test
    @DisplayName("Initializes with configured permit count")
    void initializesWithConfiguredPermitCount() {
        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(3);
        assertEquals(3, limiter.getMaxConcurrentRequests());
        assertEquals(3, limiter.availablePermits());
    }

    @Test
    @DisplayName("tryAcquire succeeds up to limit and returns false when exhausted")
    void tryAcquireSucceedsUpToLimitAndFailsWhenExhausted() {
        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(2);

        assertTrue(limiter.tryAcquire());
        assertEquals(1, limiter.availablePermits());

        assertTrue(limiter.tryAcquire());
        assertEquals(0, limiter.availablePermits());

        assertFalse(limiter.tryAcquire());
        assertEquals(0, limiter.availablePermits());

        limiter.release();
        assertEquals(1, limiter.availablePermits());

        assertTrue(limiter.tryAcquire());
        assertEquals(0, limiter.availablePermits());

        limiter.release();
        limiter.release();
        assertEquals(2, limiter.availablePermits());
    }

    @Test
    @DisplayName("Hardening: rejects release when all permits are already available")
    void rejectsOverReleaseWhenAllPermitsAvailable() {
        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(2);
        assertEquals(2, limiter.availablePermits());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                limiter::release
        );
        assertEquals("Cannot release permit: all permits are already available", ex.getMessage());
        assertEquals(2, limiter.availablePermits());
    }

    @Test
    @DisplayName("Thread-safety: multiple concurrent threads respect the permit limit")
    void threadSafetyUnderConcurrentAccess() throws InterruptedException {
        int limit = 3;
        int threadCount = 20;
        OllamaConcurrencyLimiter limiter = new OllamaConcurrencyLimiter(limit);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        AtomicInteger successfulAcquisitions = new AtomicInteger(0);
        AtomicInteger currentlyHeld = new AtomicInteger(0);
        AtomicInteger maxSimultaneousHeld = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (limiter.tryAcquire()) {
                        successfulAcquisitions.incrementAndGet();
                        int current = currentlyHeld.incrementAndGet();
                        maxSimultaneousHeld.updateAndGet(max -> Math.max(max, current));

                        // simulate brief work
                        Thread.sleep(20);

                        currentlyHeld.decrementAndGet();
                        limiter.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // The maximum simultaneously held permits must never exceed the limit
        assertTrue(maxSimultaneousHeld.get() <= limit);
        assertEquals(limit, limiter.availablePermits());
    }
}
