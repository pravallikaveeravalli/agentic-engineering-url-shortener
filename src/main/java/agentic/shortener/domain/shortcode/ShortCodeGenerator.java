package agentic.shortener.domain.shortcode;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Short-code generation. Task T038. FR-URL-006, NFR-SCA-002. ADR-007. EC-008.
 *
 * <p><strong>This replaces the interim generator the walking skeleton shipped.</strong> T032's
 * {@code domain.link.ShortCodeGenerator} had the right length and the right RNG, and its javadoc claimed
 * "ADR-007's alphabet" — while using 64 characters including {@code 0}, {@code O}, {@code 1}, {@code l}
 * and {@code I}, which are exactly the five ADR-007 excludes. The old file is deleted rather than left
 * beside this one, and a test asserts that it is gone: a second generator satisfying two of the ADR's
 * three clauses is something a future wiring change could pick up by accident.
 *
 * <p><strong>The alphabet.</strong> 62 alphanumerics less {@code 0}/{@code O} and {@code 1}/{@code l}/
 * {@code I} is 57 characters. The survivors {@code o}, {@code L} and {@code i} stay: once their
 * lookalikes are gone they are no longer ambiguous, and dropping whole groups would cost keyspace for
 * nothing. This is what gives EC-008 a deterministic answer — a code containing an excluded character
 * can never have been issued, so the response is always not-found rather than sometimes another link.
 *
 * <p><strong>Codes are case-sensitive</strong>, per ADR-007. A case-insensitive scheme would need a
 * single-case alphabet, which would cut the keyspace from 57⁷ to 31⁷ — below PVT-005's 10⁹ target.
 *
 * <p><strong>Seven characters gives 1,954,897,493,193 codes</strong>, about 2×10¹² and three orders of
 * magnitude above PVT-005. Collisions are therefore rare but not impossible, which is why ADR-007 pairs
 * generation with the store's unique constraint and a bounded retry. <strong>This class makes no
 * uniqueness claim.</strong>
 *
 * <p><strong>What is asserted and what is only argued.</strong> ADR-007 §Validation says enumeration
 * resistance is <em>argued, not measured</em> — predictability is not something a test can demonstrate
 * the absence of. So the tests assert the <em>source</em> of randomness, which is checkable, and claim
 * nothing about the output's unpredictability, which is not.
 *
 * <p>Pure domain: no framework, no persistence, no control-plane import.
 */
public final class ShortCodeGenerator {

    /**
     * ADR-007's alphabet. Also a subset of the unreserved URL characters, so a code never needs
     * escaping and a copied link cannot be mangled in transit.
     */
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final int LENGTH = 7;

    /**
     * Typed {@link SecureRandom} rather than {@link java.util.Random}, and deliberately so: this is the
     * seam ADR-007's forced-collision test needs, and typing it here means stubbing stays possible while
     * substituting a general-purpose PRNG does not compile.
     */
    private final SecureRandom random;

    public ShortCodeGenerator() {
        this(new SecureRandom());
    }

    public ShortCodeGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** A new code. No uniqueness claim — see the class note. */
    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** The issuable characters, exposed so EC-008's determinism is assertable rather than asserted. */
    public String alphabet() {
        return ALPHABET;
    }

    public int codeLength() {
        return LENGTH;
    }

    /** 57⁷. Computed rather than quoted, so a change to alphabet or length cannot leave a stale claim. */
    public long keyspace() {
        long total = 1L;
        for (int i = 0; i < LENGTH; i++) {
            total *= ALPHABET.length();
        }
        return total;
    }
}
