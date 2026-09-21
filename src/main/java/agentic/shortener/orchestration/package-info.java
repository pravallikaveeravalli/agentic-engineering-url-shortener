/**
 * The **control plane** — the governed twelve-stage engine. The application plane
 * MUST NOT import this package, and no executor within it may reference the
 * gate-decision path (CR-021, asserted by T014 and T063).
 *
 * <p>Created by task T008. Plane separation per ADR-006; dependency direction asserted by T014.
 */
package agentic.shortener.orchestration;
