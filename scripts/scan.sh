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

# --------------------------------------------------------------------------------------------
# Telemetry mode. Task T037, FR-URL-017 (non-waivable), EC-007.
#
# T037's Validate field asks for "T019 scan over captured telemetry", which the repository scan
# above cannot do: it uses `git grep`, so it only ever sees repository files, and captured telemetry
# lives under target/ which the repository scan deliberately excludes.
#
# Two differences from the repository scan, both deliberate:
#
#   1. It looks for CREDENTIALS IN A URL, which the repository patterns do not. They must not: a
#      `user:pass@host` string in a test fixture is legitimate and the repository is full of them.
#      In telemetry it is a leak.
#
#   2. It applies NO FIXTURE ALLOWANCE. A credential in a log is a leak whether or not somebody
#      labelled it a fixture, so there is nothing here that a marker can wave through.
TELEMETRY_PATTERNS=(
  '[A-Za-z][A-Za-z0-9+.-]*://[^/?#[:space:]@]+@'
  'crk_[A-Za-z0-9_-]{16,}'
  'AKIA[0-9A-Z]{16}'
  'BEGIN [A-Z ]*PRIVATE KEY'
)

# The redactor's own marker, neutralised before matching. `https://<redacted>@example.com` is the
# redaction WORKING, and on its first run this scan flagged every line the redactor had already
# cleaned -- a scanner that fails on its own success.
#
# This is not a fixture allowance by another name, and the difference matters. The marker replaces
# the ENTIRE userinfo component, so `https://<redacted>:realsecret@h` cannot occur: the redactor
# would have produced `https://<redacted>@h` and the secret is gone before this runs. The only text
# this hides is the literal string below, which is not a credential. The telemetry self-test proves
# both halves -- a real credential still matches, and a redacted line does not.
REDACTION_MARKER='<redacted>'

if [ "${1:-}" = "--telemetry" ]; then
  TARGET="${2:-target/telemetry}"
  if [ ! -d "${TARGET}" ]; then
    echo "FAIL: no captured telemetry at ${TARGET}." >&2
    echo "Run the integration tier first; a scan over nothing proves nothing (CN-005)." >&2
    exit 1
  fi
  if [ -z "$(find "${TARGET}" -type f -print -quit)" ]; then
    echo "FAIL: ${TARGET} is empty, so this scan would pass without reading anything." >&2
    exit 1
  fi

  echo "scanning captured telemetry under ${TARGET}..."
  TELEMETRY_FOUND=0
  for pattern in "${TELEMETRY_PATTERNS[@]}"; do
    # Matched, then the redaction marker is removed, then matched AGAIN. Re-testing the line rather
    # than dropping it keeps a line that carries both a redacted value and a real one.
    hits="$(grep -rnE "${pattern}" "${TARGET}" 2>/dev/null \
             | sed "s|://${REDACTION_MARKER}@|://|g" \
             | grep -E "${pattern}" || true)"
    if [ -n "${hits}" ]; then
      echo "${hits}"
      echo "  ^^ credential material in telemetry, matched: ${pattern}" >&2
      TELEMETRY_FOUND=1
    fi
  done

  if [ "${TELEMETRY_FOUND}" -ne 0 ]; then
    echo >&2
    echo "FAIL: FR-URL-017 is NON-WAIVABLE. This is a stop condition, not a defect to schedule." >&2
    exit 1
  fi
  echo "no credential material in captured telemetry"
  exit 0
fi

# The telemetry scan needs its own falsifiability proof, for the same reason the repository scan
# has one: a scanner that has never matched anything is not a control, it is a habit.
if [ "${1:-}" = "--telemetry-self-test" ]; then
  PROBE_DIR="${ROOT}/target/telemetry-self-test"
  SELF_TEST_STATUS=0

  # Probe 1: a real credential in a log line MUST be caught.
  rm -rf "${PROBE_DIR}"; mkdir -p "${PROBE_DIR}"
  printf 'INFO refused destination https://svc:s3cr3t@example.com/x (1 attempt)\n' \
    > "${PROBE_DIR}/planted.log"
  if "$0" --telemetry "${PROBE_DIR}" >/dev/null 2>&1; then
    echo "SELF-TEST FAILED: the telemetry scan did NOT match a planted credential." >&2
    echo "A clean telemetry scan would therefore prove nothing (T037, FR-URL-017)." >&2
    SELF_TEST_STATUS=1
  fi

  # Probe 2: an ALREADY-REDACTED line must NOT be flagged. Without this the marker exclusion could
  # be widened to anything at all and nothing would notice; with it, the exclusion is pinned to
  # exactly the redactor's output.
  rm -rf "${PROBE_DIR}"; mkdir -p "${PROBE_DIR}"
  printf 'INFO refused destination https://%s@example.com/x (1 attempt)\n' "${REDACTION_MARKER}" \
    > "${PROBE_DIR}/redacted.log"
  if ! "$0" --telemetry "${PROBE_DIR}" >/dev/null 2>&1; then
    echo "SELF-TEST FAILED: the telemetry scan flagged an already-redacted line." >&2
    echo "It would fail on its own success, and every clean run would be noise." >&2
    SELF_TEST_STATUS=1
  fi

  # Probe 3: a line carrying BOTH must still be caught. Re-testing after neutralising the marker is
  # what makes this work; dropping the whole line would have hidden the real one.
  rm -rf "${PROBE_DIR}"; mkdir -p "${PROBE_DIR}"
  printf 'INFO first=https://%s@a.example second=https://svc:s3cr3t@b.example\n' \
    "${REDACTION_MARKER}" > "${PROBE_DIR}/both.log"
  if "$0" --telemetry "${PROBE_DIR}" >/dev/null 2>&1; then
    echo "SELF-TEST FAILED: a real credential beside a redacted one was not caught." >&2
    SELF_TEST_STATUS=1
  fi

  rm -rf "${PROBE_DIR}"
  if [ "${SELF_TEST_STATUS}" -ne 0 ]; then
    exit 1
  fi
  echo "SELF-TEST PASSED: caught a planted credential, ignored a redacted line, caught both together."
  exit 0
fi

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
