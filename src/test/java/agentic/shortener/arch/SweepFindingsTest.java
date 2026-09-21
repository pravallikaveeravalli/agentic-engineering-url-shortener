package agentic.shortener.arch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assertions the overclaim sweep found missing. Ordered by the owner at Gate 7.
 *
 * <p>The sweep asked, for every completed task: <em>does its validation assert everything the task's
 * Artifact field names?</em> Four clauses were true in fact but unasserted, which means a future change
 * could break them and nothing would fail. Each one is a separate test here, naming its task, so a
 * failure says which promise was broken rather than merely that something is wrong.
 *
 * <p>The fifth finding — T032's *"readiness degrades when the store is stopped"* — needed a real store
 * outage and lives in {@code ReadinessDegradationIT}.
 *
 * <p>Fast tier: reads files, starts nothing.
 */
@DisplayName("Sweep findings: Artifact clauses that were true but unasserted")
class SweepFindingsTest {

    private static final Path ROOT = Path.of(".");

    @Test
    @DisplayName("T009: docker-compose declares EXACTLY ONE service (ADR-012, store only)")
    void composeDeclaresOnlyTheStore() throws Exception {
        String compose = Files.readString(ROOT.resolve("docker-compose.yml"));

        // ADR-012 chose Compose for the store and the host for the app, so the debugger and the test
        // runner stay attached to the thing being graded. A full-stack profile is backlog item B6, and
        // a second service appearing here would adopt it silently.
        List<String> services = new ArrayList<>();
        boolean inServices = false;
        for (String line : compose.split("\n")) {
            if (line.startsWith("services:")) {
                inServices = true;
                continue;
            }
            if (inServices) {
                if (!line.startsWith(" ") && !line.isBlank()) {
                    inServices = false;   // a new top-level key ends the block
                    continue;
                }
                Matcher m = Pattern.compile("^  (\\w[\\w-]*):\\s*$").matcher(line);
                if (m.matches()) {
                    services.add(m.group(1));
                }
            }
        }

        assertEquals(List.of("db"), services,
                "ADR-012: Compose carries the store and nothing else. A full-stack profile is backlog "
                        + "item B6, not adopted. Declared services: " + services);
    }

    @Test
    @DisplayName("T017: no FAST-TIER test starts a Spring context")
    void fastTierCarriesNoFrameworkContext() throws Exception {
        // T017's Artifact says the fast tier has no container AND no framework context. The
        // no-container half was demonstrated by running it; this is the other half. A fast-tier test
        // that quietly added @SpringBootTest would add seconds to every red-green cycle, and a slow
        // loop gets abandoned — which is how TDD becomes a claim rather than a practice.
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> tests = Files.walk(ROOT.resolve("src/test/java"))) {
            for (Path p : tests.filter(f -> f.toString().endsWith(".java")).toList()) {
                String name = p.getFileName().toString();
                boolean integrationTier = name.endsWith("IT.java") || name.endsWith("IntegrationTest.java");
                if (integrationTier) {
                    continue;
                }
                // Matched at an ANNOTATION POSITION -- start of line, optionally indented -- not
                // anywhere in the file. This test's own assertion message names @SpringBootTest, and
                // a substring search flagged THIS FILE on its first run. That is the same defect
                // shape as the coverage denominator, the RedirectEvent field check and the
                // controller's message filter: a pattern broad enough to match the prose describing
                // it. The line-anchored form distinguishes a real annotation from a mention of one.
                String body = Files.readString(p);
                if (Pattern.compile("^\\s*@(SpringBootTest|WebMvcTest|DataJpaTest)\\b",
                        Pattern.MULTILINE).matcher(body).find()) {
                    offenders.add(name);
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "these fast-tier tests start a framework context, which T017 exists to prevent: "
                        + offenders + ". Rename them to *IT.java so Failsafe claims them, or remove "
                        + "the annotation.");
    }

    @Test
    @DisplayName("T019: the scan writes a dependency inventory (its Artifact names captured telemetry)")
    void scanProducesADependencyInventory() throws Exception {
        String scan = Files.readString(ROOT.resolve("scripts/scan.sh"));

        // T019's Artifact names "a runnable scan over the repository AND captured telemetry". Its
        // Validate field named only the planted-secret exit code, so the telemetry half was
        // unasserted. This asserts the mechanism exists and reports its outcome either way; the
        // inventory itself is build output and is gitignored, which is why the assertion is on the
        // script rather than on a committed file.
        assertTrue(scan.contains("dependency:list"),
                "the scan must produce a dependency inventory (POL-SEC-003)");
        assertTrue(scan.contains("dependency-list.txt"),
                "the inventory must be written somewhere a later vulnerability check can read");
        assertTrue(scan.contains("inventory not written"),
                "a failure to write the inventory must be REPORTED rather than passing silently — "
                        + "otherwise a clean scan could mean the inventory step never ran");
    }

    @Test
    @DisplayName("T021: ShortLink's created_at is immutable, asserted rather than assumed")
    void shortLinkCreatedAtIsImmutable() throws Exception {
        // T021's Artifact names created_at as immutable. The type has no setter, so it is immutable in
        // fact — but nothing asserted it, and a future setter would pass every existing test.
        var fields = agentic.shortener.domain.link.ShortLink.class.getDeclaredFields();
        for (var field : fields) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                    "ShortLink." + field.getName() + " must be final: the type's invariants are "
                            + "enforced at construction, and a mutable field would let a caller "
                            + "bypass them afterwards");
        }

        List<String> mutators = new ArrayList<>();
        for (var method : agentic.shortener.domain.link.ShortLink.class.getDeclaredMethods()) {
            if (method.getName().startsWith("set")) {
                mutators.add(method.getName());
            }
        }
        assertTrue(mutators.isEmpty(),
                "ShortLink must expose no setter; found " + mutators + ". The only permitted mutation "
                        + "is ACTIVE -> EXPIRED via expire(), which returns a new value");
    }

    @Test
    @DisplayName("T024: RedirectEvent is immutable too, for the same reason")
    void redirectEventIsImmutable() {
        for (var field : agentic.shortener.domain.analytics.RedirectEvent.class.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                    "RedirectEvent." + field.getName() + " must be final: an append-only record that "
                            + "can be edited in memory is not append-only");
        }
    }
}
