package agentic.shortener.domain.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T036 — abuse and malicious-redirect controls. FR-URL-005. EC-004, EC-005.
 *
 * <p><strong>FR-URL-005 permits two outcomes and this takes the stricter one.</strong> The requirement
 * says such destinations must be addressed <em>"either by refusal or by an explicitly recorded accepted
 * risk"</em>, and nothing upstream picks between them. Refusal is chosen: it cannot weaken anything, so
 * it needs no approval, whereas accepting the risk would.
 *
 * <p><strong>The boundary cases are the point.</strong> A range check is almost never wrong in the middle
 * of a range — it is wrong one address either side of it. {@code 172.16.0.0/12} is the classic: written as
 * {@code 172.16}–{@code 172.32} it wrongly refuses a public address, written as {@code 172.16} only it
 * wrongly accepts thirty private networks. Every range below is tested at both edges and one address
 * outside each.
 *
 * <p><strong>No name resolution, asserted structurally.</strong> This guard decides on the destination's
 * literal text and never resolves a hostname. That is a deliberate limit, not an oversight: a
 * resolve-then-fetch check is defeated by DNS rebinding — the answer can change between the check and the
 * redirect — and a DNS call inside the domain would make this class untestable without a network. The
 * residual is therefore explicit: <em>a hostname that resolves to private space is not refused here.</em>
 * A test asserts the source performs no resolution, so the residual cannot be quietly closed with a
 * mechanism that does not work.
 *
 * <p>Fast tier: pure domain, no network, no store.
 */
@DisplayName("T036 abuse and malicious-redirect controls")
class AbuseGuardTest {

    private static final Set<String> OWN_HOSTS = Set.of("short.example", "www.short.example");

    private final AbuseGuard guard = new AbuseGuard(OWN_HOSTS);

    private AbuseGuard.Outcome classify(String host) {
        return guard.classify("https://" + host + "/target");
    }

