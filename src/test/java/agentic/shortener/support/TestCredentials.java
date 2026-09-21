package agentic.shortener.support;

import agentic.shortener.domain.creator.Creator;
import agentic.shortener.domain.creator.CreatorCredential;
import agentic.shortener.domain.creator.CreatorRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Provisions a creator and a usable credential for an integration test. Support for T052 onward.
 *
 * <p><strong>Why every integration test suddenly needs this.</strong> Before T052, creation was
 * anonymous and owned by a demonstration creator — a scaffold {@code LinkService} named as one.
 * FR-URL-018 says creation MUST NOT succeed anonymously, so T052 retires the scaffold, and every test
 * that creates a link now has to present a credential exactly as a caller would. That is the
 * requirement working rather than a testing inconvenience.
 *
 * <p><strong>The key is generated here, not hard-coded.</strong> A fixed literal in a test file is a
 * string that looks like a credential in a repository scan, and this project's scanner matches on the
 * value rather than the path precisely so a path could never hide one. A random key per run carries the
 * {@code TESTONLY} marker so the scanner can tell it from a real one, and authorises nothing anywhere.
 */
public final class TestCredentials {

    /**
     * @param creatorId the provisioned creator
     * @param header    the full {@code Authorization} header value a caller would send
     */
    public record Provisioned(UUID creatorId, String header) {
    }

    private TestCredentials() {
    }

    /** Provisions a creator with a credential usable for the next day. */
    public static Provisioned provision(CreatorRepository creators, Clock clock) {
        Instant now = clock.instant();
        UUID creatorId = UUID.randomUUID();
        creators.save(Creator.create(creatorId, "integration-test creator " + creatorId, now));

        // The TESTONLY marker is what lets scan.sh distinguish this from a real key by its VALUE. A
        // path-based exclusion would hide a genuine key committed in a test file, which is a common way
        // keys actually leak.
        String key = "crk_TESTONLY" + UUID.randomUUID().toString().replace("-", "");
        creators.save(CreatorCredential.fromPresentedKey(
                UUID.randomUUID(), creatorId, key, now, now.plus(1, ChronoUnit.DAYS)));

        return new Provisioned(creatorId, "Bearer " + key);
    }

    /** Provisions a credential that has already expired. For EC-041. */
    public static Provisioned provisionExpired(CreatorRepository creators, Clock clock) {
        Instant now = clock.instant();
        UUID creatorId = UUID.randomUUID();
        creators.save(Creator.create(creatorId, "expired-credential creator " + creatorId, now));

        String key = "crk_TESTONLYEXPIRED" + UUID.randomUUID().toString().replace("-", "");
        creators.save(CreatorCredential.fromPresentedKey(UUID.randomUUID(), creatorId, key,
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS)));

        return new Provisioned(creatorId, "Bearer " + key);
    }

    /** Provisions a credential that is unexpired but revoked. For EC-041's other half. */
    public static Provisioned provisionRevoked(CreatorRepository creators, Clock clock) {
        Instant now = clock.instant();
        UUID creatorId = UUID.randomUUID();
        creators.save(Creator.create(creatorId, "revoked-credential creator " + creatorId, now));

        String key = "crk_TESTONLYREVOKED" + UUID.randomUUID().toString().replace("-", "");
        creators.save(CreatorCredential.fromPresentedKey(UUID.randomUUID(), creatorId, key,
                        now.minus(2, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS))
                .revoke(now.minus(1, ChronoUnit.HOURS)));

        return new Provisioned(creatorId, "Bearer " + key);
    }
}
