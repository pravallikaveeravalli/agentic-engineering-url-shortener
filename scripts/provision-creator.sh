#!/usr/bin/env bash
# Provision a creator and one credential. Task T053. FR-URL-019.
#
# WHY A SCRIPT AND NOT AN ENDPOINT
#
# FR-URL-019 fixes this at Gate 2: creator identities are provisioned by a local operator script, not
# by HTTP. The ability to run commands on the machine IS the trust boundary, in the demonstration and
# in production alike. An HTTP issuance endpoint would create an unprotected surface whose only
# defence would be that nobody has found it. `ProvisionCreatorTest` asserts no such endpoint exists.
#
# WHY THE KEY IS PRINTED HERE AND NOWHERE ELSE
#
# Operator-invoked terminal output is not application logging. The distinction FR-URL-019 draws is
# that the APPLICATION never writes key material — not that a human may never see the key they just
# asked for. It is printed once, to this terminal, and only the hash is stored. There is no way to
# recover it afterwards, which is the point.
#
# WHY --expires HAS NO DEFAULT
#
# CR-002. A credential's lifetime must be a decision, never a forgotten field. A default of "90 days"
# would be a decision nobody made, and a default of "never" would be worse. Passing the literal
# `never` is the only route to a non-expiring credential, and it has to be typed.
#
# Usage:
#   scripts/provision-creator.sh --name "<name>" --expires <Nd|Nh|never>
#
# Store connection comes from the standard PG* variables. A running application is NOT required —
# this talks to the database, not to the service.
#   PGHOST (default localhost)  PGPORT (default 5432)
#   PGDATABASE (default shortener)  PGUSER (default shortener)  PGPASSWORD
#
# PSQL may name an alternative psql invocation, which is how the integration test reaches a
# container-hosted store on a machine with no psql installed.
set -uo pipefail

NAME=""
EXPIRES=""          # deliberately empty: there is no default (CR-002)
                    #
                    # Proved, not asserted. Substituting "${EXPIRES:-90d}" here was captured as red
                    # evidence twice (20260921T101415Z and ...101427Z): the source check caught the
                    # default, and the behavioural test caught the script provisioning a credential
                    # nobody had chosen a lifetime for.
EMIT_SQL_ONLY=0

usage() {
  cat >&2 <<'USAGE'
usage: provision-creator.sh --name "<name>" --expires <Nd|Nh|never>

  --name     the creator's display name (required)
  --expires  credential lifetime: a duration such as 90d or 24h, or the literal `never`.
             REQUIRED. There is no default — a credential's lifetime must be a decision (CR-002).
  --emit-sql print the statements instead of executing them; generates the key and hash as usual
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --name)      NAME="${2:-}"; shift 2 || true ;;
    --expires)   EXPIRES="${2:-}"; shift 2 || true ;;
    --emit-sql)  EMIT_SQL_ONLY=1; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           echo "unknown argument: $1" >&2; usage; exit 64 ;;
  esac
done

if [ -z "${NAME}" ]; then
  echo "FAIL: --name is required." >&2
  usage
  exit 64
fi

# The refusal FR-URL-019 names explicitly. Non-zero exit, nothing generated, nothing stored.
if [ -z "${EXPIRES}" ]; then
  echo "FAIL: --expires is required and has NO DEFAULT." >&2
  echo "A credential's lifetime must be an explicit decision (FR-URL-019, CR-002)." >&2
  echo "Pass a duration such as 90d or 24h, or the literal 'never'." >&2
  exit 64
fi

# --------------------------------------------------------------------------------------------------
# Expiry
# --------------------------------------------------------------------------------------------------
# `never` yields a NULL expires_at and NOTHING ELSE DOES. A far-future sentinel would make "never"
# indistinguishable from "expires in 2999", which is the distinction CR-002 exists to keep.
EXPIRES_SQL=""
case "${EXPIRES}" in
  never)
    EXPIRES_SQL="NULL"
    ;;
  *[0-9]d)
    EXPIRES_SQL="now() + interval '${EXPIRES%d} days'"
    ;;
  *[0-9]h)
    EXPIRES_SQL="now() + interval '${EXPIRES%h} hours'"
    ;;
  *)
    echo "FAIL: --expires must be <N>d, <N>h, or the literal 'never'; got '${EXPIRES}'." >&2
    exit 64
    ;;
esac

