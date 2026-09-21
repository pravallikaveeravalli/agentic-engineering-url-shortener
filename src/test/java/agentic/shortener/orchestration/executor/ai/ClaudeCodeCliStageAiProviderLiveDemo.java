package agentic.shortener.orchestration.executor.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification-only: proves {@link ClaudeCodeCliStageAiProvider} can genuinely invoke a live, nested
 * {@code claude} CLI from a headless worker when done as this class already does it — a clean {@link
 * ProcessBuilder} argv invocation, no shell, no shell-redirect syntax — as opposed to an agent typing
 * {@code claude ...} directly into its own Bash tool, which is a different code path entirely and is what
 * the earlier "This command requires approval" block actually was. NOT named {@code *Test}/{@code *IT},
 * matching the Gemini live-demo classes: surefire/failsafe both skip it by pattern. Run explicitly:
 *
 * <pre>./scripts/build.sh -q -Dtest=ClaudeCodeCliStageAiProviderLiveDemo -DfailIfNoTests=false test</pre>
 */
class ClaudeCodeCliStageAiProviderLiveDemo {

    @Test
    @DisplayName("a real, nested claude CLI call succeeds via a clean ProcessBuilder invocation")
    void realNestedCallSucceeds() throws Exception {
        ClaudeCodeCliStageAiProvider provider =
                new ClaudeCodeCliStageAiProvider("claude", "claude-sonnet-5");

        AiResponse response = provider.invoke("Reply with exactly the word: pong");

        assertFalse(response.content().isBlank(), "expected a real, non-blank answer");
        assertTrue(response.content().toLowerCase().contains("pong"),
                "expected the model to follow the exact-word instruction, got: " + response.content());
        System.out.println("CLAUDE LIVE VERIFICATION: modelId=" + response.modelId());
        System.out.println("CLAUDE LIVE VERIFICATION: content=" + response.content());
    }
}
