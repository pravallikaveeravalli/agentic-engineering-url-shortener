package agentic.shortener.audit.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The three operational signal types for a stage execution and its retry attempts — structured JSON logs,
 * counters and timers, and a span per execution/attempt parented to the run. Task T114. NFR-OBS-001,
 * NFR-OBS-002, FR-URL-017, SC-011. plan §7.
 *
 * <p><strong>Not the audit trail.</strong> ADR-010 keeps the two apart deliberately: this signal is lossy
 * and rotated — see {@code logback-spring.xml}'s rolling policy on the {@code TELEMETRY} appender — while
 * {@link agentic.shortener.audit.AuditWriter} is append-only and immutable. Conflating them is what would
 * make log-derived audit unachievable: a rotated file cannot be the evidence of record.
 *
 * <p><strong>Nothing about a stage's data reaches this class.</strong> The methods below take only ids,
 * numbers, and a body to run — never {@code inputArtifacts} — so there is no call shape through which a
 * secret could reach captured telemetry. That is what lets {@code scripts/scan.sh --telemetry} pass by
 * construction rather than by a redaction step that could miss one.
 *
 * <p><strong>{@code run_id} as a metric tag</strong> is a declared trade: unbounded label cardinality
 * against per-run queryability. Acceptable at this project's declared demonstration scale (NFR-AUT-003);
 * not a decision to assume carries to a long-lived production registry without revisiting it.
 *
 * <p><strong>Generic in the wrapped result, deliberately.</strong> An earlier version of this class took
 * and returned {@code StageOutcome}/{@code RetryOutcome} directly, which made {@code audit} depend on
 * {@code orchestration} — and {@code orchestration.reliability.RetryPolicy} already depends on this class,
 * so that shape was a package cycle {@code DependencyDirectionTest} (ADR-006) correctly refused. The
 * caller supplies its own {@code succeeded} predicate instead, so this class never needs to know what an
 * orchestration outcome type looks like.
 */
@Component
public final class StageTelemetry {

    private static final Logger LOG = LoggerFactory.getLogger("agentic.shortener.telemetry.stage");

    private static final String STAGE_TIMER = "stage.execution.duration";
    private static final String STAGE_COUNTER = "stage.execution.count";
    private static final String ATTEMPT_TIMER = "stage.retry.attempt.duration";
    private static final String ATTEMPT_COUNTER = "stage.retry.attempt.count";

    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    /**
     * {@code @Autowired} is explicit here, not decorative: with the {@link #disabled()} no-arg
     * constructor also present, Spring's constructor resolution otherwise prefers the no-arg one and
     * silently builds a disabled bean — exactly the failure this annotation exists to rule out.
     */
    @Autowired
    public StageTelemetry(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.enabled = true;
    }

    private StageTelemetry() {
        this.meterRegistry = null;
        this.enabled = false;
    }

    /**
     * For a call site with no Spring-managed registry (existing {@code RetryPolicy} unit-test callers).
     * Emits no log line and records no metric — a caller that opted out must see nothing, not a registry
     * quietly swallowing what it would otherwise have recorded.
     */
    public static StageTelemetry disabled() {
        return new StageTelemetry();
    }

    /**
     * Wraps one full stage execution (all attempts) in a span parented to the run. {@code body} receives
     * the generated span id, so nested {@link #retryAttempt} calls can parent to it. {@code succeeded}
     * classifies a non-null result — evaluated only when {@code result} is non-null, so it never has to
     * handle {@code null} itself.
     */
    public <T> T stageExecution(UUID runId, int stageNumber, Function<UUID, T> body,
                                 Predicate<T> succeeded) {
        Objects.requireNonNull(runId, "runId");
        UUID spanId = UUID.randomUUID();
        Instant started = Instant.now();
        emit(new StructuredLogEvent(started, runId, spanId, null, "STAGE_EXECUTION_STARTED", stageNumber,
                null, null, null));

        T result = null;
        try {
            result = body.apply(spanId);
            return result;
        } finally {
            Duration elapsed = Duration.between(started, Instant.now());
            String outcome = result != null && succeeded.test(result) ? "SUCCEEDED" : "FAILED";
            emit(new StructuredLogEvent(Instant.now(), runId, spanId, null, "STAGE_EXECUTION_COMPLETED",
                    stageNumber, null, outcome, elapsed.toMillis()));
            record(STAGE_TIMER, STAGE_COUNTER, runId, stageNumber, null, outcome, elapsed);
        }
    }

    /** Wraps one retry attempt; its span is parented to the enclosing stage-execution span. */
    public <T> T retryAttempt(UUID runId, UUID stageSpanId, int stageNumber, int attemptNumber,
                               Supplier<T> body, Predicate<T> succeeded) {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(stageSpanId, "stageSpanId");
        UUID spanId = UUID.randomUUID();
        Instant started = Instant.now();
        emit(new StructuredLogEvent(started, runId, spanId, stageSpanId, "RETRY_ATTEMPT_STARTED",
                stageNumber, attemptNumber, null, null));

        T result = null;
        try {
            result = body.get();
            return result;
        } finally {
            Duration elapsed = Duration.between(started, Instant.now());
            String outcome = result != null && succeeded.test(result) ? "SUCCEEDED" : "FAILED";
            emit(new StructuredLogEvent(Instant.now(), runId, spanId, stageSpanId, "RETRY_ATTEMPT_COMPLETED",
                    stageNumber, attemptNumber, outcome, elapsed.toMillis()));
            record(ATTEMPT_TIMER, ATTEMPT_COUNTER, runId, stageNumber, attemptNumber, outcome, elapsed);
        }
    }

    private void emit(StructuredLogEvent event) {
        if (!enabled) {
            return;
        }
        LOG.info(event.toJson());
    }

    private void record(String timerName, String counterName, UUID runId, int stageNumber,
                         Integer attemptNumber, String outcome, Duration elapsed) {
        if (!enabled) {
            return;
        }
        Tags tags = Tags.of("run_id", runId.toString(), "stage_number", String.valueOf(stageNumber),
                "outcome", outcome);
        if (attemptNumber != null) {
            tags = tags.and("attempt_number", String.valueOf(attemptNumber));
        }
        Timer.builder(timerName).tags(tags).register(meterRegistry).record(elapsed);
        meterRegistry.counter(counterName, tags).increment();
    }
}
