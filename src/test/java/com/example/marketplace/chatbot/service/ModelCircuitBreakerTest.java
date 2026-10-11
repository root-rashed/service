package com.example.marketplace.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The guard that keeps an absent model from making every question slow.
 *
 * <p>Without it, a refused connection is retried with a back-off and each question waits out the
 * full timeout before the built-in answers reply. These tests pin the two properties that matter:
 * the circuit opens after repeated failures, and it closes again by itself.
 */
class ModelCircuitBreakerTest {

    @Test
    @DisplayName("is available before anything has gone wrong")
    void startsAvailable() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(2, Duration.ofMinutes(2));

        assertThat(breaker.isAvailable()).isTrue();
        assertThat(breaker.retryInSeconds()).isZero();
    }

    @Test
    @DisplayName("stays open until the failures add up to the threshold")
    void opensAtTheThreshold() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(3, Duration.ofMinutes(2));

        breaker.recordFailure();
        breaker.recordFailure();
        assertThat(breaker.isAvailable()).as("still tried after two of three failures").isTrue();

        breaker.recordFailure();
        assertThat(breaker.isAvailable()).isFalse();
        assertThat(breaker.retryInSeconds()).isPositive();
    }

    @Test
    @DisplayName("closes again after a success")
    void successClosesTheCircuit() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, Duration.ofMinutes(2));
        breaker.recordFailure();
        assertThat(breaker.isAvailable()).isFalse();

        // A model that has come back must be used again, not written off for the process lifetime.
        breaker.recordSuccess();
        assertThat(breaker.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("a success part way through a run of failures clears the count")
    void successResetsTheCount() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(3, Duration.ofMinutes(2));

        breaker.recordFailure();
        breaker.recordFailure();
        breaker.recordSuccess();
        breaker.recordFailure();

        assertThat(breaker.isAvailable()).as("the count restarted, not carried over").isTrue();
    }

    @Test
    @DisplayName("comes back on its own once the cooling-off period passes")
    void recoversWithoutBeingReset() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(1, Duration.ofMillis(120));
        breaker.recordFailure();
        assertThat(breaker.isAvailable()).isFalse();

        try {
            Thread.sleep(200);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
        assertThat(breaker.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("a zero threshold still waits for one failure, and a zero cooldown reopens at once")
    void degenerateSettingsAreNotHarmful() {
        ModelCircuitBreaker breaker = new ModelCircuitBreaker(0, Duration.ZERO);

        assertThat(breaker.isAvailable()).isTrue();
        breaker.recordFailure();
        // Nothing to protect against with no cooling-off, so the next question tries again.
        assertThat(breaker.isAvailable()).isTrue();
    }
}