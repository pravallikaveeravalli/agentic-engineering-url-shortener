package agentic.shortener.orchestration.reliability;

/**
 * The two git operations rollback needs. Task T089's port. FR-ORC-016, CL-004.
 *
 * <p>A port for the same reason S8's suite runner is one: {@link RollbackHandler}'s decisions — which effects
 * it will touch and which it refuses — are the part that must be right, and they should be testable without a
 * git repository. What the port removes from the handler is knowing how git is invoked.
 *
 * <p><strong>Two operations and no more.</strong> There is deliberately no {@code delete}, no {@code push} and
 * no {@code forcePush}: the only erasable class is local un-pushed work (CL-004), and an operation that could
 * reach a remote would make that claim false. The interface is the boundary of what rollback is able to do.
 */
public interface WorkingTree {

    /** Discards uncommitted changes at one path. Never a whole-tree discard — the path is required. */
    void discard(String path);

    /** Resets or deletes a local branch. Local only; nothing here has been pushed. */
    void resetBranch(String branchRef);
}
