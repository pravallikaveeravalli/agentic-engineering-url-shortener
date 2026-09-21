/**
 * The application plane's domain model. **Depends on nothing** — no framework, no
 * persistence, no delivery, and no control-plane package. Enforced by T014, and T015 proves
 * that enforcement can fail.
 *
 * <p>Created by task T008. Plane separation per ADR-006; dependency direction asserted by T014.
 */
package agentic.shortener.domain;
