package agentic.shortener.policy;

import java.util.Map;

/**
 * The approved dependency list and their permitted licences, for {@code POL-DEP-001}/{@code POL-LIC-001}.
 * Task T098's twelve-definition set names both checks; this is their maintained reference data.
 *
 * <p><strong>Declared limitation</strong>: maintained by hand against {@code pom.xml}'s actual dependencies
 * at the time each was added, not sourced from a live SPDX/license-scanning feed — this project has none
 * wired. A dependency change that also changes its licence without this map being updated would pass
 * {@code POL-DEP-001} (still on the approved coordinate list) while silently drifting on {@code
 * POL-LIC-001}'s actual claim; disclosed here rather than implied.
 */
public final class ApprovedDependencies {

    private ApprovedDependencies() {
    }

    /** {@code groupId:artifactId} → SPDX licence identifier, for every dependency {@code pom.xml} declares. */
    public static final Map<String, String> APPROVED = Map.ofEntries(
            Map.entry("org.springframework.boot:spring-boot-starter-web", "Apache-2.0"),
            Map.entry("org.springframework.boot:spring-boot-starter-validation", "Apache-2.0"),
            Map.entry("org.springframework.boot:spring-boot-starter-data-jpa", "Apache-2.0"),
            Map.entry("org.springframework.boot:spring-boot-starter-actuator", "Apache-2.0"),
            Map.entry("org.postgresql:postgresql", "BSD-2-Clause"),
            Map.entry("org.flywaydb:flyway-core", "Apache-2.0"),
            Map.entry("org.flywaydb:flyway-database-postgresql", "Apache-2.0"),
            Map.entry("org.springframework.boot:spring-boot-starter-test", "Apache-2.0"),
            Map.entry("org.apache.httpcomponents.client5:httpclient5", "Apache-2.0"),
            Map.entry("org.testcontainers:postgresql", "MIT"),
            Map.entry("org.testcontainers:junit-jupiter", "MIT"),
            Map.entry("com.tngtech.archunit:archunit-junit5", "Apache-2.0"),
            Map.entry("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml", "Apache-2.0"),
            Map.entry("com.networknt:json-schema-validator", "Apache-2.0"));

    /** Every licence on {@link #APPROVED} — the permitted list {@code POL-LIC-001} checks against. */
    public static final java.util.Set<String> PERMITTED_LICENSES =
            java.util.Set.copyOf(APPROVED.values());
}
