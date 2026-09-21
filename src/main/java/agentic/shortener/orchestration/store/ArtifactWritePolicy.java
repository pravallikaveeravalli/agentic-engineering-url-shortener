package agentic.shortener.orchestration.store;

/**
 * What happens when two nodes write the same logical artifact. Task T076a. FR-ORC-003. EC-017.
 *
 * <p><strong>Declared per artifact class, not globally.</strong> A global setting would force one answer
 * on artifacts whose contributions mean opposite things: a design document several nodes legitimately
 * append to, and a policy verdict only one node may ever write.
 *
 * <p>What neither policy permits is both writes appearing to succeed with one silently discarded. That
 * is last-write-wins, and it leaves the run holding an artifact neither author recognises.
 */
public enum ArtifactWritePolicy {

    /**
     * The second write lands <em>behind</em> the first, as a new version. Correct where both
     * contributions matter.
     */
    SERIALIZE,

    /**
     * The second write is refused and the refusal is recorded with the losing node named. Correct where
     * a second author means somebody has misunderstood the topology — and silence would hide that.
     */
    REJECT_SECOND
}
