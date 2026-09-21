package agentic.shortener.domain.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T024 — RedirectEvent. FR-URL-010, NFR-SEC-005, POL-PRIV-001, CL-002. ADR-014.
 *
 * <p><strong>Absence is the requirement.</strong> T024's Artifact field is {@code id}, {@code
 * short_code}, {@code occurred_at} — <em>"and nothing else"</em> — and its guard says a test asserts
 * the absence so a reviewer can read it.
 *
 * <p>So the central test here enumerates the type's fields and accessors and fails on anything
 * outside the permitted three. That is deliberately stricter than checking for a list of forbidden
 * names: a deny-list would pass a field called {@code clientFingerprint}, which is exactly the kind
 * of name a well-meaning future change would choose. <strong>The allow-list is the control.</strong>
 *
 * <p>CL-002's deciding ground was data minimisation, and it is also what makes indefinite retention
 * safe (NFR-AUD-003, CR-017): there is no personal data here to retain. Weakening this type would
 * silently invalidate the retention posture too.
 *
 * <p>Fast tier.
 */
@DisplayName("T024 RedirectEvent - absence is the requirement")
class RedirectEventTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");

    /** The complete permitted state. Anything else is a defect, by requirement. */
    private static final Set<String> PERMITTED_FIELDS = Set.of("id", "shortCode", "occurredAt");

    @Test
    @DisplayName("a redirect event carries exactly a code and an instant")
    void shape() {
        RedirectEvent e = RedirectEvent.of(42L, "abc123", NOW);
        assertEquals(42L, e.id());
        assertEquals("abc123", e.shortCode());
        assertEquals(NOW, e.occurredAt());
    }

    @Test
    @DisplayName("the type declares NO field outside the permitted three")
    void noFieldOutsideTheAllowList() {
        List<String> declared = java.util.Arrays.stream(RedirectEvent.class.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toList());

        assertEquals(PERMITTED_FIELDS, Set.copyOf(declared),
                "RedirectEvent must hold EXACTLY " + PERMITTED_FIELDS + " and nothing else (CL-002, "
                        + "POL-PRIV-001). An allow-list is used rather than a deny-list because a "
                        + "deny-list would pass a plausible-looking new name. Declared: " + declared);
    }

    @Test
    @DisplayName("no accessor exposes an IP, agent, referrer, device or location")
    void noForbiddenAccessor() {
        // The allow-list above is the real control. This deny-list runs alongside it because it names
        // the specific fields CL-002 rejected, so a reader sees WHAT was excluded, not only that
        // something was.
        List<String> forbidden = List.of("ip", "ipaddress", "useragent", "agent", "referrer",
                "referer", "device", "deviceid", "geo", "geolocation", "location", "country",
                "city", "latitude", "longitude", "session", "sessionid", "fingerprint", "cookie",
                "follower", "visitor", "userid");

        for (Method m : RedirectEvent.class.getDeclaredMethods()) {
            if (m.isSynthetic()) {
                continue;
            }
            String name = m.getName().toLowerCase();
            for (String bad : forbidden) {
                assertTrue(!name.contains(bad),
                        "accessor '" + m.getName() + "' exposes " + bad + ", which CL-002 excluded "
                                + "on data-minimisation grounds. That exclusion is also what makes "
                                + "indefinite retention safe (CR-017)");
            }
        }
        for (Field f : RedirectEvent.class.getDeclaredFields()) {
            String name = f.getName().toLowerCase();
            for (String bad : forbidden) {
                assertTrue(!name.contains(bad), "field '" + f.getName() + "' exposes " + bad);
            }
        }
    }

    @Test
    @DisplayName("toString cannot leak what the type does not hold, and is asserted anyway")
    void toStringHoldsOnlyPermittedState() {
        String rendered = RedirectEvent.of(42L, "abc123", NOW).toString();
        assertTrue(rendered.contains("abc123"), "the code is permitted state");
        // NFR-SEC-005: no sensitive data in telemetry. A log line built from toString is telemetry.
        for (String bad : List.of("192.168", "Mozilla", "http://referrer")) {
            assertTrue(!rendered.contains(bad), "toString must render only permitted state");
        }
    }

    @Test
    @DisplayName("required fields are enforced by the type")
    void requiredFields() {
        assertThrows(NullPointerException.class, () -> RedirectEvent.of(1L, null, NOW));
        assertThrows(NullPointerException.class, () -> RedirectEvent.of(1L, "abc123", null));
        assertThrows(IllegalArgumentException.class, () -> RedirectEvent.of(1L, "  ", NOW),
                "a blank code cannot identify a link");
    }
}