# Reject a non-numeric duration that slipped past the glob, e.g. "9x9d".
if [ "${EXPIRES}" != "never" ]; then
  DIGITS="${EXPIRES%[dh]}"
  case "${DIGITS}" in
    ''|*[!0-9]*)
      echo "FAIL: --expires duration must be a whole number of days or hours; got '${EXPIRES}'." >&2
      exit 64 ;;
  esac
  if [ "${DIGITS}" -le 0 ]; then
    echo "FAIL: --expires must be positive; a credential expiring now is not a credential." >&2
    exit 64
  fi
fi

# --------------------------------------------------------------------------------------------------
# Key material
# --------------------------------------------------------------------------------------------------
# 32 bytes = 256 bits from the OS CSPRNG, rendered base64url so the key is safe in a header and in a
# shell argument. The `crk_` prefix is what makes a leaked key greppable — a deliberate property of
# the format, which scripts/scan.sh depends on.
RANDOM_BYTES="$(openssl rand 32 | base64 | tr '+/' '-_' | tr -d '=\n')"
if [ "${#RANDOM_BYTES}" -lt 43 ]; then
  echo "FAIL: generated key is too short (${#RANDOM_BYTES} chars); refusing to provision." >&2
  exit 1
fi
KEY="crk_${RANDOM_BYTES}"

# SHA-256 of the FULL presented string, prefix included. That is what the domain hashes
# (CreatorCredential.hashOf), and a hash over the suffix alone would never match.
KEY_HASH="$(printf '%s' "${KEY}" | shasum -a 256 | cut -d' ' -f1)"
if [ "${#KEY_HASH}" -ne 64 ]; then
  echo "FAIL: hash is not 64 hex characters; refusing to provision." >&2
  exit 1
fi

CREATOR_ID="$(uuidgen | tr 'A-Z' 'a-z')"
CREDENTIAL_ID="$(uuidgen | tr 'A-Z' 'a-z')"

# V1 made creator.api_key_hash NOT NULL and UNIQUE before V2 moved credentials to their own table.
# This filler is the creator id hashed — not key material, and named so a reader is not left
# wondering. JdbcCreatorRepository does the same thing for the same reason.
LEGACY_FILLER="$(printf '%s' "${CREATOR_ID}" | shasum -a 256 | cut -d' ' -f1)"

# Escape single quotes in the name, so a name cannot terminate the literal.
SAFE_NAME="$(printf '%s' "${NAME}" | sed "s/'/''/g")"

SQL="$(cat <<SQLEOF
BEGIN;
INSERT INTO creator (creator_id, name, api_key_hash, created_at, key_expires_at, active)
VALUES ('${CREATOR_ID}', '${SAFE_NAME}', '${LEGACY_FILLER}', now(), now() + interval '36500 days', true);
INSERT INTO creator_credential (credential_id, creator_id, key_hash, created_at, expires_at, revoked_at)
VALUES ('${CREDENTIAL_ID}', '${CREATOR_ID}', '${KEY_HASH}', now(), ${EXPIRES_SQL}, NULL);
COMMIT;
SQLEOF
)"

if [ "${EMIT_SQL_ONLY}" -eq 1 ]; then
  printf '%s\n' "${SQL}"
  printf 'CREATOR_ID=%s\n' "${CREATOR_ID}"
  printf 'KEY=%s\n' "${KEY}"
  exit 0
fi

# --------------------------------------------------------------------------------------------------
# Store
# --------------------------------------------------------------------------------------------------
PSQL_CMD="${PSQL:-psql}"
if ! command -v "${PSQL_CMD%% *}" >/dev/null 2>&1; then
  echo "FAIL: '${PSQL_CMD%% *}' is not available." >&2
  echo "This script needs a PostgreSQL client. Set PSQL to an alternative invocation if psql is" >&2
  echo "not installed (for example a docker exec wrapper), or use --emit-sql." >&2
  exit 69
fi

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-shortener}"
export PGUSER="${PGUSER:-shortener}"

if ! printf '%s\n' "${SQL}" | ${PSQL_CMD} -v ON_ERROR_STOP=1 -q >/dev/null; then
  echo "FAIL: the store rejected the provisioning transaction. Nothing was created." >&2
  exit 1
fi

# --------------------------------------------------------------------------------------------------
# Output — ONCE, to this terminal, and never anywhere else
# --------------------------------------------------------------------------------------------------
cat <<BANNER

  Creator provisioned.

    creator id : ${CREATOR_ID}
    name       : ${NAME}
    expires    : ${EXPIRES}

  KEY (shown once — it is not stored and cannot be recovered):

    ${KEY}

  Present it as:  Authorization: Bearer ${KEY}

  Only the SHA-256 hash of that string is in the database. If you lose the key, provision another
  credential and revoke this one.

BANNER
