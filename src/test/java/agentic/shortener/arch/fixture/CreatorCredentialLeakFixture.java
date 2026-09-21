package agentic.shortener.arch.fixture;

import agentic.shortener.domain.creator.CreatorCredential;

/**
 * A DELIBERATE violation fixture. Task T063a.
 *
 * <p>Stands in for an orchestration, policy or audit class that has acquired a reference to the creator
 * credential type — exactly the leak CN-012 forbids. {@link agentic.shortener.arch.CredentialBoundaryTest}
 * asserts that its boundary rule reports a violation when pointed at this class.
 *
 * <p>Lives in test scope under {@code arch.fixture}, same as T015's own fixture and for the same two
 * reasons: {@code CredentialBoundaryTest}'s real rule imports production bytecode only, so this cannot make
 * it fail; and a reviewer reading the production tree will not find a planted violation sitting in it.
 */
public final class CreatorCredentialLeakFixture {

    /** Referenced so the compiler keeps the dependency rather than optimising an unused import away. */
    public String describe(CreatorCredential credential) {
        return credential.getClass().getSimpleName();
    }
}
