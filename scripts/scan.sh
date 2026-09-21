#!/usr/bin/env bash
# Secret and dependency scanning. Task T019.
#
# NFR-SEC-002, NFR-SEC-004, POL-SEC-002, POL-SEC-003.
#
# ADR-013's requirement is that the scan MATCH A PLANTED CREDENTIAL rather than be assumed to work.
# `--self-test` does exactly that: it plants a synthetic creator key, runs the scan, and fails if the
# scan did not find it. A scanner that has never matched anything is not a control.
#
# Usage:
#   scripts/scan.sh              scan the repository, exit non-zero on any finding
#   scripts/scan.sh --self-test  prove the pattern matches a planted credential
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

# ADR-013: creator keys are opaque 256-bit values rendered with a `crk_` prefix. The prefix is what
# makes them greppable, which is a deliberate property of the format rather than an accident.
SECRET_PATTERNS=(
  'crk_[A-Za-z0-9_-]{16,}'
  'AKIA[0-9A-Z]{16}'
  'BEGIN [A-Z ]*PRIVATE KEY'
  'sk-ant-[A-Za-z0-9_-]{16,}'
)

# Paths that legitimately contain a pattern while carrying no secret: this script defines them, and
# red-phase evidence may quote a planted one.
EXCLUDE_RE='^(scripts/scan\.sh|docs/evidence/red-phase/|target/)'

# Values that SELF-IDENTIFY as test fixtures. Deliberately matched on the VALUE, never on the path.
#
# The distinction matters. A path exclusion for `src/test/**` would hide a real credential committed
# in a test file, which is a common way keys actually leak. A value allowance cannot do that: a real
# 256-bit key will not contain the literal string TESTONLY or SELFTESTONLY, because those are chosen
# by whoever writes the fixture and a real key is chosen by a CSPRNG.
#
# Why fixtures need the real `crk_` prefix at all: ADR-013 hashes the FULL presented string including
# the prefix, and CreatorCredentialTest asserts that a different prefix yields a different hash. A
# fixture using a fake prefix would not exercise the property being tested.
FIXTURE_RE='(TESTONLY|SELFTESTONLY|WRONGWRONGWRONG)'

scan() {
  local found=1   # 1 == nothing found, kept shell-conventional below
  for pattern in "${SECRET_PATTERNS[@]}"; do
    local hits
    # --untracked matters: without it git grep searches TRACKED files only, and the
    # self-test plant is untracked — so the self-test reported "no match" and declared the
    # scanner broken. It also matters in real use: a secret staged but not yet committed is
    # exactly what this should catch before it lands.
    hits="$(git grep -n -I --untracked -E "${pattern}" -- . 2>/dev/null \
             | grep -Ev "${EXCLUDE_RE}" \
             | grep -Ev "${FIXTURE_RE}" || true)"
    if [ -n "${hits}" ]; then
      echo "${hits}"
      echo "  ^^ matched secret pattern: ${pattern}" >&2
      found=0
    fi
  done
  return "${found}"
}

if [ "${1:-}" = "--self-test" ]; then
  PLANT="${ROOT}/scan-self-test-plant.txt"
  # A synthetic value in the real format, deliberately WITHOUT a fixture marker.
  #
  # This matters. The FIXTURE_RE allowance above skips values containing TESTONLY, and when
  # the plant carried that marker the self-test skipped its OWN plant and reported the
  # scanner broken -- fail-closed working correctly, and a real defect in the plant. The
  # plant needs no marker because it is transient: created here, scanned, and deleted before
  # this branch returns, so it is never a value a reader could mistake for a committed key.
  printf 'creator_key=crk_%s\n' "aQ7zR2mV9xL4bN6kD1sF8wT3yH5jC0pG" > "${PLANT}"
  echo "planted a synthetic creator key at scan-self-test-plant.txt"

  if scan >/dev/null 2>&1; then
    echo "SELF-TEST PASSED: the scan matched the planted credential, so the pattern works."
    rm -f "${PLANT}"
    echo "plant removed"
    exit 0
  fi

  echo "SELF-TEST FAILED: the scan did NOT match the planted credential." >&2
  echo "The pattern does not work, so a clean scan would prove nothing (ADR-013)." >&2
  rm -f "${PLANT}"
  exit 1
fi

echo "scanning for committed secrets..."
if scan; then
  echo "FAIL: secret pattern matched above. Nothing is committed until it is removed." >&2
  exit 1
fi
echo "no secret-pattern matches"

echo
echo "dependency inventory (POL-SEC-003):"
# Establishes the inventory a vulnerability feed will later be checked against. The feed itself is
# S10's dependency scan, a later task — said plainly rather than implying this does more than it does.
if "${ROOT}/scripts/build.sh" -q dependency:list \
      -DoutputFile="${ROOT}/target/dependency-list.txt" -DappendOutput=false >/dev/null 2>&1; then
  echo "  wrote target/dependency-list.txt"
else
  echo "  dependency:list did not complete; inventory not written" >&2
fi
