package agentic.shortener.orchestration.conductor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs a subprocess with the same argv-not-shell, closed-stdin, concurrently-drained-pipes discipline
 * {@code GeminiCliStageAiProvider} already established for {@code agy} — reused here for {@code git} and
 * the build script, not re-invented. Task T131b/T131c.
 */
final class ProcessRunner {

    private ProcessRunner() {
    }

    record Result(int exitCode, String stdout, String stderr) {
        boolean succeeded() {
            return exitCode == 0;
        }
    }

    static Result run(List<String> argv, Path workingDir, Duration timeout) throws Exception {
        Objects.requireNonNull(argv, "argv");
        Objects.requireNonNull(workingDir, "workingDir");
        Objects.requireNonNull(timeout, "timeout");

        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.directory(workingDir.toFile());
        builder.redirectInput(new File("/dev/null"));

        Process process = builder.start();
        CompletableFuture<String> stdout = readFully(process.getInputStream());
        CompletableFuture<String> stderr = readFully(process.getErrorStream());

        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException(argv.get(0) + " did not exit within " + timeout.toSeconds() + "s");
        }

        return new Result(process.exitValue(), stdout.get(), stderr.get());
    }

    private static CompletableFuture<String> readFully(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(stream.readAllBytes());
            } catch (IOException e) {
                return "";
            }
        });
    }
}
