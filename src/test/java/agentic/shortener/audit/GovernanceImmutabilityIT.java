package agentic.shortener.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * Task T113 — immutability enforcement over the governance set. NFR-AUD-001. ADR-010.
 *
 * <p>Two halves, matching T113's own Validate clause exactly:
 *
 * <ol>
 *   <li><strong>Six tables × two operations = twelve rejections.</strong> Already proven, exhaustively, by
 *       {@code AuditImmutabilityIT.allSixGovernanceTablesAreAppendOnly} (T028) — this class does not
 *       duplicate that proof; it confirms the file it lives in still contains it, the same "cite, don't
 *       re-derive" discipline {@code UrlShortenerAcceptanceIT.us1PackagesDoNotImportOrchestration} uses
 *       for {@code DependencyDirectionTest}.
 *   <li><strong>No deletion, purge or archival path exists anywhere in the codebase</strong>
 *       (NFR-AUD-003, CR-017) — a source scan, new here, because a privilege grant proves the DATABASE
 *       refuses a delete; it says nothing about whether the APPLICATION ever tries to issue one for a
 *       governance table it should not.
 * </ol>
 */
@DisplayName("T113 — governance-set immutability: twelve DB rejections (cited) plus a source-level scan")
class GovernanceImmutabilityIT {

    private static final Path SRC_MAIN = Path.of("src/main/java");

    private static final Path AUDIT_IMMUTABILITY_IT =
            Path.of("src/test/java/agentic/shortener/audit/AuditImmutabilityIT.java");

    /**
     * The six governance tables (V3, T027) plus {@code redirect_event} — CR-017 retains it indefinitely
     * too, though ADR-010 scopes it out of "audit" proper. {@code run_lease} (T095) is DELIBERATELY not
     * in this list: it is transient coordination state, not audit trail, and releasing a lock is not the
     * "deletion, purge or archival" NFR-AUD-003 forbids.
     */
    private static final List<String> RETAINED_TABLES = List.of(
            "audit_record", "state_transition", "gate_decision", "failure_event",
            "policy_check_result", "compensation_record", "redirect_event");

    @Test
    @DisplayName("1/2: the twelve-rejection proof exists and still covers all six governance tables")
    void theTwelveRejectionProofExistsAndCoversAllSixTables() throws Exception {
        assertTrue(Files.exists(AUDIT_IMMUTABILITY_IT),
                "T028's own file must exist — this class cites it rather than re-deriving its proof");
        String body = Files.readString(AUDIT_IMMUTABILITY_IT);
        assertTrue(body.contains("allSixGovernanceTablesAreAppendOnly"),
                "and must still contain the test this class's own claim depends on");
        for (String table : List.of("audit_record", "state_transition", "gate_decision",
                "failure_event", "policy_check_result", "compensation_record")) {
            assertTrue(body.contains("\"" + table + "\""),
                    "AuditImmutabilityIT must still name '" + table + "' among the governance tables "
                            + "it proves append-only — a table silently dropped from that list is a "
                            + "silent narrowing of NFR-AUD-001");
        }
    }

    @Test
    @DisplayName("2/2: NFR-AUD-003 — no DELETE/TRUNCATE against a retained governance or analytics table")
    void noDeletionPurgeOrArchivalPathExistsAnywhere() throws Exception {
        // Case-insensitive: "delete from Audit_Record" would evade a case-sensitive scan for no reason
        // that matters — SQL keywords and identifiers are not case sensitive in practice here.
        Pattern deleteOrTruncate = Pattern.compile(
                "(?i)(DELETE\\s+FROM|TRUNCATE\\s+(TABLE\\s+)?)\\s*\"?(" + String.join("|", RETAINED_TABLES)
                        + ")\"?");

        List<String> findings = new ArrayList<>();
        for (Path source : mainSources()) {
            String body = Files.readString(source);
            Matcher matcher = deleteOrTruncate.matcher(stripComments(body));
            while (matcher.find()) {
                findings.add(source + ": " + matcher.group().strip());
            }
        }

        assertEquals(List.of(), findings,
                "NFR-AUD-003, CR-017: no DELETE or TRUNCATE may target a retained governance or "
                        + "analytics table anywhere in the codebase. Findings: " + findings);
    }

    @Test
    @DisplayName("the scan is falsifiable — it catches a planted DELETE against a retained table")
    void theScanIsFalsifiable() {
        // Run through the exact same extraction the real check uses, over text rather than a planted
        // file — writing a violation into src/main to prove a test works would leave one behind on any
        // failure, the same reasoning StageExecutorContractTest's own CN-010 falsifiability test gives.
        Pattern deleteOrTruncate = Pattern.compile(
                "(?i)(DELETE\\s+FROM|TRUNCATE\\s+(TABLE\\s+)?)\\s*\"?(" + String.join("|", RETAINED_TABLES)
                        + ")\"?");
        String planted = "String sql = \"DELETE FROM audit_record WHERE audit_record_id = ?\";";

        assertTrue(deleteOrTruncate.matcher(planted).find(),
                "the pattern must catch a planted violation, or the real scan above is unusable and "
                        + "would pass while proving nothing");

        // And a legitimate, real delete against a NON-retained table (run_lease releasing a lock, T095)
        // must NOT be flagged — the claim is scoped to governance/analytics data, not all data ever.
        String legitimate = "\"DELETE FROM run_lease WHERE run_id = ? AND holder_id = ?\"";
        assertTrue(deleteOrTruncate.matcher(legitimate).results().findAny().isEmpty(),
                "a delete against a table OUTSIDE the retained set must not be flagged — over-broad "
                        + "matching here would make the real scan noisy enough to get relaxed");
    }

    private static List<Path> mainSources() throws Exception {
        if (!Files.isDirectory(SRC_MAIN)) {
            return List.of();
        }
        try (Stream<Path> tree = Files.walk(SRC_MAIN)) {
            return tree.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** Block and line comments removed, so a comment mentioning DELETE is not a finding. */
    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
