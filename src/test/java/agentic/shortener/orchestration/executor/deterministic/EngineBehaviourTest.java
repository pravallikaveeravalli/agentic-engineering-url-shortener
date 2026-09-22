package agentic.shortener.orchestration.executor.deterministic;

import agentic.shortener.orchestration.executor.ProducedArtifact;
import agentic.shortener.orchestration.executor.StageExecutor;
import agentic.shortener.orchestration.executor.StageExecutorContract;
import agentic.shortener.orchestration.executor.StageInput;
import agentic.shortener.orchestration.executor.StageOutcome;
import agentic.shortener.orchestration.reliability.FailureCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static agentic.shortener.orchestration.executor.deterministic.DeterministicEnginesTest.assertDeterministic;
import static agentic.shortener.orchestration.executor.deterministic.DeterministicEnginesTest.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What each of the five engines actually does. Task T071. FR-ORC-029.
 *
 * <p>Each engine gets its own nested class, which is also where {@link StageExecutorContract} is applied to
 * it — {@code EveryExecutorHasAContractTest} looks for exactly that, so an engine added without one fails a
 * test rather than quietly skipping the contract.
 */
@DisplayName("T071 — engine behaviour, per engine")
class EngineBehaviourTest {

    // ==============================================================================================
    // S1 — ingestion
    // ==============================================================================================

