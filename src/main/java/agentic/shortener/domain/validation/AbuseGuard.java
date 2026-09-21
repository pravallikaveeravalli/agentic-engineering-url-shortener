package agentic.shortener.domain.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Abuse and malicious-redirect controls. Task T036. FR-URL-005. EC-004, EC-005.
 *
 * <p><strong>FR-URL-005 allows two outcomes; this takes the stricter one.</strong> The requirement says
 * such destinations must be addressed <em>"either by refusal or by an explicitly recorded accepted
 * risk"</em>, and nothing upstream chooses between them. Refusal is chosen because it cannot weaken
 * anything and therefore needs nobody's approval, whereas accepting the risk would.
 *
 * <p><strong>No name resolution — a deliberate limit with a stated residual.</strong> This guard decides
 * on the destination's literal text. It never resolves a hostname, for two reasons. A resolve-then-fetch
 * check is defeated by DNS rebinding: the name can answer {@code 93.184.216.34} when checked and
 * {@code 169.254.169.254} when followed, so the check would buy confidence rather than safety. And a DNS
 * call inside the domain would make this class untestable without a network and would put an unbounded
 * wait on the creation path.
 *
 * <p>The residual is therefore explicit and is <em>not</em> claimed to be closed: <strong>a hostname that
 * resolves to private space is not refused here.</strong> The controls that do bear on it are the scheme
 * allow-list, the fact that this service never fetches a destination itself (so there is no server-side
 * request to forge), and the redirect being a client-side hop the browser applies its own rules to.
 * {@code AbuseGuardTest} asserts that this class performs no resolution, so the residual cannot later be
 * "closed" by a mechanism that does not work.
 *
 * <p><strong>Ranges are checked arithmetically, not by a library.</strong> Every boundary is tested at
 * both edges. {@code 172.16.0.0/12} is the one that is usually wrong: it ends at {@code 172.31.255.255},
 * not at {@code 172.32}, and a check written the obvious way either refuses public addresses or admits
 * thirty private networks.
 *
 * <p>Pure domain: no framework, no persistence, no network, no control-plane import.
 */
public final class AbuseGuard {

    /** The classification of one destination. Distinct reasons, so a caller can act on the answer. */
    public enum Outcome {

        PERMITTED(""),

        PRIVATE_ADDRESS("A destination must not target private network space (FR-URL-005, EC-004)."),

        LOOPBACK_ADDRESS("A destination must not target this host (FR-URL-005, EC-004)."),

        LINK_LOCAL_ADDRESS("A destination must not target link-local space (FR-URL-005, EC-004)."),

        SHARED_ADDRESS_SPACE(
                "A destination must not target shared carrier space (FR-URL-005, EC-004)."),

        UNSPECIFIED_ADDRESS(
                "A destination must not target the unspecified address (FR-URL-005, EC-004)."),

        MULTICAST_OR_RESERVED(
                "A destination must not target multicast or reserved space (FR-URL-005, EC-004)."),

        LOCAL_NAME("A destination must not name an internal host (FR-URL-005, EC-004)."),

        OWN_SHORT_LINK_SPACE(
                "A destination must not point back into this service's own links (FR-URL-005, EC-005)."),

        NO_DETERMINABLE_HOST(
                "A destination must name a host that can be checked (FR-URL-005).");

        private final String reason;

        Outcome(String reason) {
            this.reason = reason;
        }

        /** The caller-facing reason. Names the rule; never the input. */
        public String reason() {
            return reason;
        }

        public boolean refused() {
            return this != PERMITTED;
        }
    }

    /**
     * Host names that are internal by definition. {@code .local} is reserved by RFC 6762 and
     * {@code .internal} is reserved for private use, so neither can collide with a real public name.
     */
    private static final Set<String> LOCAL_NAMES = Set.of("localhost", "ip6-localhost", "ip6-loopback");

    private static final Set<String> LOCAL_SUFFIXES =
            Set.of(".localhost", ".local", ".internal", ".localdomain", ".home.arpa");

    private final Set<String> ownHosts;

