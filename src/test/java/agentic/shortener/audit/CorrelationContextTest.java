package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T112 — the correlation-identifier carrier. NFR-OBS-001. Fast tier: no store, no network — a pure
 * thread-local mechanism, tested at the unit level.
 */
@DisplayName("T112 — CorrelationContext: scoped, restored, never leaks across threads")
class CorrelationContextTest {

    @Test
    @DisplayName("no active context outside a runWithin scope")
    void noActiveContextByDefault() {
        assertTrue(CorrelationContext.current().isEmpty());
        assertThrows(IllegalStateException.class, CorrelationContext::require);
    }

    @Test
    @DisplayName("inside runWithin, current() returns the supplied run id")
    void currentReturnsTheSuppliedRunId() {
        UUID runId = UUID.randomUUID();
        AtomicReference<UUID> seen = new AtomicReference<>();

        CorrelationContext.runWithin(runId, () -> seen.set(CorrelationContext.require()));

        assertEquals(runId, seen.get());
    }

    @Test
    @DisplayName("the context is restored to empty after the scope ends")
    void contextIsRestoredAfterTheScopeEnds() {
        CorrelationContext.runWithin(UUID.randomUUID(), () -> { });

        assertTrue(CorrelationContext.current().isEmpty(),
                "a scope that ended must leave nothing active — the NEXT unrelated call must not "
                        + "inherit a stale run id");
    }

    @Test
    @DisplayName("the context is restored even when the scoped action throws")
    void contextIsRestoredEvenOnException() {
        UUID runId = UUID.randomUUID();

        assertThrows(RuntimeException.class, () -> CorrelationContext.runWithin(runId, () -> {
            throw new RuntimeException("boom");
        }));

        assertTrue(CorrelationContext.current().isEmpty(),
                "a scope that failed must still restore — a leaked context after an exception is the "
                        + "exact leak this class exists to make impossible");
    }

    @Test
    @DisplayName("nested scopes compose: the inner id is active inside, the outer id resumes after")
    void nestedScopesComposeCorrectly() throws Exception {
        UUID outer = UUID.randomUUID();
        UUID inner = UUID.randomUUID();

        CorrelationContext.runWithin(outer, () -> {
            assertEquals(outer, CorrelationContext.require());

            CorrelationContext.runWithin(inner, () -> assertEquals(inner, CorrelationContext.require()));

            // The inner scope ended; the outer id must have resumed, not been cleared.
            assertEquals(outer, CorrelationContext.require());
            return null;
        });

        assertTrue(CorrelationContext.current().isEmpty());
    }

    @Test
    @DisplayName("a value set on one thread is never visible on another")
    void contextDoesNotLeakAcrossThreads() throws Exception {
        UUID runId = UUID.randomUUID();
        CountDownLatch mainEntered = new CountDownLatch(1);
        CountDownLatch otherChecked = new CountDownLatch(1);
        AtomicReference<Boolean> otherThreadSawIt = new AtomicReference<>();

        Thread other = new Thread(() -> {
            try {
                mainEntered.await();
                otherThreadSawIt.set(CorrelationContext.current().isPresent());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                otherChecked.countDown();
            }
        });
        other.start();

        CorrelationContext.runWithin(runId, () -> {
            mainEntered.countDown();
            try {
                otherChecked.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        other.join();

        assertFalse(otherThreadSawIt.get(),
                "a correlation id set on this thread must not be visible on a different one");
    }
}
