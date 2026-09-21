package agentic.shortener.arch.fixture;

import org.springframework.stereotype.Component;

/**
 * A DELIBERATE violation fixture. Task T015.
 *
 * <p>This class exists to be caught. It is a stand-in for a domain class that has acquired a
 * framework dependency — exactly the drift NFR-MNT-001 forbids — and
 * {@link agentic.shortener.arch.DependencyDirectionFalsifiabilityTest} asserts that T014's rule
 * reports a violation when pointed at it.
 *
 * <p>It lives in <strong>test</strong> scope, under {@code arch.fixture}, for two reasons. T014
 * imports production bytecode only, so this cannot make the real rule fail; and a reviewer reading
 * the production tree will not find a planted violation sitting in it.
 *
 * <p>Why any of this is needed: ADR-006 chose a test-enforced rather than compiler-enforced plane
 * boundary, and the owner's condition for accepting that was proof the test can fail. Without this
 * fixture the boundary is a comment with a green tick beside it.
 */
@Component
public class FrameworkDependentDomainFixture {

    /** Referenced so the compiler keeps the framework dependency rather than optimising it away. */
    public String describe() {
        return Component.class.getSimpleName();
    }
}
