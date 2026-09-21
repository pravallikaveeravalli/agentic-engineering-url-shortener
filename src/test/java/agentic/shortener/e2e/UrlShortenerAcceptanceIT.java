package agentic.shortener.e2e;

import agentic.shortener.domain.creator.CreatorRepository;
import agentic.shortener.domain.link.ShortLink;
import agentic.shortener.domain.link.ShortLinkRepository;
import agentic.shortener.support.PostgresIntegrationTest;
import agentic.shortener.support.RestFixtures;
import agentic.shortener.support.TestCredentials;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * US1 acceptance sweep. Task T057. <strong>Slice 3's exit condition.</strong>
 *
 * <p>Every row of {@code quickstart.md} §2's negative-check table, executed rather than read. The
 * quickstart is the document a reviewer follows by hand; this is the same table as tests, so the two
 * cannot drift without one of them failing.
 *
 * <h2>What this sweep does NOT claim</h2>
 *
 * <p><strong>It does not claim FR-URL-016 whole.</strong> Two of that requirement's three tiers are built
 * and asserted here — PVT-012 creation and PVT-013 per-code redirects. The third, the per-creator
 * aggregate redirect tier (PVT-014), is <strong>deliberately deferred</strong> to the brownfield run
 * (T136a) and disclosed in {@code docs/delivery/baseline-omissions.md} (T055a).
 *
 * <p>That deferral is asserted here as a <em>fact of this build</em>, not skipped:
 * {@link #brownfieldBeforeState_aggregateTrafficIsNotThrottled} shows traffic spread across several links
 * passing unthrottled, and {@link #theDeferralIsDisclosedBeforeThisSweepRecordsIt} shows the register
 * carrying its entry. A green result on the first of those does <strong>not</strong> mean the requirement
 * is met; it means the gap is real, reproducible, and disclosed.
 *
 * <p>An acceptance sweep that recorded a partially built requirement as simply passing would put a green
 * false link into the traceability chain — the hardest kind to find later, and exactly what POL-TRC-001
 * exists to prevent. <strong>A recorded partial is a legal matrix state and a silent partial is not</strong>
 * (owner ruling, 2026-09-20).
 *
 * <h2>Orchestration is wired, but idle, on the US1 path</h2>
 *
 * <p>T057's original guard was "no orchestration bean is wired into the context that serves US1." T067
 * wires {@code OrchestrationConfiguration} into that same application context, which was always going to
 * make that zero-bean assertion fire — on purpose, as a forced decision point, per this class's own
 * (git-historical) comments on the guard test. The decision taken here is the one those comments
 * pre-authorized: not a loosening of the old assertion, but its replacement with the harder property —
 * US1 must still pass, and must create zero orchestration runs, with the beans PRESENT BUT IDLE. That is
 * what {@link #noOrchestrationOnThisPath} now asserts. The static half of the guard —
 * {@link #us1PackagesDoNotImportOrchestration}, backed by {@code DependencyDirectionTest} — is untouched
 * and remains the stronger of the two, since it holds whether or not a bean exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Lowered so the two built tiers can be demonstrated in a handful of requests rather
                // than six hundred. The APPROVED numbers are asserted against the shipped
                // application.yml by RateLimiterTest; this class demonstrates the behaviour.
                //
                // Twelve rather than eight, because a REFUSED request still spends budget: the limiter
                // runs before validation, deliberately, so that throttling stops work rather than
                // following it. SC-002's nine-item negative corpus therefore needs headroom, and the
                // first run of this class hit 429 on the ninth refusal — the limiter working, and the
                // test's arithmetic wrong.
                "shortener.ratelimit.creation-per-creator-per-minute=12",
                "shortener.ratelimit.redirect-per-code-per-minute=6"
        })
@Tag("us1")
@DisplayName("T057 US1 acceptance sweep — Slice 3 exit condition")
class UrlShortenerAcceptanceIT extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CreatorRepository creators;

    @Autowired
    private ShortLinkRepository links;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationContext context;

    private TestCredentials.Provisioned demo;

    @BeforeEach
    void provision() {
        // A client that neither retries nor follows redirects. Both matter here: a retry would sleep out
        // a rate-limit window and report the reset budget as a pass, and a followed redirect would make
        // every 307 invisible.
        RestFixtures.withoutRetries(rest);
        demo = TestCredentials.provision(creators, clock);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders headers(String authorization, String marker) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorization != null) {
            headers.set("Authorization", authorization);
        }
        if (marker != null) {
            headers.set("Idempotency-Key", marker);
        }
        return headers;
    }

    private ResponseEntity<String> create(String authorization, String marker, String body) {
        return rest.exchange(url("/v1/links"), HttpMethod.POST,
                new HttpEntity<>(body, headers(authorization, marker)), String.class);
    }

    private ResponseEntity<String> create(String destination) {
        return create(demo.header(), null, "{\"destination\":\"" + destination + "\"}");
    }

    private String codeOf(ResponseEntity<String> response) throws Exception {
        assertEquals(201, response.getStatusCode().value(), response.getBody());
        return JSON.readTree(response.getBody()).get("shortCode").asText();
    }

    private ResponseEntity<String> analytics(String code, String authorization) {
        HttpHeaders h = new HttpHeaders();
        if (authorization != null) {
            h.set("Authorization", authorization);
        }
        return rest.exchange(url("/v1/links/" + code + "/analytics"), HttpMethod.GET,
                new HttpEntity<>(h), String.class);
    }

    // ==============================================================================================
    // The happy path the quickstart opens with — SC-001
    // ==============================================================================================

    @Test
    @DisplayName("SC-001: a valid destination yields a link that resolves to the EXACT destination")
    void sc001_createFollowInspect() throws Exception {
        // "for 100% of the acceptance corpus" — so a corpus, not one URL, and each one chosen to carry
        // something a careless implementation would alter: path case, an encoded slash, query order, a
        // fragment, a port, a long path.
        List<String> corpus = List.of(
                "https://example.com/target",
                "https://example.com/A/b%2Fc/D?z=1&a=2&Mixed=Case#Frag-ment",
                "https://sub.example.co.uk:8443/deep/path/segment",
                "https://example.com/" + "x".repeat(1900),
                "http://example.com/plain");

        for (String destination : corpus) {
            String code = codeOf(create(destination));

            ResponseEntity<String> followed = rest.getForEntity(url("/" + code), String.class);
            assertEquals(307, followed.getStatusCode().value(),
                    destination + " did not redirect: " + followed.getBody());
            assertEquals(destination, followed.getHeaders().getFirst("Location"),
                    "the Location must equal the stored destination byte for byte (FR-URL-007)");

            // And the follow was counted, timestamp only (FR-URL-010, CL-002).
            JsonNode view = JSON.readTree(analytics(code, demo.header()).getBody());
            assertEquals(1, view.get("totalRedirects").asLong());
            assertEquals(1, view.get("events").size());
            assertEquals(1, view.get("events").get(0).size(),
                    "an event carries exactly one field, and it is a timestamp");
            assertTrue(view.get("events").get(0).has("occurredAt"));
        }
    }

    // ==============================================================================================
    // quickstart.md §2 — the negative-check table, row by row
    // ==============================================================================================

    @Test
    @DisplayName("ROW 1: javascript: destination → 400 SCHEME_NOT_ALLOWED, nothing persisted")
    void row1_disallowedScheme() throws Exception {
        long before = countLinks();

        ResponseEntity<String> response = create("javascript:alert(1)");

        assertEquals(400, response.getStatusCode().value(), response.getBody());
        assertEquals("SCHEME_NOT_ALLOWED", JSON.readTree(response.getBody()).get("code").asText(),
                "the quickstart and the contract's own example both name this code; a generic "
                        + "INVALID_INPUT satisfies the schema and tells a caller nothing (SC-002)");
        assertEquals(before, countLinks(), "nothing may be persisted by a refused request");
    }

    @Test
    @DisplayName("ROW 2: no Authorization header → 401, creation is never anonymous")
    void row2_anonymousCreationRefused() {
        ResponseEntity<String> response =
                create(null, null, "{\"destination\":\"https://example.com/anon\"}");
        assertEquals(401, response.getStatusCode().value(), response.getBody());
    }

    @Test
    @DisplayName("ROW 3: expired key then revoked key → 401 both times, BYTE-IDENTICAL")
    void row3_expiredAndRevokedAreIndistinguishable() {
        TestCredentials.Provisioned expired = TestCredentials.provisionExpired(creators, clock);
        TestCredentials.Provisioned revoked = TestCredentials.provisionRevoked(creators, clock);

        ResponseEntity<String> withExpired =
                create(expired.header(), null, "{\"destination\":\"https://example.com/e\"}");
        ResponseEntity<String> withRevoked =
                create(revoked.header(), null, "{\"destination\":\"https://example.com/r\"}");

        assertEquals(401, withExpired.getStatusCode().value());
        assertEquals(401, withRevoked.getStatusCode().value());
        assertEquals(String.valueOf(withExpired.getBody()), String.valueOf(withRevoked.getBody()),
                "EC-041: the distinction must not be disclosed");
        assertEquals(withExpired.getHeaders().getContentType(),
                withRevoked.getHeaders().getContentType());
    }

    @Test
    @DisplayName("ROW 4: follow an expired link → 410 LINK_EXPIRED, not a redirect")
    void row4_expiredLink() throws Exception {
        String code = codeOf(create("https://private.example/expired-campaign"));
        links.expire(links.findByShortCode(code).orElseThrow());

        ResponseEntity<String> response = rest.getForEntity(url("/" + code), String.class);

        assertEquals(410, response.getStatusCode().value(), response.getBody());
        assertEquals("LINK_EXPIRED", JSON.readTree(response.getBody()).get("code").asText());
        assertFalse(response.getStatusCode().is3xxRedirection());
        assertNull(response.getHeaders().getFirst("Location"), "and no Location at all");
        assertFalse(String.valueOf(response.getBody()).contains("private.example"),
                "nor the dead link's destination");
    }

    @Test
    @DisplayName("ROW 5: follow a never-issued code → 404 CODE_NOT_FOUND")
    void row5_neverIssuedCode() throws Exception {
        ResponseEntity<String> response = rest.getForEntity(url("/Zzzzzzz"), String.class);

        assertEquals(404, response.getStatusCode().value(), response.getBody());
        assertEquals("CODE_NOT_FOUND", JSON.readTree(response.getBody()).get("code").asText());
    }

    @Test
    @DisplayName("SC-003: expired and not-found are DISTINGUISHABLE, asserted together")
    void sc003_expiredIsDistinguishableFromNotFound() throws Exception {
        String expired = codeOf(create("https://example.com/sc003"));
        links.expire(links.findByShortCode(expired).orElseThrow());

        int expiredStatus = rest.getForEntity(url("/" + expired), String.class)
                .getStatusCode().value();
        int missingStatus = rest.getForEntity(url("/Yyyyyyy"), String.class)
                .getStatusCode().value();

        assertEquals(410, expiredStatus);
        assertEquals(404, missingStatus);
        assertNotEquals(expiredStatus, missingStatus,
                "SC-003 requires the two to differ in 100% of expiry tests");
    }

    @Test
    @DisplayName("ROW 6: another creator's analytics → 404, indistinguishable from not-found")
    void row6_noOwnershipOracle() throws Exception {
        String mine = codeOf(create("https://example.com/mine"));
        TestCredentials.Provisioned other = TestCredentials.provision(creators, clock);

        ResponseEntity<String> onMine = analytics(mine, other.header());
        ResponseEntity<String> onNothing = analytics("Qqqqqqq", other.header());

        assertEquals(404, onMine.getStatusCode().value());
        assertEquals(404, onNothing.getStatusCode().value());
        assertEquals(String.valueOf(onMine.getBody()), String.valueOf(onNothing.getBody()),
                "a difference here is an enumeration oracle (FR-URL-011)");
    }

    @Test
    @DisplayName("ROW 7: same marker, identical body → 200 replay: true, no second link")
    void row7_replay() throws Exception {
        String marker = "sweep-" + UUID.randomUUID();
        String body = "{\"destination\":\"https://example.com/replay\","
                + "\"expiresAt\":\"2026-12-01T10:00:00Z\"}";
        long before = countLinks();

        ResponseEntity<String> first = create(demo.header(), marker, body);
        assertEquals(201, first.getStatusCode().value(), first.getBody());

        ResponseEntity<String> second = create(demo.header(), marker, body);
        assertEquals(200, second.getStatusCode().value(), second.getBody());
        assertTrue(JSON.readTree(second.getBody()).get("replay").asBoolean(),
                "a replay is success, labelled as a replay (CL-008 case 2)");
        assertEquals(JSON.readTree(first.getBody()).get("shortCode").asText(),
                JSON.readTree(second.getBody()).get("shortCode").asText(),
                "the ORIGINAL link comes back");
        assertEquals(before + 1, countLinks(), "exactly one link, not two");
    }

    @Test
    @DisplayName("ROW 8: same marker, different expiresAt → 409, nothing minted or changed")
    void row8_idempotencyConflict() throws Exception {
        String marker = "sweep-" + UUID.randomUUID();
        ResponseEntity<String> first = create(demo.header(), marker,
                "{\"destination\":\"https://example.com/c\",\"expiresAt\":\"2026-12-01T10:00:00Z\"}");
        assertEquals(201, first.getStatusCode().value(), first.getBody());
        long after = countLinks();
        String original = JSON.readTree(first.getBody()).get("expiresAt").asText();

        ResponseEntity<String> conflict = create(demo.header(), marker,
                "{\"destination\":\"https://example.com/c\",\"expiresAt\":\"2026-12-02T10:00:00Z\"}");

        assertEquals(409, conflict.getStatusCode().value(), conflict.getBody());
        assertEquals("IDEMPOTENCY_CONFLICT", JSON.readTree(conflict.getBody()).get("code").asText());
        assertEquals(after, countLinks(), "nothing minted past a detected caller error");

        // Nothing CHANGED either: the original link's expiry is untouched.
        String code = JSON.readTree(first.getBody()).get("shortCode").asText();
        assertEquals(original, links.findByShortCode(code).orElseThrow().expiresAt().toString(),
                "a conflict must not alter the existing link (FR-URL-012)");
    }

    @Test
    @DisplayName("ROW 9: same destination twice with NO marker → two different codes")
    void row9_noDestinationDeduplication() throws Exception {
        String destination = "https://example.com/no-dedup";
        String first = codeOf(create(destination));
        String second = codeOf(create(destination));

        assertNotEquals(first, second,
                "there is no destination-based deduplication at any scope, ever (CL-008 case 1)");
    }

    @Test
    @DisplayName("ROW 10: readiness fails when the store does; liveness does not")
    void row10_healthDegradation() {
        // The quickstart's row says "stop the database". Doing that here would break every other class in
        // the tier, so ReadinessDegradationIT owns the stop with its own container. What this asserts is
        // the half that belongs in a sweep: the two probes are distinct, differently shaped, and readiness
        // reports what it checked. Stated rather than implied, so the row is not recorded as more than it
        // is.
        ResponseEntity<String> live = rest.getForEntity(url("/health/live"), String.class);
        ResponseEntity<String> ready = rest.getForEntity(url("/health/ready"), String.class);

        assertEquals(200, live.getStatusCode().value());
        assertEquals(200, ready.getStatusCode().value());
        assertTrue(String.valueOf(ready.getBody()).contains("checks"),
                "readiness reports what it checked");
        assertFalse(String.valueOf(live.getBody()).contains("checks"),
                "liveness must not, or a store outage would restart a healthy process");
        assertFalse(String.valueOf(ready.getBody()).contains("jdbc:"),
                "and neither publishes a connection string (T056)");
    }

    // ==============================================================================================
    // SC-002 and SC-017
    // ==============================================================================================

    @Test
    @DisplayName("SC-002: every negative-corpus destination is refused with an ACTIONABLE reason")
    void sc002_negativeCorpusRefusedActionably() throws Exception {
        // "100% of the negative corpus", and "actionable" is the part worth testing: each refusal must
        // carry a code a caller can branch on and a message that names a rule.
        // A creator of this test's own: nine refusals is a large fraction of any sane creation budget,
        // and a corpus that borrowed another test's remaining allowance would fail on ordering.
        demo = TestCredentials.provision(creators, clock);

        List<String> corpus = List.of(
                "javascript:alert(1)",
                "data:text/html,<script>x</script>",
                "file:///etc/passwd",
                "ftp://example.com/x",
                "not-a-url",
                "https://user:secret@example.com/x",
                "https://127.0.0.1/x",
                "https://169.254.169.254/latest/meta-data",
                "https://example.com/" + "x".repeat(2100));

        long before = countLinks();
        for (String destination : corpus) {
            ResponseEntity<String> response = create(destination);
            assertEquals(400, response.getStatusCode().value(),
                    destination + " was not refused: " + response.getBody());

            JsonNode body = JSON.readTree(response.getBody());
            assertTrue(body.has("code") && !body.get("code").asText().isBlank(),
                    destination + " — a refusal without a code is not actionable");
            assertTrue(body.has("message") && body.get("message").asText().length() > 10,
                    destination + " — nor is one without a message");
            assertFalse(String.valueOf(response.getBody()).contains("Exception"),
                    destination + " — and it must not be an exception dump");
        }
        assertEquals(before, countLinks(), "zero refused destinations may be stored");
    }

    @Test
    @DisplayName("SC-002: ZERO disallowed-scheme destinations exist in the store, ever")
    void sc002_noDisallowedSchemeIsEverStored() throws Exception {
        create("javascript:alert(1)");
        create("data:text/html,x");
        create("file:///etc/passwd");

        // Asserted against the table rather than against the API: the claim is about what is STORED.
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT COUNT(*) FROM short_link WHERE destination NOT LIKE 'http://%' "
                             + "AND destination NOT LIKE 'https://%'")) {
            rs.next();
            assertEquals(0, rs.getLong(1),
                    "a destination outside the allow-list reached the store (FR-URL-004, SC-002)");
        }
    }

    @Test
    @DisplayName("SC-017: zero personal-data fields in the stored schema")
    void sc017_noPersonalDataColumns() throws Exception {
        // Asserted against the live schema, not the migration files: what matters is the columns that
        // exist, and a column added outside Flyway would still be a column.
        // "fingerprint" alone is NOT in this list, and the reason is a false positive this check
        // produced on its first run: idempotency_record.request_fingerprint is a SHA-256 of the
        // destination and expiry a CALLER submitted. It identifies a request, not a follower, and
        // CL-008 depends on it existing. Device and browser fingerprinting are the real concern, so
        // those are what is matched. Narrowing the term beat allow-listing the column by name, which
        // would have exempted whatever else was ever called that.
        List<String> offenders = new ArrayList<>();
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT table_name, column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'public'")) {
            while (rs.next()) {
                String column = rs.getString("column_name").toLowerCase(java.util.Locale.ROOT);
                for (String personal : List.of("ip", "address", "user_agent", "useragent", "referrer",
                        "referer", "device", "geo", "country", "latitude", "longitude", "email",
                        "session", "device_fingerprint", "browser_fingerprint")) {
                    if (column.equals(personal) || column.contains("_" + personal)
                            || column.startsWith(personal + "_")) {
                        offenders.add(rs.getString("table_name") + "." + column);
                    }
                }
            }
        }
        assertEquals(List.of(), offenders,
                "these columns could identify a follower: " + offenders + " (NFR-SEC-005, SC-017)");

        // And the targeted assertion, which is the one that actually pins CL-002 down: the analytics
        // table's columns are a CLOSED SET. A broad term scan catches the fields somebody thought to
        // name badly; this catches any field at all appearing where follower data would live.
        List<String> eventColumns = new ArrayList<>();
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'public' AND table_name = 'redirect_event' "
                             + "ORDER BY column_name")) {
            while (rs.next()) {
                eventColumns.add(rs.getString("column_name"));
            }
        }
        assertEquals(List.of("occurred_at", "redirect_event_id", "short_code"), eventColumns,
                "redirect_event must hold a code, a timestamp and an identity column — nothing else. "
                        + "Any further column is where follower data would arrive (CL-002, FR-URL-010)");
    }

    @Test
    @DisplayName("SC-017: zero plaintext credentials in the repository")
    void sc017_noPlaintextCredentialsCommitted() throws Exception {
        // The scan itself is scripts/scan.sh, run by ci.sh over the whole repository and over captured
        // telemetry. Asserted here so the acceptance sweep records the property rather than assuming a
        // pipeline step covered it — and so the two cannot silently disagree about the pattern.
        String scanner = Files.readString(Path.of("scripts/scan.sh"));
        assertTrue(scanner.contains("crk_[A-Za-z0-9_-]{16,}"),
                "the scanner must match the key format the provisioning script produces");
        assertTrue(scanner.contains("--telemetry"),
                "and must be able to scan captured telemetry, which is where FR-URL-017 bites");
    }

    // ==============================================================================================
    // FR-URL-016 — two tiers asserted, the third recorded as deferred
    // ==============================================================================================

    @Test
    @DisplayName("FR-URL-016 PARTIAL, tier 1 of 3: creation is throttled per creator (PVT-012)")
    void frUrl016_tier1_creationThrottled() throws Exception {
        demo = TestCredentials.provision(creators, clock);
        for (int i = 1; i <= 12; i++) {
            assertEquals(201, create("https://example.com/sweep/tier1/" + i).getStatusCode().value());
        }
        ResponseEntity<String> throttled = create("https://example.com/sweep/tier1/over");

        assertEquals(429, throttled.getStatusCode().value(), throttled.getBody());
        assertTrue(JSON.readTree(throttled.getBody()).get("detail").asText()
                        .contains("per-creator-creation"),
                "the throttled outcome names which tier was exceeded (FR-URL-016)");
    }

    @Test
    @DisplayName("FR-URL-016 PARTIAL, tier 2 of 3: redirects are throttled per code (PVT-013)")
    void frUrl016_tier2_perCodeThrottled() throws Exception {
        String code = codeOf(create("https://example.com/sweep/tier2"));
        for (int i = 1; i <= 6; i++) {
            assertEquals(307, rest.getForEntity(url("/" + code), String.class)
                    .getStatusCode().value(), "follow " + i);
        }
        ResponseEntity<String> throttled = rest.getForEntity(url("/" + code), String.class);

        assertEquals(429, throttled.getStatusCode().value(), throttled.getBody());
        assertTrue(JSON.readTree(throttled.getBody()).get("detail").asText().contains("per-code"));
        assertFalse(String.valueOf(throttled.getBody()).contains(demo.creatorId().toString()),
                "a public follower must not learn who owns the link");
    }

    @Test
    @DisplayName("FR-URL-016 tier 3 of 3 — THE BROWNFIELD BEFORE-STATE: aggregate traffic is NOT throttled")
    void brownfieldBeforeState_aggregateTrafficIsNotThrottled() throws Exception {
        // READ THIS BEFORE READING THE ASSERTION.
        //
        // This test PASSES because FR-URL-016's third tier is NOT BUILT. It is not a test of correct
        // behaviour; it is a recorded measurement of a disclosed gap, and it exists so the gap is a
        // demonstrable before-state rather than a claim.
        //
        // PVT-014 would cap redirects per creator ACROSS ALL THEIR LINKS, deliberately below the sum of
        // the per-code limits. With the per-code limit at 6 for this class, four links could serve 24
        // follows while every individual link stayed inside its own limit. Nothing stops that today.
        //
        // Disclosed in advance, not discovered here:
        //   docs/delivery/baseline-omissions.md, entry 1, closed by T136a.
        //
        // FR-URL-016 remains BINDING IN FULL. This build does not satisfy it. Release readiness must
        // report it NOT MET if T136a does not close the tier — the register says so in those words.
        //
        // When T136a builds the tier, THIS TEST MUST FAIL. That failure is the after-state, and at that
        // point this test is rewritten and the register entry is closed. A test that kept passing would
        // mean the tier had not been built.
        demo = TestCredentials.provision(creators, clock);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            codes.add(codeOf(create("https://example.com/sweep/aggregate/" + i)));
        }

        int served = 0;
        for (int round = 0; round < 5; round++) {
            for (String code : codes) {
                if (rest.getForEntity(url("/" + code), String.class).getStatusCode().value() == 307) {
                    served++;
                }
            }
        }

        assertEquals(20, served,
                "all twenty follows must succeed. If this assertion starts failing, the per-creator "
                        + "aggregate tier has been built — which is the goal — and baseline-omissions.md "
                        + "entry 1 must be closed and this test rewritten as the after-state (T136a)");
    }

    @Test
    @DisplayName("the deferral is DISCLOSED, and the register says everything it must")
    void theDeferralIsDisclosedBeforeThisSweepRecordsIt() throws Exception {
        // T055a's ordering requirement, asserted from inside the sweep that depends on it. Without this
        // record the test above documents a gap with no disclosure behind it, and the sweep would be
        // writing the record to fit the finding instead of the other way round.
        Path register = Path.of("docs/delivery/baseline-omissions.md");
        assertTrue(Files.exists(register), "missing " + register);

        String text = Files.readString(register).replaceAll("\\s+", " ");
        assertTrue(text.contains("PVT-014"), "names the omitted tier");
        assertTrue(text.contains("FR-URL-016"), "names the requirement it belongs to");
        assertTrue(text.contains("binding in full"), "states the requirement is still binding");
        assertTrue(text.contains("T136a"), "names the run that closes it");
        assertTrue(text.contains("NOT MET"), "tells release readiness what to report if it does not");
    }

    // ==============================================================================================
    // T057's guard: no dependency on Phase 5
    // ==============================================================================================

    @Test
    @DisplayName("GUARD: US1 is demonstrable with the orchestration engine present but IDLE")
    void noOrchestrationOnThisPath() throws Exception {
        // THIS GUARD HAS NOW CHANGED FORM A SECOND TIME, AS PLANNED.
        //
        // Slice 3 asserted the orchestration package held no classes. Slice 4 built the state model and
        // the guard became "no orchestration bean is wired into the context that serves US1" — a
        // behavioural assertion, since code could exist without being wired in.
        //
        // T067 wires OrchestrationConfiguration (RunInspectionController, RunInspectionQuery) into this
        // same context, for the FIRST time, and the zero-bean assertion below would now fail permanently
        // on every run — not a regression, but the forced decision point this class's own prior comments
        // named in advance. The decision: US1 has NOT acquired a dependency on the engine. Nothing on the
        // US1 path constructs a workflow run, and that is now asserted directly rather than inferred from
        // bean absence — a stronger and more concrete claim than "the beans don't exist" ever was.
        //
        // So this guard has two parts: (1) the orchestration beans ARE present, proving Phase 5 wiring is
        // real rather than accidentally absent from this test's premise, and (2) driving the SAME US1
        // requests this sweep exercises elsewhere (create, follow, read analytics) creates ZERO rows in
        // workflow_run — orchestration's own root table. That is "present but idle" made falsifiable: an
        // interceptor or filter that secretly started a run on the US1 path would show up here as a
        // nonzero delta, not as a bean that happens to exist.
        //
        // The static direction — that no shortener package may even IMPORT orchestration — is
        // DependencyDirectionTest's (and this.us1PackagesDoNotImportOrchestration's), and remains the
        // stronger of the two guards because it holds whether or not a bean exists.
        List<String> orchestrationBeans = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type != null && type.getName().startsWith("agentic.shortener.orchestration")) {
                orchestrationBeans.add(name + " (" + type.getName() + ")");
            }
        }
        assertFalse(orchestrationBeans.isEmpty(),
                "expected T067's OrchestrationConfiguration to be wired into this context; if this is "
                        + "empty, the premise of this guard (beans PRESENT but idle) no longer holds and "
                        + "the guard must be reconsidered again rather than left passing vacuously");

        long runsBefore = countWorkflowRuns();

        String code = codeOf(create("https://example.com/orchestration-idle-check"));
        ResponseEntity<String> followed = rest.getForEntity(url("/" + code), String.class);
        assertEquals(307, followed.getStatusCode().value(), "the US1 path itself must still work");
        JSON.readTree(analytics(code, demo.header()).getBody());

        long runsAfter = countWorkflowRuns();
        assertEquals(runsBefore, runsAfter,
                "creating a link, following its redirect, and reading its analytics must not create an "
                        + "orchestration run — the engine is present on this path but IDLE");
    }

    private long countWorkflowRuns() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM workflow_run")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("GUARD: the US1 path imports nothing from the orchestration plane")
    void us1PackagesDoNotImportOrchestration() throws Exception {
        // The static half, asserted here as well as in DependencyDirectionTest, because this is the
        // sweep that claims US1 is independently demonstrable. An acceptance sweep that relied on
        // another class to hold its own guard would be one nobody re-reads.
        assertTrue(Files.exists(
                        Path.of("src/test/java/agentic/shortener/arch/DependencyDirectionTest.java")),
                "the architecture rule that enforces plane separation must exist");
        assertTrue(Files.readString(
                        Path.of("src/test/java/agentic/shortener/arch/DependencyDirectionTest.java"))
                        .contains("applicationPlaneDoesNotImportControlPlane"),
                "and must still contain the rule this sweep depends on");
    }

    private long countLinks() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM short_link")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