    /**
     * @param ownHosts this service's own host names, lower-cased on entry
     * @throws IllegalArgumentException if empty — an empty set makes the EC-005 check vacuous, and a
     *                                  guard that silently checks nothing is worse than none at all
     */
    public AbuseGuard(Set<String> ownHosts) {
        Objects.requireNonNull(ownHosts, "ownHosts");
        Set<String> normalized = new LinkedHashSet<>();
        for (String host : ownHosts) {
            if (host != null && !host.isBlank()) {
                normalized.add(host.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one own host is required, or the EC-005 check would permit every loop");
        }
        this.ownHosts = Set.copyOf(normalized);
    }

    /** Classifies a destination. Total: any input, including {@code null}, produces an outcome. */
    public Outcome classify(String destination) {
        String host = hostOf(destination);
        if (host == null || host.isBlank()) {
            // Fail closed. If the host cannot be determined, whether it is internal cannot be
            // determined either, and the safe answer to an unanswerable question is no. This also
            // removes any dependence on validation having run first.
            return Outcome.NO_DETERMINABLE_HOST;
        }

        String lower = host.toLowerCase(Locale.ROOT);
        if (ownHosts.contains(lower)) {
            return Outcome.OWN_SHORT_LINK_SPACE;
        }

        if (lower.startsWith("[") && lower.endsWith("]")) {
            return classifyIpv6(lower.substring(1, lower.length() - 1));
        }

        int[] octets = parseIpv4(lower);
        if (octets != null) {
            return classifyIpv4(octets);
        }

        if (LOCAL_NAMES.contains(lower)) {
            return Outcome.LOCAL_NAME;
        }
        for (String suffix : LOCAL_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return Outcome.LOCAL_NAME;
            }
        }

        // A name this guard cannot classify. See the class note: the residual is that it is not
        // resolved, and it is permitted here rather than pretended about.
        return Outcome.PERMITTED;
    }

    /**
     * Refuses an abusive destination. FR-URL-005's negative clause is that such destinations must not be
     * <em>silently accepted and served</em>, so classification alone is not enough — this is the form
     * callers use.
     *
     * @throws IllegalArgumentException naming the rule, never the input
     */
    public void requireNotAbusive(String destination) {
        Outcome outcome = classify(destination);
        if (outcome.refused()) {
            throw new IllegalArgumentException(outcome.reason());
        }
    }

    private static String hostOf(String destination) {
        if (destination == null || destination.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(destination.strip());
            return uri.getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /** @return the four octets, or {@code null} when this is not a dotted-quad literal */
    private static int[] parseIpv4(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3) {
                return null;
            }
            for (int j = 0; j < parts[i].length(); j++) {
                if (!Character.isDigit(parts[i].charAt(j))) {
                    return null;
                }
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return null;
            }
        }
        return octets;
    }

    private static Outcome classifyIpv4(int[] o) {
        int a = o[0];
        int b = o[1];

        if (a == 0) {
            return Outcome.UNSPECIFIED_ADDRESS;                 // 0.0.0.0/8 "this network"
        }
        if (a == 127) {
            return Outcome.LOOPBACK_ADDRESS;                    // 127.0.0.0/8
        }
        if (a == 10) {
            return Outcome.PRIVATE_ADDRESS;                     // 10.0.0.0/8
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return Outcome.PRIVATE_ADDRESS;                     // 172.16.0.0/12 — ends at 172.31
        }
        if (a == 192 && b == 168) {
            return Outcome.PRIVATE_ADDRESS;                     // 192.168.0.0/16
        }
        if (a == 169 && b == 254) {
            return Outcome.LINK_LOCAL_ADDRESS;                  // 169.254.0.0/16, incl. metadata
        }
        if (a == 100 && b >= 64 && b <= 127) {
            return Outcome.SHARED_ADDRESS_SPACE;                // 100.64.0.0/10, RFC 6598
        }
        if (a >= 224) {
            return Outcome.MULTICAST_OR_RESERVED;               // 224.0.0.0/4 and 240.0.0.0/4
        }
        return Outcome.PERMITTED;
    }

    private static Outcome classifyIpv6(String address) {
        if (address.equals("::")) {
            return Outcome.UNSPECIFIED_ADDRESS;
        }
        if (address.equals("::1")) {
            return Outcome.LOOPBACK_ADDRESS;
        }

        // IPv4-mapped, ::ffff:0:0/96. Written either as ::ffff:127.0.0.1 or as ::ffff:7f00:1, and a
        // check that handles only one of the two spellings is a bypass rather than a control.
        String mapped = afterIpv4MappedPrefix(address);
        if (mapped != null) {
            int[] octets = parseIpv4(mapped);
            if (octets == null) {
                octets = parseHexPairAsIpv4(mapped);
            }
            if (octets != null) {
                return classifyIpv4(octets);
            }
        }

        int leading = leadingHextet(address);
        if (leading < 0) {
            return Outcome.PERMITTED;
        }
        if ((leading & 0xff00) == 0xff00) {
            return Outcome.MULTICAST_OR_RESERVED;               // ff00::/8
        }
        if ((leading & 0xffc0) == 0xfe80) {
            return Outcome.LINK_LOCAL_ADDRESS;                  // fe80::/10 — ten bits, not sixteen
        }
        if ((leading & 0xffc0) == 0xfec0) {
            // fec0::/10, deprecated site-local. Deprecated is not public: an address here still names
            // something internal, so it is refused rather than let through on a technicality.
            return Outcome.PRIVATE_ADDRESS;
        }
        if ((leading & 0xfe00) == 0xfc00) {
            return Outcome.PRIVATE_ADDRESS;                     // fc00::/7 unique local
        }
        return Outcome.PERMITTED;
    }

    private static String afterIpv4MappedPrefix(String address) {
        for (String prefix : new String[]{"::ffff:", "0:0:0:0:0:ffff:", "::"}) {
            if (address.startsWith(prefix) && address.length() > prefix.length()) {
                String rest = address.substring(prefix.length());
                if (prefix.equals("::") && !rest.contains(".")) {
                    continue;   // "::1" and friends are not IPv4-mapped
                }
                return rest;
            }
        }
        return null;
    }

    /** {@code 7f00:1} — the same address as {@code 127.0.0.1}, written as two hextets. */
    private static int[] parseHexPairAsIpv4(String rest) {
        String[] parts = rest.split(":", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            int high = Integer.parseInt(parts[0], 16);
            int low = Integer.parseInt(parts[1], 16);
            if (high < 0 || high > 0xffff || low < 0 || low > 0xffff) {
                return null;
            }
            return new int[]{high >> 8, high & 0xff, low >> 8, low & 0xff};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int leadingHextet(String address) {
        int colon = address.indexOf(':');
        String first = colon < 0 ? address : address.substring(0, colon);
        if (first.isEmpty() || first.length() > 4) {
            return -1;
        }
        try {
            return Integer.parseInt(first, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
