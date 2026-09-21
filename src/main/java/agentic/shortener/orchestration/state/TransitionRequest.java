package agentic.shortener.orchestration.state;

/** STUB — task T079, shape only so the red phase is an assertion failure. */
public record TransitionRequest(StageState from, StageState to, int artifactCount,
                                boolean recordedGateDecision, int attemptsUsed, int attemptBound,
                                boolean retryVotes, RunState runState, boolean humanDecision,
                                boolean retentionExpiry, boolean effectErasable) {

    public static TransitionRequest of(StageState from, StageState to) {
        return new TransitionRequest(from, to, 0, false, 0, 3, false, RunState.RUNNING, false, false,
                true);
    }

    public TransitionRequest withArtifactCount(int count) {
        return new TransitionRequest(from, to, count, recordedGateDecision, attemptsUsed, attemptBound,
                retryVotes, runState, humanDecision, retentionExpiry, effectErasable);
    }

    public TransitionRequest withRecordedGateDecision(boolean recorded) {
        return new TransitionRequest(from, to, artifactCount, recorded, attemptsUsed, attemptBound,
                retryVotes, runState, humanDecision, retentionExpiry, effectErasable);
    }

    public TransitionRequest withAttemptsUsed(int used) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, used, attemptBound,
                retryVotes, runState, humanDecision, retentionExpiry, effectErasable);
    }

    public TransitionRequest withAttemptBound(int bound) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, attemptsUsed, bound,
                retryVotes, runState, humanDecision, retentionExpiry, effectErasable);
    }

    public TransitionRequest withRetryVotes(boolean votes) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, attemptsUsed,
                attemptBound, votes, runState, humanDecision, retentionExpiry, effectErasable);
    }

    public TransitionRequest withRunState(RunState state) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, attemptsUsed,
                attemptBound, retryVotes, state, humanDecision, retentionExpiry, effectErasable);
    }

    public TransitionRequest withHumanDecision(boolean decided) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, attemptsUsed,
                attemptBound, retryVotes, runState, decided, retentionExpiry, effectErasable);
    }

    public TransitionRequest withRetentionExpiry(boolean expired) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, attemptsUsed,
                attemptBound, retryVotes, runState, humanDecision, expired, effectErasable);
    }

    public TransitionRequest withEffectErasable(boolean erasable) {
        return new TransitionRequest(from, to, artifactCount, recordedGateDecision, attemptsUsed,
                attemptBound, retryVotes, runState, humanDecision, retentionExpiry, erasable);
    }
}