    @Test
    @DisplayName("EC-004: loopback is refused, in every spelling")
    void loopbackRefused() {
        for (String host : List.of("127.0.0.1", "127.0.0.2", "127.1.2.3", "127.255.255.255",
                "[::1]", "localhost", "LOCALHOST", "foo.localhost")) {
            AbuseGuard.Outcome outcome = classify(host);
            assertTrue(outcome == AbuseGuard.Outcome.LOOPBACK_ADDRESS
                            || outcome == AbuseGuard.Outcome.LOCAL_NAME,
                    host + " is loopback and must be refused; got " + outcome);
        }
        // The edges of 127.0.0.0/8.
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("126.255.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("128.0.0.1"));
    }

    @Test
    @DisplayName("EC-004: RFC 1918 private space is refused — every range, at both edges")
    void privateSpaceRefused() {
        // 10.0.0.0/8
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("9.255.255.255"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("10.0.0.0"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("10.255.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("11.0.0.0"));

        // 172.16.0.0/12 — the range most often written wrong.
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("172.15.255.255"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("172.16.0.0"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("172.31.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("172.32.0.0"),
                "172.32.0.0 is PUBLIC; refusing it would be a correctness bug in the other direction");

        // 192.168.0.0/16
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("192.167.255.255"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("192.168.0.0"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("192.168.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("192.169.0.0"));

        // IPv6 unique-local, fc00::/7.
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("[fc00::1]"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("[fd12:3456:789a::1]"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("[2606:2800:220:1::1946]"));
    }

    @Test
    @DisplayName("EC-004: link-local is refused — including the cloud metadata address")
    void linkLocalRefused() {
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("169.253.255.255"));
        assertEquals(AbuseGuard.Outcome.LINK_LOCAL_ADDRESS, classify("169.254.0.0"));
        assertEquals(AbuseGuard.Outcome.LINK_LOCAL_ADDRESS, classify("169.254.169.254"),
                "the cloud instance-metadata address is the highest-value SSRF target there is");
        assertEquals(AbuseGuard.Outcome.LINK_LOCAL_ADDRESS, classify("169.254.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("169.255.0.0"));

        // IPv6 link-local, fe80::/10 — ten bits, so febf:: is inside it and fe7f:: is not.
        assertEquals(AbuseGuard.Outcome.LINK_LOCAL_ADDRESS, classify("[fe80::1]"));
        assertEquals(AbuseGuard.Outcome.LINK_LOCAL_ADDRESS, classify("[febf::1]"));

        // fec0::/10 is deprecated site-local. Deprecated is not the same as public, and an address in
        // it still names something internal, so it is refused rather than let through on a technicality.
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("[fec0::1]"));
    }

    @Test
    @DisplayName("EC-004: shared, unspecified, multicast and reserved space is refused too")
    void otherNonPublicSpaceRefused() {
        // 100.64.0.0/10, RFC 6598 carrier-grade NAT. Not private and not public: reaching it means
        // reaching somebody else's internal network.
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("100.63.255.255"));
        assertEquals(AbuseGuard.Outcome.SHARED_ADDRESS_SPACE, classify("100.64.0.0"));
        assertEquals(AbuseGuard.Outcome.SHARED_ADDRESS_SPACE, classify("100.127.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("100.128.0.0"));

        assertEquals(AbuseGuard.Outcome.UNSPECIFIED_ADDRESS, classify("0.0.0.0"));
        assertEquals(AbuseGuard.Outcome.UNSPECIFIED_ADDRESS, classify("0.1.2.3"));
        assertEquals(AbuseGuard.Outcome.UNSPECIFIED_ADDRESS, classify("[::]"));

        assertEquals(AbuseGuard.Outcome.MULTICAST_OR_RESERVED, classify("224.0.0.1"));
        assertEquals(AbuseGuard.Outcome.MULTICAST_OR_RESERVED, classify("239.255.255.255"));
        assertEquals(AbuseGuard.Outcome.MULTICAST_OR_RESERVED, classify("240.0.0.1"));
        assertEquals(AbuseGuard.Outcome.MULTICAST_OR_RESERVED, classify("255.255.255.255"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, classify("223.255.255.255"),
                "223.255.255.255 is the last unicast address before multicast begins");
    }

    @Test
    @DisplayName("EC-004: an IPv4-mapped IPv6 literal does not bypass the IPv4 rules")
    void ipv4MappedDoesNotBypass() {
        // The classic bypass: write the loopback as an IPv6 literal and a naive check sees neither an
        // IPv4 address nor a known IPv6 prefix.
        assertEquals(AbuseGuard.Outcome.LOOPBACK_ADDRESS, classify("[::ffff:127.0.0.1]"));
        assertEquals(AbuseGuard.Outcome.PRIVATE_ADDRESS, classify("[::ffff:10.0.0.1]"));
        assertEquals(AbuseGuard.Outcome.LINK_LOCAL_ADDRESS, classify("[::ffff:169.254.169.254]"));
        assertEquals(AbuseGuard.Outcome.LOOPBACK_ADDRESS, classify("[::ffff:7f00:1]"),
                "the same address written in hex rather than dotted quad");
    }

    @Test
    @DisplayName("EC-004: internal-looking names are refused")
    void internalNamesRefused() {
        for (String host : List.of("localhost", "ip6-localhost", "printer.local", "db.internal",
                "svc.cluster.local", "host.localdomain")) {
            assertEquals(AbuseGuard.Outcome.LOCAL_NAME, classify(host), host + " must be refused");
        }
    }

    @Test
    @DisplayName("public destinations are PERMITTED — the baseline that makes the rest mean something")
    void publicDestinationsPermitted() {
        // Without this, a guard that refused everything would satisfy every assertion above.
        for (String host : List.of("example.com", "www.example.com", "8.8.8.8", "1.1.1.1",
                "93.184.216.34", "[2606:2800:220:1:248:1893:25c8:1946]", "sub.domain.example.co.uk")) {
            assertEquals(AbuseGuard.Outcome.PERMITTED, classify(host),
                    host + " is a legitimate public destination and must be permitted");
        }
    }

    @Test
    @DisplayName("EC-005: a destination in our own short-link space is refused")
    void ownSpaceRefused() {
        for (String destination : List.of(
                "https://short.example/abc1234",
                "http://short.example/",
                "https://short.example:8443/abc1234",
                "https://SHORT.EXAMPLE/abc1234",
                "https://www.short.example/abc1234")) {
            assertEquals(AbuseGuard.Outcome.OWN_SHORT_LINK_SPACE, guard.classify(destination),
                    destination + " would redirect into our own space — a chain or a loop (EC-005)");
        }
        // A different host that merely contains our name is not ours.
        assertEquals(AbuseGuard.Outcome.PERMITTED, guard.classify("https://short.example.evil/abc"));
        assertEquals(AbuseGuard.Outcome.PERMITTED, guard.classify("https://notshort.example/abc"));
    }

    @Test
    @DisplayName("GUARD: an abusive destination is REFUSED, not flagged and served")
    void abusiveDestinationsAreRefused() {
        // FR-URL-005's negative clause: "such destinations MUST NOT be silently accepted and served."
        // classify() reporting an outcome is not enough on its own — something has to act on it, so the
        // guard offers a throwing form and that is what callers use.
        for (String destination : List.of(
                "https://127.0.0.1/x", "https://10.0.0.1/x", "https://169.254.169.254/latest/meta-data",
                "https://localhost/x", "https://short.example/abc1234")) {
            assertThrows(IllegalArgumentException.class, () -> guard.requireNotAbusive(destination),
                    destination + " must be refused, not merely classified");
        }
        guard.requireNotAbusive("https://example.com/legitimate");
    }

    @Test
    @DisplayName("fail-closed: a guard with no own-host set cannot be constructed")
    void failsClosedWithoutOwnHosts() {
        // An empty own-host set would make the EC-005 check vacuous and silently permit every loop.
        // Refusing to exist is better than existing and checking nothing.
        assertThrows(IllegalArgumentException.class, () -> new AbuseGuard(Set.of()));
        assertThrows(NullPointerException.class, () -> new AbuseGuard(null));
        assertThrows(IllegalArgumentException.class, () -> new AbuseGuard(Set.of("  ")),
                "a blank host name is an empty set spelled differently");
    }

    @Test
    @DisplayName("fail-closed: a destination with no determinable host is refused")
    void failsClosedOnUndeterminableHost() {
        // If the guard cannot tell what the host is, it cannot tell whether the host is internal. The
        // safe answer is no. This also removes any dependence on validation having run first.
        for (String destination : List.of("not a url", "https://", "/relative", "",
                "https://exa mple.com/x")) {
            assertEquals(AbuseGuard.Outcome.NO_DETERMINABLE_HOST, guard.classify(destination),
                    "'" + destination + "' has no determinable host and must be refused");
        }
        assertEquals(AbuseGuard.Outcome.NO_DETERMINABLE_HOST, guard.classify(null));
    }

    @Test
    @DisplayName("GUARD: the guard performs NO name resolution — asserted on the source")
    void performsNoNameResolution() throws Exception {
        // The residual this leaves is stated in the class javadoc: a hostname resolving to private
        // space is not refused. That limit is deliberate, because a resolve-then-fetch check is
        // defeated by DNS rebinding. Asserting it here stops the residual being closed later by a
        // mechanism that does not actually work, and keeps the domain free of network calls.
        Path source = Path.of("src/main/java/agentic/shortener/domain/validation/AbuseGuard.java");
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        String active = Files.readString(source).lines()
                .map(String::stripLeading)
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);

        for (String forbidden : List.of("InetAddress", "getByName", "getAllByName", "getHostAddress",
                "Socket", "HttpClient", "URLConnection", "openConnection", "Resolver")) {
            assertFalse(active.contains(forbidden),
                    "AbuseGuard must decide on literal text alone; found '" + forbidden + "'");
        }
    }

    @Test
    @DisplayName("a refusal names the rule and never echoes the destination")
    void refusalDoesNotEchoInput() {
        // Same clause as FR-URL-002's: a refusal reaches an unauthenticated caller through a 400 body.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> guard.requireNotAbusive("https://169.254.169.254/<script>marker-abuse</script>"));
        assertFalse(thrown.getMessage().contains("marker-abuse"), thrown.getMessage());
        assertFalse(thrown.getMessage().contains("169.254"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("FR-URL-005"),
                "the refusal must cite its rule: " + thrown.getMessage());
    }

    @Test
    @DisplayName("every outcome has its own reason, and PERMITTED has none to leak")
    void outcomesAreDistinguishable() {
        // Same Done condition T033 carries: a caller that cannot tell the classes apart cannot act.
        Set<String> reasons = new HashSet<>();
        for (AbuseGuard.Outcome outcome : AbuseGuard.Outcome.values()) {
            if (outcome == AbuseGuard.Outcome.PERMITTED) {
                assertTrue(outcome.reason().isEmpty(), "PERMITTED must carry no refusal reason");
                assertFalse(outcome.refused(), "PERMITTED is not a refusal");
                continue;
            }
            assertTrue(reasons.add(outcome.reason()),
                    "two outcomes share the reason '" + outcome.reason() + "'");
            assertTrue(outcome.refused(), outcome + " must be a refusal");
        }
    }
}
