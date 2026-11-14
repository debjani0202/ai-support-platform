package com.supportai.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * Shared singleton concurrency limiter for all Ollama LLM requests across the application
 * (category classification, priority suggestion, etc.).
 *
 * Ensures that combined concurrent requests to the local Ollama instance cannot exceed
 * the configured capacity (app.ai.ollama.max-concurrent-requests).
 * Hardened against accidental over-release beyond the configured maximum.
 */
@Component
public class OllamaConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(OllamaConcurrencyLimiter.class);

    private final int maxConcurrentRequests;
    private final Semaphore semaphore;

    @Autowired
    public OllamaConcurrencyLimiter(
            @Value("${app.ai.ollama.max-concurrent-requests:2}") int maxConcurrentRequests
    ) {
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException(
                    "Ollama max concurrent requests must be positive, got: " + maxConcurrentRequests
            );
        }
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.semaphore = new Semaphore(maxConcurrentRequests);
    }

    /**
     * Attempts to acquire a concurrency permit immediately.
     *
     * @return {@code true} if a permit was acquired; {@code false} otherwise
     */
    public synchronized boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Releases a previously acquired permit.
     * Must be invoked only in a finally block after a successful {@link #tryAcquire()}.
     * Hardened to reject releases when all permits are already available.
     *
     * @throws IllegalStateException if all permits are already available (accidental over-release)
     */
    public synchronized void release() {
        if (semaphore.availablePermits() >= maxConcurrentRequests) {
            log.warn("Attempted to release a permit when all permits are already available ({}/{})",
                    semaphore.availablePermits(), maxConcurrentRequests);
            throw new IllegalStateException("Cannot release permit: all permits are already available");
        }
        semaphore.release();
    }

    /**
     * Returns the number of currently available permits.
     */
    public synchronized int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * Returns the maximum configured concurrent requests.
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }
}
