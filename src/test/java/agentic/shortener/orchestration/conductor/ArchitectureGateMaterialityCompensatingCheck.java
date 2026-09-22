package agentic.shortener.orchestration.conductor;

import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.executor.ai.ClaudeCodeCliStageAiProvider;
import agentic.shortener.orchestration.executor.ai.StageAiProvider;
import agentic.shortener.orchestration.executor.ai.stages.DesignAiExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CR-057's own required compensating check, PART 3: proves live, against the real model, that the
 * architecture gate's new conditional trigger did NOT go soft — DS-C's own real trusted-partner design must
 * still be classified as containing material design decisions. Scoped to ONE real live call to {@link
 * DesignAiExecutor} rather than a full S1&#8211;S6 pipeline run: {@code DsCClarificationRun}'s own full live
 * re-run (this same turn) genuinely re-confirmed S4's own ambiguity gate still fires on real, new
 * {@code MATERIAL_PENDING} findings, but stopped there honestly (two genuinely novel findings neither this
 * agent nor the owner had pre-answered — never forced past). This class instead feeds {@link
 * DesignAiExecutor} the REAL {@code tasks} output DS-C's own S5 stage already produced live, in an earlier
 * turn of this same engagement ({@code docs/evidence/ds-c/replan.json}'s own {@code s5RealResponse}, real,
 * not fabricated for this check) — the same real trusted-partner scenario, without re-deriving S1-S5 or
 * re-risking a fresh, novel S4 finding. NOT named {@code *Test}/{@code *IT}; run explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=ArchitectureGateMaterialityCompensatingCheck -DfailIfNoTests=false test</pre>
 */
class ArchitectureGateMaterialityCompensatingCheck {

    private static final String CLI = "claude";
    private static final String MODEL = "claude-sonnet-5";
    private static final ObjectMapper JSON = new ObjectMapper();

    // docs/evidence/ds-c/replan.json's own real, live s5RealResponse -- DS-C's own real trusted-partner
    // scenario's real decomposition, captured in an earlier turn of this same engagement. Reused verbatim
    // rather than re-derived, so this check tests exactly the ONE thing CR-057 changed (S6's own real
    // materiality classification) without re-risking a fresh, novel S3/S4 finding on the way there.
    private static final String REAL_DS_C_TASKS = "[{\"taskId\":\"T1\",\"requirementIds\":[\"1.1\"],"
            + "\"dependsOn\":[]},{\"taskId\":\"T2\",\"requirementIds\":[\"1.1\"],\"dependsOn\":[\"T1\"]},"
            + "{\"taskId\":\"T3\",\"requirementIds\":[\"1.2\"],\"dependsOn\":[]},"
            + "{\"taskId\":\"T4\",\"requirementIds\":[\"1.2\"],\"dependsOn\":[\"T3\"]},"
            + "{\"taskId\":\"T5\",\"requirementIds\":[\"1.3\"],\"dependsOn\":[]},"
            + "{\"taskId\":\"T6\",\"requirementIds\":[\"1.3\"],\"dependsOn\":[\"T2\",\"T5\"]}]";

    @Test
    void dsCsOwnRealTrustedPartnerDesignIsStillClassifiedMaterial() throws Exception {
        StageAiProvider provider = new ClaudeCodeCliStageAiProvider(CLI, MODEL);
        DesignAiExecutor s6 = new DesignAiExecutor(provider, Clock.systemUTC());

        StageInput input = new StageInput(UUID.randomUUID(), "S6", 6, 1,
                Map.of("tasks", REAL_DS_C_TASKS));

        StageOutcome outcome = s6.execute(input);

        assertTrue(outcome.succeeded(), "the real design call must succeed: "
                + (outcome.succeeded() ? "" : outcome.failure().detail()));
        String design = outcome.producedArtifacts().get(0).content();
        System.out.println("COMPENSATING CHECK: real live S6 design output:\n" + design);

        JsonNode root = JSON.readTree(design);
        JsonNode materialDecisions = root.get("materialDesignDecisions");
        assertTrue(materialDecisions != null && materialDecisions.isArray(),
                "materialDesignDecisions must be present and an array");
        assertFalse(materialDecisions.isEmpty(), "CR-057's own compensating check: DS-C's real "
                + "trusted-partner design (an authentication/authorization boundary, a real security-posture "
                + "choice) must still be classified as containing material design decisions -- an empty "
                + "result here would mean the new conditional gate went soft, and per the owner's own "
                + "instruction this is a STOP-and-report condition, not something to force past. Real "
                + "output: " + design);
    }
}
