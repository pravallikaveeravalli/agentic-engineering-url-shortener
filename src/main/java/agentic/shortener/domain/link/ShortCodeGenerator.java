package agentic.shortener.domain.link;

import java.security.SecureRandom;

/**
 * Generates short codes. Task T032. FR-URL-006. ADR-007.
 *
 * <p>CSPRNG rather than a counter or a hash of the destination. A counter leaks how many links exist
 * and lets anyone enumerate them; a hash of the destination would create destination-based
 * deduplication, which CL-008 case 1 forbids at any scope, ever.
 *
 * <p>Seven characters from a 64-symbol alphabet is 2^42 of space. Collisions are therefore rare but
 * not impossible, which is exactly why ADR-007 pairs generation with the store's unique constraint and
 * bounded retry rather than asserting uniqueness here. This class makes no uniqueness claim.
 */
public final class ShortCodeGenerator {

    /** ADR-007's alphabet: unreserved URL characters only, so a code never needs escaping. */
    private static final char[] ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

    private static final int LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }
}
