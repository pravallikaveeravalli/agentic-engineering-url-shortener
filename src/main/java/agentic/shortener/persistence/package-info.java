/**
 * Repository implementations against PostgreSQL 16 (ADR-002). Real store in tests;
 * mocks are prohibited where the property needs a real mechanism (ADR-011).
 *
 * <p>Created by task T008. Plane separation per ADR-006; dependency direction asserted by T014.
 */
package agentic.shortener.persistence;
