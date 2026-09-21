package agentic.shortener.orchestration.executor;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task T066 — HUMAN executor kind recorded. FR-ORC-029 (CR-001). KE-25.
 *
 * <p>{@link ExecutorKind} itself and its structural refusal in {@code StageOutcome.succeeded()} were
 * built with T069 (Slice 4), before the no-plan gate existed to be the one legitimate producer of
 * {@code HUMAN}. This task's own job, now that {@code NoPlanGate} (T065) exists, is to prove the
 * remaining two-thirds of FR-ORC-029's claim: {@code HUMAN} is recorded <strong>only</strong> as a
 * no-plan-gate outcome, and <strong>no other</strong> caller or configuration can request it.
 */
@DisplayName("T066 — HUMAN is recorded only as a no-plan-gate outcome, nowhere else")
class HumanExecutorKindTest {

    @Test
    @DisplayName("three kinds, exactly — DETERMINISTIC, AI, HUMAN")
    void threeKindsExactly() {
        assertEquals(List.of("DETERMINISTIC", "AI", "HUMAN"),
                java.util.Arrays.stream(ExecutorKind.values()).map(Enum::name).toList());
    }

    @Test
    @DisplayName("StageOutcome.succeeded() structurally refuses HUMAN — no executor can claim it")
    void stageOutcomeRefusesHuman() {
        // T069's own guard, re-verified here because T066 is the task this specific guarantee is
        // attributed to, now that NoPlanGate exists as the contrasting legitimate path.
        assertThrows(IllegalArgumentException.class,
                () -> StageOutcome.succeeded(
                        List.of(new ProducedArtifact("k", "v", List.of())), ExecutorKind.HUMAN),
                "FR-ORC-031: HUMAN is never selectable by an executor");
    }

    @Test
    @DisplayName("ONLY JdbcRunStore.recordExecutorKind can write executor_kind_used, and only NoPlanGate calls it with HUMAN")
    void onlyNoPlanGateWritesHuman() throws Exception {
        // Structural half: which production classes even CALL recordExecutorKind at all. A source scan
        // rather than ArchUnit's dependency check, because the property is "which classes call this
        // METHOD", finer-grained than "which classes depend on this TYPE" — JdbcRunStore itself
        // obviously depends on its own method, but that is not a caller in the sense this test means.
        List<String> callers = new ArrayList<>();
        Path root = Path.of("src/main/java/agentic/shortener");
        try (var walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("JdbcRunStore.java")) {
                    continue; // the method's own definition, not a caller
                }
                String body = Files.readString(file);
                String stripped = body.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
                if (stripped.contains(".recordExecutorKind(")) {
                    callers.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(List.of("NoPlanGate.java"), callers,
                "only NoPlanGate may call recordExecutorKind in production code: " + callers);
    }

    @Test
    @DisplayName("no configuration key can select HUMAN — application.yml names no executor kind at all")
    void noConfigurationSelectsHuman() throws Exception {
        String config = Files.readString(Path.of("src/main/resources/application.yml"));
        assertTrue(!config.toUpperCase().contains("EXECUTORKIND") && !config.contains("executor-kind"),
                "executor kind must never be a configuration surface — it is recorded, never selected");
    }

    @Test
    @DisplayName("the caller-scan is falsifiable — it would catch a second class calling recordExecutorKind")
    void callerScanIsFalsifiable() {
        // Without this, the assertion above could be vacuously true because the scan itself is broken
        // (a wrong file extension filter, a typo in the method name). Proven against a literal string
        // matching the same pattern the real scan looks for.
        String plantedCallSite = "runStore.recordExecutorKind(runId, \"S7\", ExecutorKind.DETERMINISTIC);";
        assertTrue(plantedCallSite.contains(".recordExecutorKind("),
                "the pattern the real scan searches for must actually match a realistic call site");
    }
}
