#!/usr/bin/env bash
# Capture a failing test run as dated red-phase evidence. Task T020.
#
# NFR-TST-002 requires the red phase to be EVIDENCED, not asserted, and CN-004 forbids
# retrospectively describing implementation-first work as TDD. Every later RED-FIRST task depends on
# this script, which is why T020 is not parallel.
#
# The script is deliberately strict about one thing: it FAILS if the run it was asked to capture
# PASSED. A red-phase artifact recording a green run would be worse than no artifact — it would be
# evidence of a discipline that was not followed, filed as though it had been.
#
# Usage: scripts/record-red.sh <label> <maven args...>
#   e.g. scripts/record-red.sh DependencyDirectionTest -Dtest=DependencyDirectionTest test
set -uo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <label> <maven args...>" >&2
  exit 64
fi

LABEL="$1"; shift
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="${ROOT}/docs/evidence/red-phase"
OUT="${OUT_DIR}/${STAMP}-${LABEL}.txt"

mkdir -p "${OUT_DIR}"

echo "recording red phase for: ${LABEL}"
echo "command: ./mvnw $*"

# Run it, keeping both streams, and keep going on failure — failure is the point.
set +e
RUN_OUTPUT="$("${ROOT}/scripts/build.sh" "$@" 2>&1)"
RUN_EXIT=$?
set -e

{
  echo "# Red-phase evidence"
  echo
  echo "- **Label**: ${LABEL}"
  echo "- **Captured (UTC)**: ${STAMP}"
  echo "- **Command**: \`./mvnw $*\`"
  echo "- **Exit code**: ${RUN_EXIT}"
  echo "- **Requirement**: NFR-TST-002 (red phase evidenced, not asserted); CN-004"
  echo
  echo "## Why this file exists"
  echo
  echo "This is the failing run captured BEFORE the implementation that makes it pass. It is stored"
  echo "so that the red phase is readable by a reviewer rather than claimed in a commit message."
  echo
  echo "## Captured output"
  echo
  echo '```'
  echo "${RUN_OUTPUT}"
  echo '```'
} > "${OUT}"

if [ "${RUN_EXIT}" -eq 0 ]; then
  echo
  echo "FAIL: the run PASSED (exit 0), so there is no red phase to record." >&2
  echo "A red-phase artifact for a green run would misrepresent the discipline." >&2
  echo "Removing ${OUT}" >&2
  rm -f "${OUT}"
  exit 1
fi

echo
echo "red phase captured: ${OUT#"${ROOT}"/}"
echo "  exit code was ${RUN_EXIT} (non-zero, as required)"
