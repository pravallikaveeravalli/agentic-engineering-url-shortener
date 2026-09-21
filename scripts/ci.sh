#!/usr/bin/env bash
# CI-equivalent local script. plan §14 Slice 1.
#
# One command a reviewer can run to get the same verdict a pipeline would give. Slice 1's exit
# condition is that THIS runs green from a clean checkout.
#
# Ordered cheapest-first so a failure surfaces as early as possible:
#   1. compile             - the stack is buildable at ADR-001's Java 21
#   2. fast tier           - container-free unit, architecture and contract tests
#   3. secret self-test    - prove the scanner matches a planted credential
#   4. secret scan         - prove nothing is actually committed
#   5. integration tier    - real PostgreSQL 16 through Testcontainers
#   6. telemetry scan      - prove no credential reached what the application logged (T037)
#
# Steps 1-4 need no Docker. Steps 5 and 6 do, and are skipped together with a LOUD notice and a
# distinct exit code rather than silently, so a green result never implies more than it proved.
# Step 6 comes last because it reads what step 5 captured: there is no telemetry to scan until the
# integration tier has produced some, and scanning an empty directory would pass while proving
# nothing (CN-005).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"
FAILED=0

step() {
  echo
  echo "=============================================================="
  echo "  $1"
  echo "=============================================================="
}

step "1/6  compile (ADR-001 Java 21)"
./scripts/build.sh -q compile || FAILED=1

step "2/6  fast tier - container-free"
./scripts/build.sh -q test || FAILED=1

step "3/6  secret scanner self-test (ADR-013: must match a planted credential)"
./scripts/scan.sh --self-test || FAILED=1

step "4/6  secret scan over the repository"
./scripts/scan.sh || FAILED=1

step "5/6  integration tier - real PostgreSQL 16"
DOCKER_AVAILABLE=0
if docker info >/dev/null 2>&1; then
  DOCKER_AVAILABLE=1
  ./scripts/build.sh -q -DfailIfNoTests=false verify || FAILED=1
else
  echo "!! SKIPPED: no reachable Docker daemon."
  echo "!! The integration tier did NOT run. This result does not cover persistence,"
  echo "!! migrations, or anything needing a real store. Start Docker and re-run."
  if [ "${FAILED}" -eq 0 ]; then FAILED=2; fi
fi

step "6/6  telemetry scan - FR-URL-017, non-waivable (T037)"
if [ "${DOCKER_AVAILABLE}" -eq 1 ]; then
  ./scripts/scan.sh --telemetry-self-test || FAILED=1
  ./scripts/scan.sh --telemetry || FAILED=1
else
  echo "!! SKIPPED: the integration tier did not run, so no telemetry was captured."
  echo "!! Nothing here has been shown about what the application logs."
  if [ "${FAILED}" -eq 0 ]; then FAILED=2; fi
fi

echo
echo "=============================================================="
case "${FAILED}" in
  0) echo "  CI-EQUIVALENT: GREEN - every step ran and passed" ; exit 0 ;;
  2) echo "  CI-EQUIVALENT: INCOMPLETE - steps 1-4 passed; integration and telemetry skipped" ; exit 2 ;;
  *) echo "  CI-EQUIVALENT: FAILED - see the failing step above" ; exit 1 ;;
esac
