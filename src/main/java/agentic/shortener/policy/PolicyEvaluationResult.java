package agentic.shortener.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * S10's full output — the shape {@code contracts/policy-evaluation.schema.json} validates. Task T099,
 * T100. FR-ORC-022.
 *
 * <p>{@code blocking} is never accepted from a caller — {@link #of} is the only public way to build one,
 * and it always computes {@code blocking} from {@code results} via {@link BlockingEnforcer}, evaluated at
 * {@code evaluatedAt} (EC-027: at use). A caller cannot construct an inconsistent pair.
 */
public record PolicyEvaluationResult(UUID runId, String policySetVersion, Instant evaluatedAt,
                                      List<PolicyCheckResult> results, boolean blocking) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public PolicyEvaluationResult {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(policySetVersion, "policySetVersion");
        if (policySetVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "a policy evaluation with no recorded version is invalid — T097's own Validate clause");
        }
        Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        Objects.requireNonNull(results, "results");
        if (results.isEmpty()) {
            throw new IllegalArgumentException("an evaluation with no results evaluated nothing");
        }
        results = List.copyOf(results);
    }

    public static PolicyEvaluationResult of(UUID runId, String policySetVersion, Instant evaluatedAt,
                                             List<PolicyCheckResult> results) {
        boolean blocking = BlockingEnforcer.isBlocking(results, evaluatedAt);
        return new PolicyEvaluationResult(runId, policySetVersion, evaluatedAt, results, blocking);
    }

    /** The shape {@code contracts/policy-evaluation.schema.json} validates. */
    public com.fasterxml.jackson.databind.JsonNode toJson() {
        ObjectNode node = JSON.createObjectNode();
        node.put("runId", runId.toString());
        node.put("policySetVersion", policySetVersion);
        node.put("evaluatedAt", evaluatedAt.toString());
        node.put("blocking", blocking);
        ArrayNode resultsNode = node.putArray("results");
        for (PolicyCheckResult result : results) {
            ObjectNode r = resultsNode.addObject();
            r.put("policyId", result.policyId());
            r.put("domain", result.domain());
            r.put("mandatory", result.mandatory());
            r.put("outcome", result.outcome().name());
            r.put("reason", result.reason());
            PolicyException exception = result.exception();
            if (exception != null) {
                ObjectNode e = r.putObject("exception");
                e.put("policyId", exception.policyId());
                e.put("clause", exception.clause());
                e.put("reason", exception.reason());
                e.put("scope", exception.scope());
                e.put("approvingAuthority", exception.approvingAuthority());
                e.put("compensatingControl", exception.compensatingControl());
                e.put("residualRisk", exception.residualRisk());
                e.put("approvedAt", exception.approvedAt().toString());
                e.put("expiresAt", exception.expiresAt().toString());
                e.put("repositoryRecordPath", exception.repositoryRecordPath());
            }
        }
        return node;
    }

    /** A short, human-readable summary — what {@link agentic.shortener.orchestration.executor.deterministic
     * .PolicyEvaluationEngine} carries into the produced artifact or the failure detail. */
    public String summary() {
        List<String> mandatoryFailIds = results.stream()
                .filter(r -> r.mandatory() && r.outcome() == PolicyOutcome.FAIL)
                .map(PolicyCheckResult::policyId).toList();
        long exceptionRequested = results.stream()
                .filter(r -> r.outcome() == PolicyOutcome.EXCEPTION_REQUESTED).count();
        return results.size() + " checks evaluated under " + policySetVersion + ", " + mandatoryFailIds.size()
                + " mandatory FAIL" + (mandatoryFailIds.isEmpty() ? "" : " (" + mandatoryFailIds + ")")
                + ", " + exceptionRequested + " EXCEPTION_REQUESTED, blocking=" + blocking;
    }
}
