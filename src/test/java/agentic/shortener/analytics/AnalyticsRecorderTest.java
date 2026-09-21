package agentic.shortener.analytics;

import agentic.shortener.domain.analytics.AnalyticsRecordingPort;
import agentic.shortener.domain.analytics.RedirectEvent;
import agentic.shortener.domain.analytics.RedirectEventRepository;
import agentic.shortener.persistence.analytics.TransactionalAnalyticsRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T050 — the recorder's failure semantics. FR-URL-010, EC-012. ADR-014.
 *
 * <p><strong>The contract under test is "never throws".</strong> EC-012 requires a resolvable redirect to
 * succeed while analytics recording fails, so a recorder that propagated its failure would take down a
 * link that was working perfectly — for the sake of a counter. That is the inversion this task exists to
 * prevent, and it is why the catch below is deliberately broad.
 *
 * <p><strong>Never silent, either.</strong> PVT-009 as ADR-014 redefined it is a ceiling on
 * <em>counted, visible</em> failures with <strong>zero silent ones</strong>. 0% was rejected because it
 * cannot survive its own fault-injection test: when the store is deliberately killed, appends must fail
 * and be counted, and that is the design working rather than breaking. So the counter is part of the port
 * and is asserted here.
 *
 * <p>Fast tier. The EC-012 proof <em>through HTTP</em> and the durability-across-restart proof are
 * {@code AnalyticsRecordingIT}'s; this covers the recorder's own behaviour.
 */
@DisplayName("T050 analytics recording failure semantics")
class AnalyticsRecorderTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    /** A store that can be told to fail, and with what. */
    private static final class Store implements RedirectEventRepository {
        private final List<RedirectEvent> appended = new ArrayList<>();
        private RuntimeException runtimeFailure;
        private Error fatalFailure;

        @Override
        public RedirectEvent append(RedirectEvent event) {
            if (fatalFailure != null) {
                throw fatalFailure;
            }
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            appended.add(event);
            return event;
        }

        @Override
        public List<RedirectEvent> findByShortCode(String shortCode) {
            return List.copyOf(appended);
        }

        @Override
        public List<RedirectEvent> findByShortCodeBetween(String c, Instant f, Instant t) {
            return List.copyOf(appended);
        }

        @Override
        public long countByShortCode(String shortCode) {
            return appended.size();
        }
    }

    private final Store store = new Store();
    private final AnalyticsRecordingPort recorder = new TransactionalAnalyticsRecorder(store);

    @Test
    @DisplayName("a successful append is counted as accepted")
    void successCounted() {
        recorder.record("Aaaaaaa", NOW);
        recorder.record("Aaaaaaa", NOW.plusSeconds(1));

        assertEquals(2, recorder.accepted());
        assertEquals(0, recorder.failed());
        assertEquals(2, store.appended.size());
        assertEquals("Aaaaaaa", store.appended.get(0).shortCode());
        assertEquals(NOW, store.appended.get(0).occurredAt());
    }

    @Test
    @DisplayName("EC-012: a failing append does NOT throw")
    void failingAppendDoesNotThrow() {
        store.runtimeFailure = new IllegalStateException("failed to append redirect event");

        assertDoesNotThrow(() -> recorder.record("Bbbbbbb", NOW),
                "a recorder that threw here would fail a redirect the link had earned — which is "
                        + "exactly what EC-012 forbids");
        assertEquals(1, recorder.failed());
        assertEquals(0, recorder.accepted());
    }

    @Test
    @DisplayName("EC-012: not even an Error escapes the recording path")
    void notEvenAnErrorEscapes() {
        // Deliberately broad, and the reason is stated rather than assumed: a catch of RuntimeException
        // would let a StackOverflowError or a LinkageError from a store driver through, and the redirect
        // would die for a counter. The two things that must not happen are throwing, and hiding.
        store.fatalFailure = new StackOverflowError("simulated");

        assertDoesNotThrow(() -> recorder.record("Ccccccc", NOW));
        assertEquals(1, recorder.failed(), "and it must still be counted, not merely swallowed");
    }

    @Test
    @DisplayName("NEVER SILENT: every failure increments the counter the port exposes")
    void everyFailureIsCounted() {
        store.runtimeFailure = new IllegalStateException("store down");
        for (int i = 0; i < 25; i++) {
            recorder.record("Ddddddd", NOW.plusSeconds(i));
        }
        assertEquals(25, recorder.failed(), "PVT-009 is measured from this counter (ADR-014 Condition 2)");
        assertEquals(0, recorder.accepted());
    }

    @Test
    @DisplayName("accepted is incremented AFTER the append returns, never before")
    void acceptedMeansDurable() {
        // A counter incremented first would over-report exactly when the store was in trouble — the
        // moment the number matters most. Proved by failing the append and checking accepted stayed 0.
        store.runtimeFailure = new IllegalStateException("store down");
        recorder.record("Eeeeeee", NOW);
        assertEquals(0, recorder.accepted(),
                "accepted must not count an event the store refused");

        store.runtimeFailure = null;
        recorder.record("Eeeeeee", NOW);
        assertEquals(1, recorder.accepted());
    }

    @Test
    @DisplayName("recovery: the counters keep both totals after the store comes back")
    void countersSurviveRecovery() {
        store.runtimeFailure = new IllegalStateException("store down");
        recorder.record("Fffffff", NOW);
        recorder.record("Fffffff", NOW.plusSeconds(1));

        store.runtimeFailure = null;
        recorder.record("Fffffff", NOW.plusSeconds(2));

        assertEquals(2, recorder.failed(), "the outage must remain visible after recovery");
        assertEquals(1, recorder.accepted());
    }

    @Test
    @DisplayName("the counters are correct under concurrency")
    void countersAreThreadSafe() throws Exception {
        // The redirect path is concurrent by nature, and a counter that loses increments under load
        // would under-report failures precisely when there are most of them.
        int threads = 64;
        store.runtimeFailure = new IllegalStateException("store down");
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    go.await();
                    recorder.record("Ggggggg", NOW);
                    return null;
                });
            }
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(threads, recorder.failed(), "increments were lost under concurrency");
    }

    @Test
    @DisplayName("CL-002: the port offers no parameter through which follower data could arrive")
    void portCannotCarryFollowerData() throws Exception {
        // FR-URL-010's data minimization is structural rather than a matter of remembering: there is no
        // IP address, user agent or referrer to omit, because the signature has nowhere to put one.
        Method record = AnalyticsRecordingPort.class.getMethod("record", String.class, Instant.class);
        assertEquals(2, record.getParameterCount(),
                "record takes a code and a timestamp. A third parameter would be the place a follower "
                        + "identifier eventually arrived");

        for (Method method : AnalyticsRecordingPort.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter.getName().toLowerCase(java.util.Locale.ROOT).contains("request"),
                        "method '" + method.getName() + "' takes " + parameter.getName()
                                + ", which would carry headers, an address and a user agent with it");
            }
        }
    }
}
