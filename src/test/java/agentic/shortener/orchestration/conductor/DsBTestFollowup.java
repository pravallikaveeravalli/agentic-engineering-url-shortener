package agentic.shortener.orchestration.conductor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * CR-069 — DS-B's own hand-authored test-file follow-up. See {@link DsBLiveRun}'s own javadoc on {@code
 * s7WithTestBookkeepingFollowup} for why this exists as hand-authored, disclosed content rather than AI
 * output: the two sibling test files this feature legitimately makes stale (asserting the tier's own
 * absence) require real judgement to rewrite — which demonstration numbers to use, how to restructure a
 * before/after test — not a mechanically-derivable fact like CR-064's own path-count bump.
 *
 * <p><strong>Reads real branch content, never guesses a symbol.</strong> The one thing S7's own real,
 * live output decides that this class does not control is the new configuration property's own key name
 * (the security-gate context document itself only suggested one "or similarly named") — so this class reads
 * the real, live {@code application.yml} from the branch S7 actually produced and extracts whichever key
 * was actually added, rather than assuming a name in advance. Everything else these edits touch (HTTP status
 * codes, JSON body shapes, the tier name the requirement itself fixed as {@code "per-creator-aggregate"}) is
 * fixed by the requirement text, not by S7's own discretion.
 */
final class DsBTestFollowup {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    private DsBTestFollowup() {
    }

    static String changeSet(Path repoRoot, String branch) throws Exception {
        String applicationYml = show(repoRoot, branch, "src/main/resources/application.yml");
        String newKey = findNewRateLimitKey(applicationYml);

        ObjectNode root = JSON.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode files = root.putArray("files");

        addEdit(files, "src/test/java/agentic/shortener/delivery/ratelimit/RateLimiterTest.java",
                RATE_LIMITER_TEST_RETIRE_SEARCH, RATE_LIMITER_TEST_RETIRE_REPLACE);
        addEdit(files, "src/test/java/agentic/shortener/delivery/ratelimit/RateLimiterTest.java",
                RATE_LIMITER_TEST_DEFAULT_SEARCH,
                "        assertEquals(3000, rateLimit.path(\"" + newKey + "\").asInt(),\n"
                        + "                \"PVT-014: T136a's own aggregate redirect tier, 3000 requests "
                        + "per minute per creator\");");

        addEdit(files, "src/test/java/agentic/shortener/delivery/ratelimit/RateLimitIT.java",
                RATE_LIMIT_IT_PROPERTIES_SEARCH,
                "@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,\n"
                        + "        properties = {\n"
                        + "                \"shortener.ratelimit.creation-per-creator-per-minute=5\",\n"
                        + "                \"shortener.ratelimit.redirect-per-code-per-minute=4\",\n"
                        + "                \"shortener.ratelimit." + newKey + "=6\"\n"
                        + "        })");
        addEdit(files, "src/test/java/agentic/shortener/delivery/ratelimit/RateLimitIT.java",
                RATE_LIMIT_IT_BEFORE_STATE_SEARCH, RATE_LIMIT_IT_AFTER_STATE_REPLACE);

        return root.toString();
    }

    private static void addEdit(com.fasterxml.jackson.databind.node.ArrayNode files, String path,
                                String search, String replace) {
        ObjectNode file = files.addObject();
        file.put("path", path);
        file.put("action", "EDIT");
        ObjectNode edit = file.putArray("edits").addObject();
        edit.put("search", search);
        edit.put("replace", replace);
    }

