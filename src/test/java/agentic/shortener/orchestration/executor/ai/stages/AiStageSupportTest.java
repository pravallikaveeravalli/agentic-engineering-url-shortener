package agentic.shortener.orchestration.executor.ai.stages;

import agentic.shortener.orchestration.reliability.MalformedProviderOutputException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Task T073a-f's shared JSON extraction. FR-ORC-029.
 *
 * <p>{@link AiStageSupport#parseJson} tolerates a markdown fence anywhere in a model's answer, including
 * prose BEFORE it — a real live call for T073d's design adapter answered
 * {@code "Now I have enough context. Here is the design output. ```json {...} ```"}, which the original
 * (start-of-string-only) fence check refused as malformed even though the answer was perfectly usable. This
 * class pins that fixture as a permanent regression case, found live and fixed before it could recur.
 */
@DisplayName("T073a-f — the shared JSON extraction tolerates real model formatting habits")
class AiStageSupportTest {

    @Test
    @DisplayName("a bare JSON object, no fence, parses directly")
    void bareObjectParses() {
        JsonNode node = AiStageSupport.parseJson("{\"a\":1}");
        assertEquals(1, node.get("a").asInt());
    }

    @Test
    @DisplayName("a fence AT THE START of the answer is stripped")
    void fenceAtStartIsStripped() {
        JsonNode node = AiStageSupport.parseJson("```json\n{\"a\":1}\n```");
        assertEquals(1, node.get("a").asInt());
    }

    @Test
    @DisplayName("REGRESSION (found live, T073d): prose BEFORE a fenced answer is stripped too")
    void proseBeforeAFenceIsStripped() {
        JsonNode node = AiStageSupport.parseJson(
                "Now I have enough context. Here is the design output.  ```json {\"a\":1} ```");
        assertEquals(1, node.get("a").asInt());
    }

    @Test
    @DisplayName("prose before an UNFENCED object is stripped to the first JSON-opening character")
    void proseBeforeAnUnfencedObjectIsStripped() {
        JsonNode node = AiStageSupport.parseJson("Sure, here you go: {\"a\":1}");
        assertEquals(1, node.get("a").asInt());
    }

    @Test
    @DisplayName("prose before an UNFENCED array is stripped to the first JSON-opening character")
    void proseBeforeAnUnfencedArrayIsStripped() {
        JsonNode node = AiStageSupport.parseJson("Sure, here you go: [1,2,3]");
        assertEquals(3, node.size());
    }

    @Test
    @DisplayName("NEGATIVE: an answer with no JSON anywhere is refused, not silently emptied")
    void noJsonAnywhereIsRefused() {
        assertThrows(MalformedProviderOutputException.class,
                () -> AiStageSupport.parseJson("I could not complete this request."));
    }
}
