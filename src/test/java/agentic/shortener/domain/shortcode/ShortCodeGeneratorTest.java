package agentic.shortener.domain.shortcode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T038 — short-code generation. FR-URL-006, NFR-SCA-002. ADR-007. EC-008.
 *
 * <p><strong>This replaces an interim generator that claimed conformance it did not have.</strong> The
 * walking skeleton (T032) shipped {@code domain/link/ShortCodeGenerator} whose javadoc said "ADR-007's
 * alphabet" while using 64 characters including {@code 0}, {@code O}, {@code 1}, {@code l} and
 * {@code I} — precisely the five ADR-007 excludes. The length and the RNG were right; the alphabet
 * contradicted the accepted decision. So this is a replacement, not an addition, and the old file is
 * deleted rather than left beside it.
 *
 * <p><strong>What a test can and cannot establish here.</strong> ADR-007 §Validation is explicit that
 * <em>"enumeration resistance is argued, not measured … predictability is not something a test can
 * demonstrate the absence of."</em> So nothing below claims the codes are unpredictable. What is asserted
 * is T038's guard — that the <strong>source</strong> of randomness is a CSPRNG — and that no weaker one
 * can be substituted, which is the part that is actually checkable.
 *
 * <p>Fast tier: pure domain.
 */
@DisplayName("T038 short-code generation")
class ShortCodeGeneratorTest {

    /** The five ADR-007 names: no {@code 0}/{@code O}, no {@code 1}/{@code l}/{@code I}. */
    private static final List<Character> EXCLUDED = List.of('0', 'O', '1', 'l', 'I');

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    @DisplayName("the alphabet is exactly 57 confusable-free characters")
    void alphabetIsFiftySeven() {
        String alphabet = generator.alphabet();
        assertEquals(57, alphabet.length(),
                "62 alphanumerics less ADR-007's five exclusions is 57; got " + alphabet.length()
                        + ": " + alphabet);

        Set<Character> distinct = new HashSet<>();
        for (char c : alphabet.toCharArray()) {
            assertTrue(distinct.add(c), "the alphabet repeats '" + c + "', which skews the distribution");
        }
    }

    @Test
    @DisplayName("each excluded character is absent, and its surviving lookalike is present")
    void confusablesExcluded() {
        String alphabet = generator.alphabet();
        for (char excluded : EXCLUDED) {
            assertFalse(alphabet.indexOf(excluded) >= 0,
                    "'" + excluded + "' is visually confusable and ADR-007 excludes it");
        }

        // Asserted in both directions. Removing the whole confusable group would also pass the check
        // above while shrinking the keyspace and losing characters nobody asked to lose: 'o' and 'L'
        // and 'i' are not ambiguous once their partners are gone.
        for (char kept : List.of('o', 'L', 'i')) {
            assertTrue(alphabet.indexOf(kept) >= 0,
                    "'" + kept + "' is unambiguous once its lookalikes are excluded and must remain");
        }
    }

    @Test
    @DisplayName("EC-008: at most one member of each confusable group survives")
    void ec008HasADeterministicAnswer() {
        // EC-008 asks for a deterministic outcome when a code differs only by a confusable character.
        // The alphabet is what makes it deterministic: with only one member of each group issuable, a
        // code containing the others can never have been issued, so the answer is always not-found
        // rather than sometimes-another-link.
        String alphabet = generator.alphabet();
        for (List<Character> group : List.of(List.of('0', 'O', 'o'), List.of('1', 'l', 'I'))) {
            long present = group.stream().filter(c -> alphabet.indexOf(c) >= 0).count();
            assertTrue(present <= 1,
                    "confusable group " + group + " has " + present + " members in the alphabet; "
                            + "more than one makes EC-008's outcome a coin toss");
        }
    }

    @Test
    @DisplayName("codes are case-sensitive: both cases are issuable")
    void codesAreCaseSensitive() {
        // ADR-007: "Codes are case-sensitive." If the alphabet were single-case, EC-008's case clause
        // would be meaningless and the keyspace would fall from 57^7 to 31^7 — below PVT-005.
        String alphabet = generator.alphabet();
        assertTrue(alphabet.chars().anyMatch(Character::isUpperCase), "no upper-case characters");
        assertTrue(alphabet.chars().anyMatch(Character::isLowerCase), "no lower-case characters");
        assertTrue(alphabet.chars().anyMatch(Character::isDigit), "no digits");
    }

    @Test
    @DisplayName("every code is 7 characters drawn only from the alphabet")
    void lengthAndMembership() {
        String alphabet = generator.alphabet();
        assertEquals(7, generator.codeLength());
        for (int i = 0; i < 2000; i++) {
            String code = generator.next();
            assertEquals(7, code.length(), "wrong length: '" + code + "'");
            for (char c : code.toCharArray()) {
                assertTrue(alphabet.indexOf(c) >= 0,
                        "'" + c + "' is not in the alphabet but appeared in '" + code + "'");
            }
        }
    }

