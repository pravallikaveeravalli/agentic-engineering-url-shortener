package agentic.shortener.audit;

import java.time.Duration;

/**
 * The result of {@link MttrCalculator#calculate}. Tasks T118, T119.
 *
 * <p>{@code unrecoveredCount} exists on this type, not bolted on afterward, because T119 forbids folding
 * unrecovered failures into {@code mttr} — the mandated method excludes them from the denominator, and a
 * caller reading only {@code mttr} without also being handed the excluded count would see a flattering
 * number with no visible asterisk.
 *
 * @param mttr             the mean of {@code individualRecoveryDuration} over RECOVERED events only
 * @param recoveredCount   the denominator {@code mttr} was actually divided by
 * @param unrecoveredCount excluded from {@code mttr}, reported here so it is never silently dropped
 */
public record MttrResult(Duration mttr, int recoveredCount, int unrecoveredCount) {
}
