package agentic.shortener.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T106 — the change-request/impact-analysis model: eight required fields, and the detector {@code
 * POL-CHG-001} uses to find a contract change with no record. Constitution VI, {@code POL-CHG-001}.
 */
@DisplayName("T106 — ChangeRequest: eight fields required; a contract change with no record is found")
class ChangeRequestTest {

    private static ChangeRequest valid() {
        return new ChangeRequest("CR-999", "contracts/example.schema.json", "Pravallika Veeravalli",
                "MINOR — additive only", "no existing consumer's read is affected",
                List.of("PolicyEvaluationSchemaTest"), List.of("PolicyEvaluationSchemaTest"),
                "this record", List.of("schema updated", "conformance test green"), "APPROVED, 2026-09-21");
    }

    @Test
    @DisplayName("a fully-formed change request constructs")
    void fullyFormedChangeRequestConstructs() {
        assertEquals("CR-999", valid().id());
    }

    @Test
    @DisplayName("NEGATIVE: each of the eight required fields is rejected when missing")
    void eachRequiredFieldRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest(" ", "p", "o", "v", "c",
                List.of("x"), List.of("t"), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", " ", "o", "v", "c",
                List.of("x"), List.of("t"), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", " ", "v", "c",
                List.of("x"), List.of("t"), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", " ", "c",
                List.of("x"), List.of("t"), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", "v", " ",
                List.of("x"), List.of("t"), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", "v", "c",
                List.of(), List.of("t"), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", "v", "c",
                List.of("x"), List.of(), "d", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", "v", "c",
                List.of("x"), List.of("t"), " ", List.of("s"), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", "v", "c",
                List.of("x"), List.of("t"), "d", List.of(), "a"));
        assertThrows(IllegalArgumentException.class, () -> new ChangeRequest("id", "p", "o", "v", "c",
                List.of("x"), List.of("t"), "d", List.of("s"), " "));
    }

    @Test
    @DisplayName("POL-CHG-001's detector: a changed contract path with a matching record is not flagged")
    void changedArtifactWithRecordIsNotFlagged() {
        List<String> missing = ChangeRequest.changedArtifactsMissingARecord(
                List.of("contracts/example.schema.json"), List.of(valid()));
        assertTrue(missing.isEmpty());
    }

    @Test
    @DisplayName("POL-CHG-001's detector: a changed contract path with NO record is flagged by name")
    void changedArtifactWithNoRecordIsFlagged() {
        List<String> missing = ChangeRequest.changedArtifactsMissingARecord(
                List.of("contracts/example.schema.json", "contracts/unrecorded.schema.json"),
                List.of(valid()));
        assertEquals(List.of("contracts/unrecorded.schema.json"), missing);
    }
}