    @Test
    @DisplayName("every alphabet character is reachable — catches an off-by-one in the draw")
    void wholeAlphabetIsReachable() {
        // `nextInt(length - 1)` is the classic bug: it passes every membership check above and silently
        // makes the last character unissuable. Over 20,000 codes (140,000 draws) the chance a reachable
        // character never appears is vanishing, so a gap here means a real bound error.
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            for (char c : generator.next().toCharArray()) {
                seen.add(c);
            }
        }
        for (char c : generator.alphabet().toCharArray()) {
            assertTrue(seen.contains(c),
                    "'" + c + "' never appeared in 140,000 draws — it is probably unreachable");
        }
    }

    @Test
    @DisplayName("GUARD: the randomness source is a CSPRNG, by type and not by comment")
    void randomnessSourceIsSecure() throws Exception {
        // T038's guard: "a general-purpose PRNG makes codes predictable and the link space enumerable —
        // the test asserts the SECURE RNG." Asserted on the field's declared type, so it holds whatever
        // the constructor happens to do.
        boolean found = false;
        for (Field field : ShortCodeGenerator.class.getDeclaredFields()) {
            if (java.util.Random.class.isAssignableFrom(field.getType())) {
                assertEquals(SecureRandom.class, field.getType(),
                        "field '" + field.getName() + "' is a general-purpose PRNG; ADR-007 requires a "
                                + "cryptographically secure one");
                found = true;
            }
        }
        assertTrue(found, "no randomness field found at all — where do the codes come from?");
    }

    @Test
    @DisplayName("GUARD: a weak PRNG cannot be injected either")
    void weakRandomnessCannotBeInjected() {
        // A secure default with a constructor that accepts java.util.Random would be a secure default
        // and an insecure product. The seam ADR-007 needs for the forced-collision test is typed
        // SecureRandom, so stubbing stays possible and weakening does not.
        for (Constructor<?> constructor : ShortCodeGenerator.class.getDeclaredConstructors()) {
            for (Class<?> parameter : constructor.getParameterTypes()) {
                if (java.util.Random.class.isAssignableFrom(parameter)) {
                    assertEquals(SecureRandom.class, parameter,
                            "a constructor accepts " + parameter.getName()
                                    + "; only SecureRandom may be injected");
                }
            }
        }
    }

    @Test
    @DisplayName("GUARD: no weak randomness anywhere in the source")
    void sourceNamesNoWeakRandomness() throws Exception {
        Path source = Path.of("src/main/java/agentic/shortener/domain/shortcode/ShortCodeGenerator.java");
        assertTrue(Files.exists(source), "missing " + source.toAbsolutePath());
        // Comments are stripped: the class javadoc explains why a general-purpose PRNG is wrong, and
        // that explanation names the very things being forbidden. Scoping the check to active code is
        // the fix; this is the sixth time in this project that provenance prose has tripped an
        // absence assertion, and every time the answer has been to scope it rather than soften it.
        String active = Files.readString(source).lines()
                .map(String::stripLeading)
                .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .reduce("", (a, b) -> a + "\n" + b);

        for (String forbidden : List.of("Math.random", "ThreadLocalRandom", "new Random(",
                "java.util.Random", "nanoTime", "currentTimeMillis")) {
            assertFalse(active.contains(forbidden),
                    "active code names '" + forbidden + "', which is not a secure randomness source");
        }
    }

    @Test
    @DisplayName("the keyspace is three orders of magnitude above PVT-005")
    void keyspaceExceedsTarget() {
        // NFR-SCA-002 and PVT-005: at least 10^9 links before collision pressure. 57^7 is about
        // 1.95 x 10^12, which is where ADR-007's "three orders of magnitude" claim comes from. Asserted
        // rather than quoted, because a later change to alphabet or length would invalidate the claim
        // silently and this is the only place that would notice.
        long keyspace = generator.keyspace();
        assertEquals(1_954_897_493_193L, keyspace, "57^7");
        assertTrue(keyspace >= 1_000L * 1_000_000_000L,
                "the keyspace must be at least 1000x PVT-005's 10^9; got " + keyspace);
    }

    @Test
    @DisplayName("distinct instances do not produce the same sequence")
    void instancesAreIndependent() {
        // A generator seeded from a fixed value — or from the clock at class-load — would hand two
        // instances the same sequence. That is not a test of unpredictability; it is a test that no
        // shared or constant seed was introduced.
        ShortCodeGenerator other = new ShortCodeGenerator();
        Set<String> first = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            first.add(generator.next());
        }
        int overlap = 0;
        for (int i = 0; i < 200; i++) {
            if (first.contains(other.next())) {
                overlap++;
            }
        }
        assertEquals(0, overlap, "two independent generators produced overlapping codes");
    }

    @Test
    @DisplayName("the interim generator is GONE, not merely superseded")
    void theInterimGeneratorIsDeleted() {
        // Leaving the 64-character version in place would leave a second generator that satisfies
        // ADR-007's length and RNG clauses and violates its alphabet clause — and nothing would stop a
        // future wiring change from picking it.
        assertFalse(Files.exists(Path.of(
                        "src/main/java/agentic/shortener/domain/link/ShortCodeGenerator.java")),
                "the interim T032 generator must be deleted, not kept beside this one");
    }
}