    @Nested
    @DisplayName("IngestionEngine (S1)")
    class IngestionEngineTest extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new IngestionEngine();
        }

        @Override
        protected StageInput validInput() {
            return input("S1", 1, TestPorts.sampleArtifactsFor(1));
        }

        @Test
        @DisplayName("admits a requirement and normalizes it without changing what it says")
        void admitsAndNormalizes() {
            StageOutcome outcome = new IngestionEngine().execute(input("S1", 1,
                    Map.of("submission", "  Shorten URLs\r\n  with an expiry.  \n\n")));

            assertTrue(outcome.succeeded());
            ProducedArtifact intake = outcome.producedArtifacts().get(0);
            assertEquals("requirement.intake", intake.artifactKey());
            assertEquals("Shorten URLs\nwith an expiry.", intake.content(),
                    "line endings and surrounding whitespace normalized; the words untouched. "
                            + "Normalization MUST NOT discard, merge or silently reinterpret a submitted "
                            + "requirement");
            assertEquals(List.of("submission"), intake.derivedFrom());
        }

        @Test
        @DisplayName("blank or missing input is permanent, not retried")
        void blankInputIsPermanent() {
            for (Map<String, String> bad : List.of(Map.<String, String>of(),
                    Map.of("submission", "   \n  "))) {
                StageOutcome outcome = new IngestionEngine().execute(input("S1", 1, bad));
                assertFalse(outcome.succeeded(), "input was: " + bad);
                assertEquals(FailureCategory.INVALID_INPUT, outcome.failure().category());
                assertFalse(outcome.failure().executorProposesRetryable(),
                        "plan §3, S1: permanent on malformed input. Resubmitting the same blank text "
                                + "cannot change the answer");
            }
        }

        @Test
        @DisplayName("deterministic")
        void deterministic() {
            assertDeterministic(new IngestionEngine(), TestPorts.sampleArtifactsFor(1), "S1", 1);
        }
    }

    // ==============================================================================================
    // S8 — testing
    // ==============================================================================================

    @Nested
    @DisplayName("TestingEngine (S8)")
    class TestingEngineTest extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new TestingEngine(TestPorts.working().testSuiteRunner());
        }

        @Override
        protected StageInput validInput() {
            return input("S8", 8, TestPorts.sampleArtifactsFor(8));
        }

        @Test
        @DisplayName("a passing suite produces the results artifact, CR-065: real executedBehaviors too")
        void passingSuite() throws Exception {
            StageOutcome outcome = executorUnderTest().execute(validInput());
            assertTrue(outcome.succeeded());
            assertEquals("test-results", outcome.producedArtifacts().get(0).artifactKey());
            String content = outcome.producedArtifacts().get(0).content();
            assertTrue(content.contains("0 failures"));

            com.fasterxml.jackson.databind.JsonNode json =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
            assertEquals(42, json.get("total").asInt());
            assertEquals(0, json.get("failed").asInt());
            assertTrue(json.get("executedBehaviors").isArray());
            assertTrue(json.get("executedBehaviors").size() > 0,
                    "CR-065: a passing suite must report which real behaviours it verified, or S9's own "
                            + "drift guard can never accept any documentation at all");
        }

        @Test
        @DisplayName("a FAILING suite fails the stage permanently — it does not succeed with bad news")
        void failingSuiteFailsTheStage() {
            StageOutcome outcome = new TestingEngine(TestPorts.failingSuite().testSuiteRunner())
                    .execute(validInput());

            assertFalse(outcome.succeeded(),
                    "a stage that SUCCEEDED while tests failed would let S9 and S10 build on a broken "
                            + "change; plan §3 routes it back to S7 instead");
            assertEquals(FailureCategory.INVALID_INPUT, outcome.failure().category(),
                    "INVALID_INPUT, because what was offered is not acceptable and re-running the same "
                            + "branch cannot change the answer. Not INTERNAL: the defect is in the change "
                            + "under test, not in us");
            assertFalse(outcome.failure().executorProposesRetryable(),
                    "plan §3, S8: test failure = permanent");
            assertTrue(outcome.failure().detail().contains("3 failures"),
                    "the detail must carry the count, or the routing back to S7 arrives with nothing to "
                            + "act on: " + outcome.failure().detail());
        }

        @Test
        @DisplayName("a runner that cannot start is UNAVAILABLE, which IS retryable here")
        void unavailableRunnerIsRetryable() {
            StageOutcome outcome = new TestingEngine(TestPorts.unavailableSuite().testSuiteRunner())
                    .execute(validInput());

            assertEquals(FailureCategory.UNAVAILABLE, outcome.failure().category());
            assertTrue(outcome.failure().executorProposesRetryable(),
                    "S8's declared set is {UNAVAILABLE}; distinguishing this from a test failure is the "
                            + "whole reason the engine translates rather than failing generically");
        }

        @Test
        @DisplayName("deterministic")
        void deterministic() {
            assertDeterministic(new TestingEngine(TestPorts.working().testSuiteRunner()),
                    TestPorts.sampleArtifactsFor(8), "S8", 8);
        }
    }

    // ==============================================================================================
    // S10 — security and policy
    // ==============================================================================================

    @Nested
    @DisplayName("PolicyEvaluationEngine (S10)")
    class PolicyEvaluationEngineTest extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new PolicyEvaluationEngine(TestPorts.working().policyEvaluator());
        }

        @Override
        protected StageInput validInput() {
            return input("S10", 10, TestPorts.sampleArtifactsFor(10));
        }

        @Test
        @DisplayName("a clean evaluation produces the results artifact")
        void cleanEvaluation() {
            StageOutcome outcome = executorUnderTest().execute(validInput());
            assertTrue(outcome.succeeded());
            assertEquals("policy-results", outcome.producedArtifacts().get(0).artifactKey());
        }

        @Test
        @DisplayName("a BLOCKING verdict still produces the record, and still fails the stage")
        void blockingVerdictFailsTheStageAndKeepsTheRecord() {
            StageOutcome outcome = new PolicyEvaluationEngine(TestPorts.blockingPolicy().policyEvaluator())
                    .execute(validInput());

            assertFalse(outcome.succeeded(), "a mandatory FAIL blocks downstream progression");
            assertTrue(outcome.failure().detail().contains("SEC-002"),
                    "the verdict's content has to survive the failure, or the run holds a blocked stage "
                            + "and no record of what blocked it: " + outcome.failure().detail());
            assertFalse(outcome.failure().executorProposesRetryable(),
                    "verdicts must be repeatable; re-evaluating the same evidence gives the same answer");
        }

        @Test
        @DisplayName("deterministic")
        void deterministic() {
            assertDeterministic(new PolicyEvaluationEngine(TestPorts.working().policyEvaluator()),
                    TestPorts.sampleArtifactsFor(10), "S10", 10);
        }
    }

    // ==============================================================================================
    // S11 — release readiness
    // ==============================================================================================

    @Nested
    @DisplayName("ReleaseReadinessEngine (S11)")
    class ReleaseReadinessEngineTest extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new ReleaseReadinessEngine(TestPorts.working().readinessEvaluator());
        }

        @Override
        protected StageInput validInput() {
            return input("S11", 11, TestPorts.sampleArtifactsFor(11));
        }

        @Test
        @DisplayName("evaluation produces the report — and the report is NOT the decision")
        void evaluationProducesAReportNotADecision() {
            StageOutcome outcome = executorUnderTest().execute(validInput());

            assertTrue(outcome.succeeded());
            ProducedArtifact report = outcome.producedArtifacts().get(0);
            assertEquals("release-readiness", report.artifactKey());
            assertTrue(report.content().contains("recorded human decision"),
                    "plan §3, S11: deterministic evaluation THEN the release owner's recorded decision. A "
                            + "report that read as a decision would let the gate look already satisfied: "
                            + report.content());
        }

        @Test
        @DisplayName("an unmet condition fails the stage rather than reporting ready")
        void unmetConditionFails() {
            StageOutcome outcome = new ReleaseReadinessEngine(TestPorts.notReady().readinessEvaluator())
                    .execute(validInput());
            assertFalse(outcome.succeeded());
            assertTrue(outcome.failure().detail().contains("2 unmet"));
        }

        @Test
        @DisplayName("deterministic")
        void deterministic() {
            assertDeterministic(new ReleaseReadinessEngine(TestPorts.working().readinessEvaluator()),
                    TestPorts.sampleArtifactsFor(11), "S11", 11);
        }
    }

    // ==============================================================================================
    // S12 — final summary
    // ==============================================================================================

    @Nested
    @DisplayName("SummaryEngine (S12)")
    class SummaryEngineTest extends StageExecutorContract {

        @Override
        protected StageExecutor executorUnderTest() {
            return new SummaryEngine();
        }

        @Override
        protected StageInput validInput() {
            return input("S12", 12, TestPorts.sampleArtifactsFor(12));
        }

        @Test
        @DisplayName("every line of the summary traces to an input artifact — nothing is invented")
        void everyClaimTraceable() {
            Map<String, String> evidence = TestPorts.sampleArtifactsFor(12);
            StageOutcome outcome = new SummaryEngine().execute(input("S12", 12, evidence));

            assertTrue(outcome.succeeded());
            ProducedArtifact summary = outcome.producedArtifacts().get(0);

            // The guard in its checkable form. Every non-heading line must be a citation of an input's key
            // and its content — so "creativity is a liability here" is a property, not an instruction.
            List<String> uncited = summary.content().lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .filter(line -> evidence.keySet().stream().noneMatch(line::contains))
                    .toList();
            assertEquals(List.of(), uncited,
                    "S12 assembles from recorded evidence only; every claim must be traceable. Uncited: "
                            + uncited);

            assertEquals(evidence.keySet().stream().sorted().toList(),
                    summary.derivedFrom().stream().sorted().toList(),
                    "and the provenance must name every piece of evidence it drew on");
        }

        @Test
        @DisplayName("missing evidence is permanent — the summary is not written around the gap")
        void missingEvidenceIsPermanent() {
            StageOutcome outcome = new SummaryEngine().execute(input("S12", 12,
                    Map.of("test-results", "42 tests, 0 failures")));

            assertFalse(outcome.succeeded());
            assertEquals(FailureCategory.INVALID_INPUT, outcome.failure().category());
            assertFalse(outcome.failure().executorProposesRetryable(),
                    "plan §3, S12: missing evidence = permanent");
            assertTrue(outcome.failure().detail().contains("policy-results"),
                    "the detail names what is missing: " + outcome.failure().detail());
        }

        @Test
        @DisplayName("the order of the evidence map does not change the summary")
        void summaryIsOrderIndependent() {
            // A Map has no order, so an engine iterating it directly would produce a summary whose line
            // order varied with hashing — deterministic within a JVM run and not across them, which is the
            // worst kind: every test passes and two demonstration runs disagree.
            Map<String, String> evidence = TestPorts.sampleArtifactsFor(12);
            java.util.LinkedHashMap<String, String> reordered = new java.util.LinkedHashMap<>();
            evidence.keySet().stream().sorted(java.util.Comparator.reverseOrder())
                    .forEach(k -> reordered.put(k, evidence.get(k)));

            assertEquals(new SummaryEngine().execute(input("S12", 12, evidence)),
                    new SummaryEngine().execute(input("S12", 12, reordered)));
        }

        @Test
        @DisplayName("deterministic")
        void deterministic() {
            assertDeterministic(new SummaryEngine(), TestPorts.sampleArtifactsFor(12), "S12", 12);
        }
    }
}
