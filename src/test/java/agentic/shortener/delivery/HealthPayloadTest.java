package agentic.shortener.delivery;

import agentic.shortener.persistence.ConnectionSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T056 — the health payloads carry <strong>only</strong> dependency names and states. FR-URL-015.
 *
 * <p>The degradation behaviour itself is {@code ReadinessDegradationIT}'s: it owns a container and stops
 * it, which is the only way to show readiness failing while liveness holds. What that test cannot do
 * cheaply is enumerate every field of every payload, and T056's guard is about exactly that — health
 * <strong>MUST NOT</strong> expose secrets, connection strings or internal topology.
 *
 * <p><strong>Asserted as a closed set, not as a list of forbidden words.</strong> {@code WalkingSkeletonIT}
 * already checks that the readiness body contains no {@code jdbc:} and no password, which catches the
 * mistakes somebody thought of. This asserts the payload contains <em>nothing else at all</em>, which
 * catches the ones nobody did: a driver version, a pool size, a host name, a stack trace. A deny-list of
 * field names cannot do that, and this project has found four separate places where a deny-list was the
 * wrong control.
 *
 * <p>Fast tier: the controller is constructed directly with a {@link ConnectionSource} that works and one
 * that does not. No container, because the failure being modelled is "the store did not answer", and a
 * lambda that throws is a faithful and deterministic model of it (ADR-011 permits an injected fake for a
 * reliability proof).
 */
@DisplayName("T056 health payloads expose nothing but names and states")
class HealthPayloadTest {

    private static final ConnectionSource UNREACHABLE = () -> {
        throw new java.sql.SQLException("Connection to db.internal:5432 refused; password authentication "
                + "failed for user \"shortener\"");
    };

    @Test
    @DisplayName("liveness carries EXACTLY one field, and no checks")
    void livenessIsOneField() {
        Map<String, Object> body = new HealthController(UNREACHABLE).live();

        assertEquals(Set.of("status"), body.keySet(),
                "liveness answers one question. Any further field is either useless or a disclosure");
        assertEquals("UP", body.get("status"));
        assertFalse(body.containsKey("checks"),
                "if liveness reported dependency checks, a store outage would fail it and an "
                        + "orchestrator would restart a process that was working (FR-URL-015)");
    }

    @Test
    @DisplayName("liveness is UP even when the store is unreachable")
    void livenessIgnoresTheStore() {
        // The same controller, the same broken ConnectionSource. Liveness never consults it, so the
        // answer cannot change — which is the property, stated as an equality rather than a hope.
        assertEquals(new HealthController(() -> null).live(),
                new HealthController(UNREACHABLE).live(),
                "liveness must be byte-identical whether or not the store is reachable");
    }

    @Test
    @DisplayName("readiness carries EXACTLY status and checks — nothing else")
    void readinessIsTwoFields() {
        ResponseEntity<Map<String, Object>> response = new HealthController(UNREACHABLE).ready();
        Map<String, Object> body = response.getBody();

        assertEquals(Set.of("status", "checks"), body.keySet(),
                "a closed set. A pool size, a driver version or a host name would each be internal "
                        + "topology, and none of them is a dependency name or a state");
    }

    @Test
    @DisplayName("every check value is UP or DOWN, and never a message")
    void checkValuesAreStatesOnly() {
        // This is where a driver exception reaches an unauthenticated caller in most services: the
        // check's value becomes its error text. The value set is closed to two literals.
        @SuppressWarnings("unchecked")
        Map<String, Object> checks = (Map<String, Object>)
                new HealthController(UNREACHABLE).ready().getBody().get("checks");

        assertEquals(Set.of("store"), checks.keySet(),
                "the dependency NAME is fine to publish; which host it lives on is not");
        for (Map.Entry<String, Object> check : checks.entrySet()) {
            assertTrue(Set.of("UP", "DOWN").contains(check.getValue()),
                    "check '" + check.getKey() + "' has value '" + check.getValue()
                            + "'; only UP and DOWN may be published");
        }
    }

    @Test
    @DisplayName("FAILS CLOSED: an unreachable store gives DOWN and 503")
    void readinessFailsClosed() {
        ResponseEntity<Map<String, Object>> response = new HealthController(UNREACHABLE).ready();

        assertEquals(503, response.getStatusCode().value(),
                "failing open would make a broken instance look healthy, which is the failure mode "
                        + "this default exists to prevent (T018)");
        assertEquals("DOWN", response.getBody().get("status"));
    }

    @Test
    @DisplayName("the driver's own message reaches no part of the response")
    void driverMessageIsNotDisclosed() {
        // The ConnectionSource above throws a message containing a host, a port, a user name and the
        // phrase "password authentication failed" — everything a probe must not republish. Rendering the
        // whole payload and searching it is the assertion, because the leak could be in any field.
        String rendered = String.valueOf(new HealthController(UNREACHABLE).ready().getBody());

        for (String leak : List.of("db.internal", "5432", "password", "shortener", "SQLException",
                "refused", "authentication")) {
            assertFalse(rendered.contains(leak),
                    "the driver's message leaked '" + leak + "' into the readiness payload: " + rendered);
        }
    }

    @Test
    @DisplayName("a reachable store gives UP and 200")
    void readinessUpWhenReachable() {
        // The baseline. Without it, a controller that always answered DOWN would satisfy every
        // assertion above.
        ConnectionSource reachable = () -> (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                HealthPayloadTest.class.getClassLoader(),
                new Class<?>[]{java.sql.Connection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isValid" -> true;
                    case "close" -> null;
                    case "toString" -> "reachable-connection";
                    default -> null;
                });

        ResponseEntity<Map<String, Object>> response = new HealthController(reachable).ready();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
    }
}