    private static String findNewRateLimitKey(String applicationYml) throws Exception {
        JsonNode rateLimit = YAML.readTree(applicationYml).path("shortener").path("ratelimit");
        Iterator<Map.Entry<String, JsonNode>> fields = rateLimit.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!"enabled".equals(field.getKey()) && !"creation-per-creator-per-minute".equals(field.getKey())
                    && !"redirect-per-code-per-minute".equals(field.getKey())) {
                return field.getKey();
            }
        }
        throw new IllegalStateException("no new key found under shortener.ratelimit in the real, live "
                + "application.yml S7 produced -- expected one beyond the two existing tiers' own keys. "
                + "Real content:\n" + applicationYml);
    }

    private static String show(Path repoRoot, String branch, String path) throws Exception {
        ProcessRunner.Result result = ProcessRunner.run(
                java.util.List.of("git", "show", branch + ":" + path), repoRoot, Duration.ofSeconds(30));
        if (!result.succeeded()) {
            throw new IllegalStateException("could not read " + path + " from " + branch + ": "
                    + result.stderr());
        }
        return result.stdout();
    }

    private static final String RATE_LIMITER_TEST_RETIRE_SEARCH =
            "    @Test\n"
                    + "    @DisplayName(\"the aggregate tier is genuinely ABSENT, not half-built\")\n"
                    + "    void theAggregateTierIsNotPartiallyPresent() throws Exception {\n"
                    + "        // A half-built third tier would be worse than none: it would look present "
                    + "in a review and\n"
                    + "        // enforce nothing. The absence is asserted so that building it is a "
                    + "visible change that has to\n"
                    + "        // update this test and the register together.\n"
                    + "        try (java.util.stream.Stream<Path> sources =\n"
                    + "                     Files.walk(Path.of(\"src/main/java/agentic/shortener/delivery/"
                    + "ratelimit\"))) {\n"
                    + "            for (Path file : sources.filter(f -> f.toString().endsWith(\".java\"))."
                    + "toList()) {\n"
                    + "                String text = Files.readString(file).lines()\n"
                    + "                        .map(String::stripLeading)\n"
                    + "                        .filter(l -> !l.startsWith(\"*\") && !l.startsWith(\"//\") "
                    + "&& !l.startsWith(\"/*\"))\n"
                    + "                        .reduce(\"\", (a, b) -> a + \"\\n\" + b);\n"
                    + "                assertFalse(text.contains(\"PVT_014\") || "
                    + "text.contains(\"aggregatePerCreator\"),\n"
                    + "                        file + \" implements part of the deferred tier; if it is "
                    + "being built, T136a and \"\n"
                    + "                                + \"the baseline-omissions register must change "
                    + "with it\");\n"
                    + "            }\n"
                    + "        }\n"
                    + "    }";

    private static final String RATE_LIMITER_TEST_RETIRE_REPLACE =
            "    @Test\n"
                    + "    @DisplayName(\"T136a: the aggregate tier's own baseline-omissions entry is now "
                    + "CLOSED\")\n"
                    + "    void theAggregateTierIsNowBuiltAndDisclosedAsClosed() throws Exception {\n"
                    + "        // Replaces theAggregateTierIsNotPartiallyPresent, whose own premise (the "
                    + "tier is absent) T136a\n"
                    + "        // makes false on purpose. The register entry itself is the durable fact "
                    + "worth asserting here,\n"
                    + "        // not a specific class's own symbol names authored by a live AI dispatch "
                    + "this test must not\n"
                    + "        // assume in advance.\n"
                    + "        String text = Files.readString(Path.of(\"docs/delivery/"
                    + "baseline-omissions.md\"));\n"
                    + "        assertTrue(text.toLowerCase(java.util.Locale.ROOT).contains(\"status: "
                    + "closed\"),\n"
                    + "                \"T136a landed; entry 1 of the baseline-omissions register must say "
                    + "so\");\n"
                    + "    }";

    private static final String RATE_LIMITER_TEST_DEFAULT_SEARCH =
            "        assertTrue(rateLimit.path(\"aggregate-per-creator-per-minute\").isMissingNode(),\n"
                    + "                \"the deferred tier must have no configuration key: a key with no "
                    + "enforcement behind it \"\n"
                    + "                        + \"is worse than an absence, because it reads as a working "
                    + "control\");";

    private static final String RATE_LIMIT_IT_PROPERTIES_SEARCH =
            "@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,\n"
                    + "        properties = {\n"
                    + "                \"shortener.ratelimit.creation-per-creator-per-minute=5\",\n"
                    + "                \"shortener.ratelimit.redirect-per-code-per-minute=4\"\n"
                    + "        })";

    private static final String RATE_LIMIT_IT_BEFORE_STATE_SEARCH =
            "    @Test\n"
                    + "    @DisplayName(\"THE DEFERRED TIER'S BEFORE-STATE: traffic spread across links is "
                    + "NOT throttled\")\n"
                    + "    void aggregateTrafficPassesUnthrottled() throws Exception {\n"
                    + "        // This is DS-B's before-state, and it is deliberately a PASS. FR-URL-016's "
                    + "third tier — PVT-014,\n"
                    + "        // per creator aggregated across all their links — is not built. Traffic "
                    + "spread across several\n"
                    + "        // links, each individually inside the per-code limit, therefore goes "
                    + "through untouched.\n"
                    + "        //\n"
                    + "        // Recorded as an omission BEFORE this test existed:\n"
                    + "        //   docs/delivery/baseline-omissions.md, entry 1, closed by T136a.\n"
                    + "        //\n"
                    + "        // Read carefully: a green result here does NOT mean FR-URL-016 is "
                    + "satisfied. It means the gap the\n"
                    + "        // register describes is real and reproducible, which is what makes it a "
                    + "demonstrable before-state\n"
                    + "        // rather than a claim. T057's acceptance sweep records the same thing at "
                    + "the slice boundary.\n"
                    + "        List<String> codes = new ArrayList<>();\n"
                    + "        for (int i = 0; i < 4; i++) {\n"
                    + "            codes.add(JSON.readTree(create(\"https://example.com/t055/spread/\" + "
                    + "i).getBody())\n"
                    + "                    .get(\"shortCode\").asText());\n"
                    + "        }\n"
                    + "\n"
                    + "        // Three follows each: twelve in total, comfortably past any aggregate "
                    + "limit set below the sum of\n"
                    + "        // the per-code limits, and inside the per-code limit of four for every "
                    + "individual code.\n"
                    + "        int allowed = 0;\n"
                    + "        for (int round = 0; round < 3; round++) {\n"
                    + "            for (String code : codes) {\n"
                    + "                if (rest.getForEntity(url(\"/\" + code), String.class)\n"
                    + "                        .getStatusCode().value() == 307) {\n"
                    + "                    allowed++;\n"
                    + "                }\n"
                    + "            }\n"
                    + "        }\n"
                    + "\n"
                    + "        assertEquals(12, allowed,\n"
                    + "                \"every follow must succeed. If this starts failing, the aggregate "
                    + "tier has been built — \"\n"
                    + "                        + \"which is good, and means baseline-omissions.md entry 1 "
                    + "must be closed and \"\n"
                    + "                        + \"this test rewritten as the after-state (T136a)\");\n"
                    + "    }";

    private static final String RATE_LIMIT_IT_AFTER_STATE_REPLACE =
            "    @Test\n"
                    + "    @DisplayName(\"T136a AFTER-STATE: traffic spread across links IS throttled once "
                    + "the aggregate limit is hit\")\n"
                    + "    void aggregateTrafficIsThrottledAfterT136a() throws Exception {\n"
                    + "        // Was aggregateTrafficPassesUnthrottled, DS-B's own before-state\n"
                    + "        // (docs/delivery/baseline-omissions.md entry 1, closed by T136a). The "
                    + "aggregate limit is lowered\n"
                    + "        // to 6 for this class only (see the properties above), the same pattern "
                    + "already used for the\n"
                    + "        // other two tiers.\n"
                    + "        List<String> codes = new ArrayList<>();\n"
                    + "        for (int i = 0; i < 4; i++) {\n"
                    + "            codes.add(JSON.readTree(create(\"https://example.com/t055/spread/\" + "
                    + "i).getBody())\n"
                    + "                    .get(\"shortCode\").asText());\n"
                    + "        }\n"
                    + "\n"
                    + "        // Six follows, spread so no single code approaches its own per-code limit "
                    + "of four -- only the\n"
                    + "        // AGGREGATE tier, shared across all four codes by the same creator, can be "
                    + "what throttles the 7th.\n"
                    + "        int[] rotation = {0, 1, 2, 3, 0, 1};\n"
                    + "        for (int i = 0; i < 6; i++) {\n"
                    + "            assertEquals(307, rest.getForEntity(url(\"/\" + "
                    + "codes.get(rotation[i])), String.class)\n"
                    + "                    .getStatusCode().value(), \"follow \" + (i + 1) + \" of 6 is "
                    + "within the aggregate limit\");\n"
                    + "        }\n"
                    + "\n"
                    + "        ResponseEntity<String> throttled = rest.getForEntity(url(\"/\" + "
                    + "codes.get(2)), String.class);\n"
                    + "        assertEquals(429, throttled.getStatusCode().value(), throttled.getBody());\n"
                    + "        harness.assertConforms(\"/{shortCode}\", \"get\", 429, "
                    + "throttled.getBody());\n"
                    + "\n"
                    + "        JsonNode body = JSON.readTree(throttled.getBody());\n"
                    + "        assertEquals(\"RATE_LIMITED\", body.get(\"code\").asText());\n"
                    + "        assertTrue(body.get(\"detail\").asText().contains(\"per-creator-aggregate\"),"
                    + "\n"
                    + "                \"the follower must be told which tier: \" + body);\n"
                    + "        String text = String.valueOf(throttled.getBody());\n"
                    + "        assertFalse(text.contains(caller.creatorId().toString()),\n"
                    + "                \"the owning creator's id reached a public follower: \" + text);\n"
                    + "        assertFalse(text.toLowerCase(java.util.Locale.ROOT).contains(\"creator\"),\n"
                    + "                \"not even the word may reach a public follower: \" + text);\n"
                    + "    }";
}
