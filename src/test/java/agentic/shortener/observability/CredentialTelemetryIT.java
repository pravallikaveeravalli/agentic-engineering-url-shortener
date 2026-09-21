package agentic.shortener.observability;

import agentic.shortener.support.PostgresIntegrationTest;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T037's Done condition: <strong>no occurrence in captured telemetry</strong>. FR-URL-017, EC-007.
 *
 * <p>{@code CredentialRedactionTest} proves the redactor works on strings. That is not the requirement.
 * The requirement is that credentials in a submitted destination never reach a log or a trace, and the
 * only way to check that is to submit one to the running application and read everything it wrote.
 *
 * <p>The danger is not that someone writes {@code log.info(destination)}. It is that a framework does it
 * for them: an exception message quoting the offending input, a parser echoing what it could not read, a
 * request log recording the body. So this attaches a capture appender to the <em>root</em> logger at
 * {@code DEBUG} and reads every event any component emits, including the text of every stack trace.
 *
 * <p><strong>What this does not cover</strong>, stated rather than implied: the appender attaches after
 * the context has started, so startup output is outside it, and the level is {@code DEBUG} rather than
 * {@code TRACE}. FR-URL-017's "at any log level, including startup and shutdown" clause is about creator
 * API key material, which is T052's and ADR-013's; this covers EC-007's destination credentials.
 *
 * <p>The captured text is written to {@code target/telemetry/} so {@code scripts/scan.sh --telemetry}
 * can run T019's scan over it, which is the second half of T037's Validate field.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("T037 credentials never reach captured telemetry")
class CredentialTelemetryIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Deliberately unlabelled. A value carrying {@code TESTONLY} would be waved through by the
     * scanner's fixture allowance, and the scan would then prove nothing at all.
     */
    private static final String SECRET = "hunter2-qzjxvm";

    private static final String USERNAME = "svc-admin-qzjxvm";

    private static final Path CAPTURE = Path.of("target/telemetry/credential-redaction.log");

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private ListAppender<ILoggingEvent> captured;
    private Logger root;
    private Level originalLevel;

    @BeforeEach
    void attachCaptureAppender() {
        root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        originalLevel = root.getLevel();
        root.setLevel(Level.DEBUG);
        captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
    }

    @AfterEach
    void detachCaptureAppender() {
        root.detachAppender(captured);
        captured.stop();
        root.setLevel(originalLevel);
    }

    private String post(String destination) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = rest.exchange(
                "http://localhost:" + port + "/v1/links", HttpMethod.POST,
                new HttpEntity<>("{\"destination\":\"" + destination + "\"}", headers), String.class);
        return String.valueOf(response.getBody());
    }

    /**
     * The one logger that is the <em>test's</em> own and not the application's.
     *
     * <p>{@code TestRestTemplate} logs the request body it is about to send, at {@code DEBUG}, from
     * inside this JVM. That is the test echoing its own input: there is no such client in the running
     * service, so counting it would fail the assertion for a reason that says nothing about the
     * application. The exclusion is kept to exactly one logger and asserted to be exactly one, because
     * the obvious way to make this test pass wrongly is to widen it.
     */
    private static final List<String> TEST_HARNESS_LOGGERS =
            List.of("org.springframework.web.client.RestTemplate");

    private String capturedText() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(captured.list)) {
            if (TEST_HARNESS_LOGGERS.contains(event.getLoggerName())) {
                continue;
            }
            all.append(event.getLoggerName()).append(' ')
                    .append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                all.append(ThrowableProxyUtil.asString(event.getThrowableProxy())).append('\n');
            }
        }
        return all.toString();
    }

    @Test
    @DisplayName("the harness exclusion is exactly one test-only logger")
    void theExclusionIsNarrow() {
        // Asserted so that widening it later is a visible change to a test rather than a quiet edit to
        // a list. Everything else the application emits is in scope.
        assertEquals(1, TEST_HARNESS_LOGGERS.size(), TEST_HARNESS_LOGGERS.toString());
        assertTrue(TEST_HARNESS_LOGGERS.get(0).endsWith("client.RestTemplate"),
                "only the test's own outbound HTTP client may be excluded");
    }

    @Test
    @DisplayName("NON-WAIVABLE: nothing a credential-bearing request produces contains the credential")
    void credentialsNeverReachTelemetryOrResponses() throws Exception {
        // Several shapes, because each takes a different path through the application: a plain refusal,
        // one that is also abusive, one that is also over-length, and one with a username only.
        String longTail = "x".repeat(2100);
        List<String> bodies = List.of(
                post("https://" + USERNAME + ":" + SECRET + "@example.com/x"),
                post("https://" + USERNAME + ":" + SECRET + "@169.254.169.254/latest/meta-data"),
                post("https://" + USERNAME + ":" + SECRET + "@example.com/" + longTail),
                post("https://" + USERNAME + "@example.com/x"),
                post("https://" + USERNAME + ":" + SECRET + "@exa mple.com/malformed"));

        String telemetry = capturedText();
        Files.createDirectories(CAPTURE.getParent());
        Files.writeString(CAPTURE, telemetry);

        assertFalse(telemetry.isBlank(),
                "no telemetry was captured at all, so this test would pass without proving anything");

        // The Done condition.
        assertFalse(telemetry.contains(SECRET),
                "the password reached telemetry. FR-URL-017 is non-waivable: this is a stop condition, "
                        + "not a defect to schedule.");
        assertFalse(telemetry.contains(USERNAME), "the username reached telemetry");
        assertFalse(telemetry.contains("hunter2"), "a fragment of the password reached telemetry");
        assertFalse(telemetry.contains(USERNAME + ":" + SECRET), "the pair reached telemetry");

        // FR-URL-017 names error responses alongside logs and traces.
        for (String body : bodies) {
            assertFalse(body.contains(SECRET), "the password reached a response body: " + body);
            assertFalse(body.contains(USERNAME), "the username reached a response body: " + body);
        }
    }

    @Test
    @DisplayName("the capture mechanism itself works — proved against a planted line")
    void theCaptureMechanismIsFalsifiable() {
        // A capture that silently recorded nothing would make the test above pass for the worst
        // possible reason. Same argument as T013's: a check that has never caught anything is
        // unvalidated, so it is made to catch something on purpose.
        String planted = "planted-telemetry-probe-qzjxvm";
        LoggerFactory.getLogger(CredentialTelemetryIT.class).debug(planted);

        assertTrue(capturedText().contains(planted),
                "the appender captured nothing; every assertion about absence would be vacuous");
    }
}
