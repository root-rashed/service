package com.example.marketplace.chatbot.service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;

/**
 * Stops the assistant from trying an absent model on every single question.
 *
 * <p>A local model that is not running does not fail quickly. Spring AI retries a refused
 * connection several times with a growing back-off, so without this every question would sit for
 * the full timeout before the built-in answers replied, and a chat box that takes twenty seconds to
 * say "hello" reads as a broken site rather than as a working fallback.
 *
 * <p>So failures are counted, and after a few of them the model is left alone for a cooling-off
 * period while the rules answer everything. The circuit closes on its own: once the period passes,
 * the next question tries the model again and recovers if it has come back. Nothing here has to be
 * restarted or reconfigured.
 *
 * <p>Not thread safe beyond what it needs to be: the counters are atomic and the deadline is
 * volatile, which is enough because a lost update only means one extra attempt.
 */
@Getter
class ModelCircuitBreaker {

    /** Failures in a row before the model is left alone. */
    private final int failureThreshold;

    /** How long the model is left alone once the threshold is reached. */
    private final long cooldownMillis;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    private volatile long retryAt;

    ModelCircuitBreaker(int failureThreshold, Duration cooldown) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldownMillis = Math.max(0, cooldown.toMillis());
    }

    /** True when the model is worth asking, either because it is healthy or the cooldown is up. */
    boolean isAvailable() {
        return System.currentTimeMillis() >= retryAt;
    }

    /** Seconds until the model will be tried again; zero when it is being tried now. */
    long retryInSeconds() {
        return Math.max(0, (retryAt - System.currentTimeMillis() + 999) / 1000);
    }

    /** A question was answered by the model, so it is healthy. */
    void recordSuccess() {
        consecutiveFailures.set(0);
        retryAt = 0;
    }

    /** A question could not reach the model; opens the circuit once there are too many in a row. */
    void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            retryAt = System.currentTimeMillis() + cooldownMillis;
        }
    }
}