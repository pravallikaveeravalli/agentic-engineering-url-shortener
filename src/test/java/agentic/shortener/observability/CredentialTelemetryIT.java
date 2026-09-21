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
     * The <em>test's own outbound HTTP client</em>, which is not the application.
     *
     * <p>The client logs the request body it is about to send, at {@code DEBUG}, from inside this JVM.
     * That is the test echoing its own input, and counting it would fail the assertion for a reason that
     * says nothing about the service.
     *
     * <p><strong>By package, not by class name.</strong> This was pinned to
     * {@code org.springframework.web.client.RestTemplate} and silently stopped matching when T052 forced
     * the suite onto Apache HttpClient — whose {@code http.wire} logger then reported the credential and
     * failed this test for the harness's behaviour rather than the application's. A prefix survives that
     * substitution.
     *
     * <p><strong>Why excluding a client logger cannot hide an application leak</strong>: the application
     * contains no HTTP client at all. It never fetches a destination — that is what makes the SSRF
     * residual in {@code AbuseGuard} acceptable — so every event from a client package in this JVM is the
     * test's. {@link #theApplicationHasNoHttpClient} asserts that rather than assuming it.
     */
    private static final List<String> TEST_HARNESS_LOGGER_PREFIXES = List.of(
            "org.apache.hc.",
            "org.springframework.web.client.");

    private String capturedText() {
        StringBuilder all = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(captured.list)) {
            if (isTestHarnessLogger(event.getLoggerName())) {
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

    private static boolean isTestHarnessLogger(String loggerName) {
        return TEST_HARNESS_LOGGER_PREFIXES.stream().anyMatch(loggerName::startsWith);
    }

    @Test
    @DisplayName("the exclusion covers only outbound HTTP client packages")
    void theExclusionIsNarrow() {
        // Asserted so that widening it later is a visible change rather than a quiet edit to a list.
        // Nothing of the application's own is excluded: agentic.shortener is explicitly in scope.
        assertEquals(2, TEST_HARNESS_LOGGER_PREFIXES.size(), TEST_HARNESS_LOGGER_PREFIXES.toString());
        for (String prefix : TEST_HARNESS_LOGGER_PREFIXES) {
            assertFalse(prefix.startsWith("agentic"), "no application logger may be excluded: " + prefix);
        }
        assertFalse(isTestHarnessLogger("agentic.shortener.delivery.LinkController"));
        assertFalse(isTestHarnessLogger("org.springframework.web.servlet.mvc.method.annotation"
                + ".RequestResponseBodyMethodProcessor"),
                "the SERVER-side body logger — the one that leaked in the first place — stays in scope");
        assertTrue(isTestHarnessLogger("org.apache.hc.client5.http.wire"));
    }

    @Test
    @DisplayName("the application contains no HTTP client, which is what makes the exclusion safe")
    void theApplicationHasNoHttpClient() throws Exception {
        // If the service ever fetched a destination itself, a client-package log line could be the
        // application's and excluding it would hide a real leak. It does not, and this is where that
        // stops being an assumption.
        List<String> offenders = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                String active = Files.readString(file).lines()
                        .map(String::stripLeading)
                        .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                        .reduce("", (a, b) -> a + "\n" + b);
                for (String client : List.of("HttpClient", "RestTemplate", "WebClient",
                        "URLConnection", "org.apache.hc")) {
                    if (active.contains(client)) {
                        offenders.add(file + " names " + client);
                    }
                }
            }
        }
        assertEquals(List.of(), offenders,
                "the application would then have its own HTTP client, and the exclusion above could "
                        + "hide its log lines: " + offenders);
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
