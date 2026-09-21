package agentic.shortener.provisioning;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T053 — creator provisioning, the parts that need no store. FR-URL-019.
 *
 * <p>Two of T053's four assertions are about <em>absence</em>, and absence is what this file covers:
 * <strong>no HTTP key-issuance surface exists</strong>, and <strong>the application never writes key
 * material</strong>. The other two — that {@code never} yields a null expiry and that only the hash is
 * stored — need a real store and live in {@code ProvisionCreatorIT}.
 *
 * <p><strong>Why the absence test is the important one.</strong> An issuance endpoint would be an
 * unprotected surface whose only demonstration defence is that nobody has found it. FR-URL-019 fixes
 * provisioning at a local operator script because <em>the ability to run commands on the machine is the
 * trust boundary</em> — in the demonstration and in production alike. A test that only checked the script
 * works would leave that decision unenforced.
 *
 * <p>Fast tier: reads the script and the sources. No store, no container.
 */
@DisplayName("T053 provisioning: the absences")
class ProvisionCreatorTest {

    private static final Path SCRIPT = Path.of("ops/scripts/provision-creator.sh");

    @Test
    @DisplayName("the script exists and is executable")
    void scriptExists() throws Exception {
        assertTrue(Files.exists(SCRIPT), "missing " + SCRIPT.toAbsolutePath());
        assertTrue(Files.isExecutable(SCRIPT),
                "an operator script that has to be invoked through an interpreter is a trap: "
                        + SCRIPT.toAbsolutePath());
    }

