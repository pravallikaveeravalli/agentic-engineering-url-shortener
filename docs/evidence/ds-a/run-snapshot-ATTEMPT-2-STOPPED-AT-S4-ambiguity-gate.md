# DS-A live run snapshot

runId: c7e59dac-08aa-4744-805f-422886792769
runState: RUNNING

## Nodes

- S1 (stage 1, SINGLETON) -> SUCCEEDED, executorClass=DETERMINISTIC, attemptsUsed=0
- S2 (stage 2, SINGLETON) -> SUCCEEDED, executorClass=AI_CAPABLE, attemptsUsed=0
- S3 (stage 3, SINGLETON) -> SUCCEEDED, executorClass=AI_CAPABLE, attemptsUsed=0
- S4 (stage 4, SINGLETON) -> AWAITING_APPROVAL, executorClass=HUMAN_GATE, attemptsUsed=0
- S5 (stage 5, SINGLETON) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S6 (stage 6, SINGLETON) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S7 (stage 7, FAN_OUT_PARENT) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S7.join (stage 7, JOIN) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S8 (stage 8, SINGLETON) -> BLOCKED, executorClass=DETERMINISTIC, attemptsUsed=0
- S9 (stage 9, SINGLETON) -> BLOCKED, executorClass=AI_CAPABLE, attemptsUsed=0
- S10 (stage 10, SINGLETON) -> BLOCKED, executorClass=DETERMINISTIC, attemptsUsed=0
- S11 (stage 11, SINGLETON) -> BLOCKED, executorClass=HUMAN_GATE, attemptsUsed=0
- S12 (stage 12, SINGLETON) -> BLOCKED, executorClass=DETERMINISTIC, attemptsUsed=0

## Model ids actually used

- S3: claude-sonnet-5
- S2: claude-sonnet-5
