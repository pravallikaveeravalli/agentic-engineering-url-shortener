package agentic.shortener.delivery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T055a — the baseline-omissions register is complete and stays that way. FR-URL-016, PVT-014, CN-007.
 *
 * <p>T055a's Done condition has two parts, and the second is the one worth a test:
 * <strong>no entry without a named closing run</strong>. An entry that loses its closure stops being a
 * scheduled omission and becomes a gap — and a gap sitting in a register of planned work is worse than a
 * gap nobody wrote down, because it reads as owned.
 *
 * <p>The five fields are asserted per entry rather than once over the whole file. A register with two
 * entries where only the first is complete would pass a whole-file check and mislead every reader of the
 * second.
 *
 * <p>Fast tier: reads one document.
 */
@DisplayName("T055a baseline-omissions register")
class BaselineOmissionsTest {

    private static final Path REGISTER = Path.of("docs/delivery/baseline-omissions.md");

    /** Each entry's body, keyed by its heading. */
    private static List<String[]> entries() throws Exception {
        String text = Files.readString(REGISTER);
        Pattern heading = Pattern.compile("^## (Entry \\d+ — .*)$", Pattern.MULTILINE);
        Matcher matcher = heading.matcher(text);

        List<int[]> spans = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            titles.add(matcher.group(1));
            spans.add(new int[]{matcher.start(), matcher.end()});
        }

        List<String[]> found = new ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            int from = spans.get(i)[1];
            int to = i + 1 < spans.size() ? spans.get(i + 1)[0] : endOfEntries(text);
            found.add(new String[]{titles.get(i), text.substring(from, to)});
        }
        return found;
    }

    /** Entries end where the integrity section begins; that section is about the file, not an entry. */
    private static int endOfEntries(String text) {
        int integrity = text.indexOf("## Register integrity");
        return integrity < 0 ? text.length() : integrity;
    }

    @Test
    @DisplayName("the register exists and holds at least one entry")
    void registerExists() throws Exception {
        assertTrue(Files.exists(REGISTER), "missing " + REGISTER.toAbsolutePath());
        assertFalse(entries().isEmpty(),
                "a register with no entries would make T055's deferral undisclosed");
    }

    @Test
    @DisplayName("every entry names all five fields T055a requires")
    void everyEntryHasFiveFields() throws Exception {
        for (String[] entry : entries()) {
            String title = entry[0];
            String body = entry[1];

            assertTrue(body.contains("### 1. What is omitted"), title + " — missing field 1");
            assertTrue(body.contains("### 2. Which requirement it belongs to"),
                    title + " — missing field 2");
            assertTrue(body.contains("### 3. The requirement remains binding in full"),
                    title + " — missing field 3");
            assertTrue(body.contains("### 4. The run that closes it"), title + " — missing field 4");
            assertTrue(body.contains("### 5. What release readiness must report"),
                    title + " — missing field 5");
        }
    }

    @Test
    @DisplayName("DONE: no entry without a named closing run")
    void everyEntryNamesAClosingRun() throws Exception {
        // The condition that separates a scheduled omission from a gap. A task identifier is what makes
        // the closure nameable — "in a later slice" is not a run, and neither is "when we get to it".
        Pattern taskId = Pattern.compile("\\bT\\d{3}[a-z]?\\b");

        for (String[] entry : entries()) {
            String closing = section(entry[1], "### 4. The run that closes it");
            assertFalse(closing.isBlank(), entry[0] + " — the closing-run section is empty");
            assertTrue(taskId.matcher(closing).find(),
                    entry[0] + " — no task identifier names the run that closes it. An entry that has "
                            + "lost its closure is a GAP and must be reported as one, not kept here "
                            + "where it reads as planned work (T055a)");
        }
    }

    @Test
    @DisplayName("every entry states plainly that its requirement is still binding")
    void everyEntryStatesTheRequirementIsBinding() throws Exception {
        // The sentence that stops this register reading as a list of things the project decided it did
        // not have to do.
        for (String[] entry : entries()) {
            String binding = section(entry[1], "### 3. The requirement remains binding in full");
            assertTrue(binding.contains("binding in full"),
                    entry[0] + " — must say the requirement is binding in full");
            assertFalse(binding.toLowerCase(java.util.Locale.ROOT).contains("accepted risk"),
                    entry[0] + " — a scheduled omission is NOT an accepted risk. That would need the "
                            + "constitution's exception procedure: an approving authority, a "
                            + "compensating control, a residual-risk statement and an expiry");
        }
    }

    @Test
    @DisplayName("release readiness is told what to report, in refusable terms")
    void releaseReadinessReportingIsSpecific() throws Exception {
        for (String[] entry : entries()) {
            String reporting = section(entry[1], "### 5. What release readiness must report");
            assertTrue(reporting.contains("NOT MET"),
                    entry[0] + " — release readiness must be told to report the requirement NOT MET, "
                            + "not softened");
            // The softenings that would make the report useless are named so they cannot creep in.
            for (String softening : List.of("substantially met", "minor exception",
                    "met for the demonstration")) {
                assertTrue(reporting.contains(softening),
                    entry[0] + " — the entry should name '" + softening + "' as a phrasing that must "
                            + "NOT be used; that is what stops it being used");
            }
        }
    }

    @Test
    @DisplayName("the register distinguishes itself from the indefinite backlog")
    void registerIsNotABacklog() throws Exception {
        // T055a's guard: this MUST NOT become a general backlog. backlog.md holds items deferred
        // indefinitely; this holds scheduled omissions with closures. Conflating them would let an
        // unowned item inherit the credibility of an owned one.
        String text = Files.readString(REGISTER);
        assertTrue(text.contains("docs/delivery/backlog.md"),
                "the register must point at the backlog and say how it differs");
        assertTrue(Files.exists(Path.of("docs/delivery/backlog.md")),
                "and the file it distinguishes itself from must exist");
        assertTrue(text.contains("Gap"), "and it must name the third category: an entry that has lost "
                + "its closing run is neither, and is reported as a gap");
    }

    @Test
    @DisplayName("the entry count the register claims matches the entries it holds")
    void claimedCountMatches() throws Exception {
        // A register that miscounts itself is the first sign of one nobody reads.
        String text = Files.readString(REGISTER);
        Matcher claimed = Pattern.compile("Current entries: (\\d+)").matcher(text);
        assertTrue(claimed.find(), "the register should state how many entries it holds");
        assertEquals(entries().size(), Integer.parseInt(claimed.group(1)),
                "the register's own count disagrees with its entries");
    }

    /**
     * One section of an entry, with runs of whitespace collapsed to single spaces.
     *
     * <p>The collapsing is not tidiness. This is prose in a Markdown file, so it wraps — and the first
     * run of this test failed because {@code "met for the demonstration scope"} happened to break across
     * two lines. A phrase assertion against wrapped text is a line-width assertion in disguise, and it
     * fails on a reflow that changed nothing. This project has now hit that shape four times.
     */
    private static String section(String entryBody, String heading) {
        int from = entryBody.indexOf(heading);
        if (from < 0) {
            return "";
        }
        int next = entryBody.indexOf("### ", from + heading.length());
        String raw = entryBody.substring(from, next < 0 ? entryBody.length() : next);
        return raw.replaceAll("\\s+", " ");
    }
}
