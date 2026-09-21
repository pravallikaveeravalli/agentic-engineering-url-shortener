package agentic.shortener.observability;

import agentic.shortener.audit.telemetry.StageTelemetry;
import agentic.shortener.support.PostgresIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T114 — proves the real, Spring-wired {@link StageTelemetry} actually lands on disk under
 * {@code target/telemetry/}, where {@code scripts/ci.sh}'s step 6 already runs
 * {@code scripts/scan.sh --telemetry} (T019) over it. NFR-OBS-001, NFR-OBS-002, FR-URL-017.
 *
 * <p>{@code StageTelemetryTest} and {@code RetryPolicyTelemetryTest} prove the signal shapes against a
 * captured {@code ListAppender}, which never touches {@code logback-spring.xml}'s routing. This is the
 * one place that proves the routing itself: a real Spring context, the real {@code TELEMETRY} appender,
 * the real rolling file.
 *
 * <p><strong>What this class does not prove</strong>, stated rather than implied:
 * {@link #theAppenderIsDeclaredRotatable} confirms the {@code SizeAndTimeBasedRollingPolicy} is
 * configured, not that a rollover was actually exercised — forcing one would mean writing past
 * {@code maxFileSize} (10 MB) inside a test, disproportionate to what T114 asks this class to prove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("T114 — real telemetry lands under target/telemetry/, rotatable, and secret-free")
class StageTelemetryFileCaptureIT extends PostgresIntegrationTest {

    private static final Path TELEMETRY_FILE = Path.of("target/telemetry/stage-telemetry.log");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private StageTelemetry telemetry;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("a real stage execution and retry attempt reach target/telemetry/stage-telemetry.log")
    void realTelemetryLandsOnDisk() throws Exception {
        UUID runId = UUID.randomUUID();

        telemetry.stageExecution(runId, 7, spanId -> {
            telemetry.retryAttempt(runId, spanId, 7, 1, () -> "attempted", "attempted"::equals);
            return "executed";
        }, "executed"::equals);

        assertTrue(Files.exists(TELEMETRY_FILE),
                "logback-spring.xml routes agentic.shortener.telemetry.* to this file; it must exist "
                        + "after a real emission");
        String content = Files.readString(TELEMETRY_FILE);
        assertTrue(content.contains(runId.toString()),
                "the run this test just executed must appear in the file the CI scan actually reads");

        // T019, FR-URL-017 (non-waivable): the same URL-credential shape scripts/scan.sh --telemetry
        // checks for. StageTelemetry structurally cannot emit one (no field carries stage data), and this
        // asserts that holds for what is actually on disk, not just for the type's shape.
        assertFalse(content.matches("(?s).*[A-Za-z][A-Za-z0-9+.-]*://[^/?#\\s@]+@.*"),
                "no URL-embedded credential shape may appear in captured telemetry");
    }

    @Test
    @DisplayName("the real MeterRegistry (not a test double) records a run_id-tagged sample")
    void realMeterRegistryRecordsTheSample() {
        UUID runId = UUID.randomUUID();
        telemetry.stageExecution(runId, 7, spanId -> "executed", "executed"::equals);

        var timer = meterRegistry.find("stage.execution.duration").tag("run_id", runId.toString()).timer();
        assertNotNull(timer, "the Spring-managed MeterRegistry must carry this run's tagged sample");
    }

    @Test
    @DisplayName("the TELEMETRY appender is a rolling (rotatable) file appender, not a plain one")
    void theAppenderIsDeclaredRotatable() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/logback-spring.xml"));
        assertTrue(config.contains("RollingFileAppender"),
                "T114's Artifact requires captured telemetry be rotatable — a plain FileAppender never "
                        + "rotates and would grow without bound");
        assertTrue(config.contains("SizeAndTimeBasedRollingPolicy") || config.contains("maxFileSize"),
                "a rolling policy must actually be configured, not merely the appender class named");
        assertTrue(config.contains("target/telemetry"),
                "the rotatable file must land where scripts/scan.sh --telemetry (T019) already looks");
    }
}