    @Test
    @DisplayName("ABSENCE: no HTTP endpoint issues, creates or rotates key material")
    void noHttpIssuanceSurface() throws Exception {
        // Scans every request mapping in the application. An endpoint that minted a key would have to be
        // one of these, and the check is on the MAPPINGS rather than on a list of class names, so a new
        // controller is covered the day it is written.
        Pattern mapping = Pattern.compile(
                "@(Get|Post|Put|Patch|Delete|Request)Mapping\\s*\\(\\s*\"?([^\")]*)");
        List<String> suspicious = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                Matcher matcher = mapping.matcher(text);
                while (matcher.find()) {
                    String path = matcher.group(2).toLowerCase(java.util.Locale.ROOT);
                    for (String forbidden : List.of("creator", "credential", "key", "token", "provision",
                            "apikey", "rotate")) {
                        if (path.contains(forbidden)) {
                            suspicious.add(file.getFileName() + " maps " + matcher.group(2));
                        }
                    }
                }
            }
        }

        assertEquals(List.of(), suspicious,
                "these mappings look like a key-issuance surface: " + suspicious
                        + ". FR-URL-019 puts provisioning in an operator script, because the ability to "
                        + "run commands on the machine IS the trust boundary");
    }

    @Test
    @DisplayName("ABSENCE: the application generates no key material, at startup or ever")
    void applicationGeneratesNoKeys() throws Exception {
        // FR-URL-019's negative clause: keys MUST NOT be generated at application startup and emitted to
        // console or log streams. The application has no code that could produce one — it only ever
        // HASHES a key somebody else presented.
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(Path.of("src/main/java"))) {
            for (Path file : sources.filter(f -> f.toString().endsWith(".java")).toList()) {
                String active = Files.readString(file).lines()
                        .map(String::stripLeading)
                        .filter(l -> !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                        .reduce("", (a, b) -> a + "\n" + b);
                // A key is `crk_` plus random bytes. Producing one means writing that prefix.
                if (active.contains("\"crk_\"") || active.contains("crk_\" +")) {
                    offenders.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), offenders,
                "these files construct key material: " + offenders
                        + ". Only the operator script may do that (FR-URL-019)");
    }

    @Test
    @DisplayName("the script generates at least 256 CSPRNG bits and prefixes them")
    void keyStrengthAndFormat() throws Exception {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("openssl rand 32"),
                "32 bytes is 256 bits; FR-URL-019's evidence line requires at least that");
        assertTrue(script.contains("tr '+/' '-_'") && script.contains("tr -d '=\\n'"),
                "base64url, so the key is safe in a header and in a shell argument");
        assertTrue(script.contains("KEY=\"crk_${RANDOM_BYTES}\""),
                "the crk_ prefix is what makes a leaked key greppable, which scan.sh depends on");
        assertTrue(script.contains("-lt 43"),
                "a short generation must be refused rather than provisioned");
    }

    @Test
    @DisplayName("the script hashes the FULL presented string, prefix included")
    void hashCoversThePrefix() throws Exception {
        String script = Files.readString(SCRIPT);
        assertTrue(script.contains("printf '%s' \"${KEY}\" | shasum -a 256"),
                "the hash must cover ${KEY}, not ${RANDOM_BYTES} — the domain hashes the full string, "
                        + "so a hash over the suffix alone would never match (CreatorCredentialTest)");
        assertTrue(script.contains("-ne 64"), "a malformed digest must be refused, not stored");
    }

    @Test
    @DisplayName("--expires has NO DEFAULT anywhere in the script")
    void expiresHasNoDefault() throws Exception {
        String script = Files.readString(SCRIPT);

        assertTrue(script.contains("EXPIRES=\"\""),
                "the variable must start empty; a default value here would be a decision nobody made");
        assertFalse(script.contains("EXPIRES=\"${EXPIRES:-"),
                "no shell default substitution may supply an expiry (CR-002)");
        assertTrue(script.contains("--expires is required and has NO DEFAULT"),
                "and the refusal should say so in as many words");
    }

    @Test
    @DisplayName("only `never` produces a NULL expiry — no sentinel date in active code")
    void onlyNeverIsNull() throws Exception {
        String script = Files.readString(SCRIPT);
        assertTrue(script.contains("EXPIRES_SQL=\"NULL\""), "never must map to a real NULL");

        // ACTIVE lines only. The script's own header explains why a sentinel is wrong, and that
        // explanation contains the very string being forbidden — which is how this assertion first
        // failed, on the script it was written to defend. It is the seventh time in this project that
        // provenance prose has tripped an absence check, and the answer has been the same every time:
        // scope the assertion to active code rather than soften it.
        String active = activeShell(script);

        // A far-future sentinel would make "never" indistinguishable from "expires in 2999", which is
        // the distinction CR-002 exists to keep.
        for (String sentinel : List.of("9999-12-31", "2999", "9999")) {
            assertFalse(active.contains(sentinel),
                    "a sentinel date (" + sentinel + ") would erase the never/long-lived distinction");
        }
        assertFalse(active.contains("expires_at') ,"), "no placeholder expiry may be substituted");
    }

    /** Shell source with comment lines removed. */
    private static String activeShell(String script) {
        return script.lines()
                .filter(line -> !line.stripLeading().startsWith("#"))
                .reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("the key is printed once, and the script says it cannot be recovered")
    void keyIsShownOnceAndSaidToBeUnrecoverable() throws Exception {
        String script = Files.readString(SCRIPT);

        // Counted rather than eyeballed: the key appears in the banner and in the Bearer example, both
        // in one heredoc written once. More than one print path would mean more than one place a key
        // could end up.
        assertEquals(1, script.split("cat <<BANNER", -1).length - 1,
                "there must be exactly one output banner");
        assertTrue(script.contains("shown once"), "the operator must be told it is not repeatable");
        assertTrue(script.contains("cannot be recovered"),
                "and that losing it means provisioning another credential");
        assertTrue(script.contains("Only the SHA-256 hash"),
                "the script should state what is actually stored");
    }

    @Test
    @DisplayName("the script does not need a running application")
    void noApplicationDependency() throws Exception {
        String script = Files.readString(SCRIPT);
        // T053's Validate says store connectivity but NOT a running application. A script that called
        // the service would make provisioning depend on the thing provisioning exists to enable.
        for (String forbidden : List.of("curl", "wget", "localhost:8080", "/v1/links", "http://")) {
            assertFalse(script.contains(forbidden),
                    "the script must talk to the store, not to the service; found '" + forbidden + "'");
        }
        assertTrue(script.contains("PGHOST"), "it should use the standard PG* connection variables");
    }

    @Test
    @DisplayName("the generated key would be caught by the secret scanner")
    void aLeakedKeyIsGreppable() throws Exception {
        // The prefix is load-bearing rather than decorative: scan.sh's pattern is crk_ plus sixteen or
        // more key characters. A format change that dropped it would make every future leak invisible.
        String scanner = Files.readString(Path.of("scripts/scan.sh"));
        assertTrue(scanner.contains("crk_[A-Za-z0-9_-]{16,}"),
                "the scanner's pattern and the script's format must agree");

        String script = Files.readString(SCRIPT);
        assertTrue(script.contains("crk_"), "the script must produce that prefix");
        assertNotEquals(-1, script.indexOf("greppable"),
                "and should say why, so a later format change is a considered one");
    }
}
