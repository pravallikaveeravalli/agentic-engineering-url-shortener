# ADR-013: Authentication and Authorization Mechanism

## Status

**Accepted** — 2026-09-20 by Pravallika Veeravalli (human owner) at Gate 4, incorporating her three refinements
(bearer transport, `crk_` key prefix, required expiry). The required-expiry refinement's specification impact is
routed through **CR-002**, still open. Decision record: `docs/governance/gate-decisions/gate-04-adr.md`.

## Context

CL-001 (Gate 2) settled the **model**, and this ADR does not revisit it. The owner separated two populations
that must be treated differently: **link creators**, who are identified, own their links, and alone may read
their analytics; and **link followers**, the public, who must never authenticate because "a short link that
requires login is useless". The owner also settled that provisioning is a local operator script rather than an
HTTP endpoint, that keys are stored only as hashes, that the application never writes key material anywhere,
and that ownership is real — every link records its creator and analytics are owner-only.

What remains open is the **mechanism**: what form the credential takes, how it is stored and verified, and how
authorization is enforced on the analytics path.

Relevant constraints: PVT-002 proposes a 200 ms p95 creation budget, and credential verification sits on that
path. FR-URL-011 requires a refusal that does not reveal whether a code exists — no ownership oracle. EX-005
excludes self-service signup and account lifecycle.

**Assessment implication**: this is a security-sensitive decision in a submission for a financial firm. The
reasoning, not just the outcome, will be probed.

## Decision Drivers

1. **Hash-only storage** — mandated by CL-001; non-negotiable.
2. **Verification cost on the creation path** — PVT-002.
3. **Resistance to credential guessing.**
4. **Revocation** without an account lifecycle (EX-005).
5. **Setup burden for a reviewer.**
6. **No ownership oracle** on the analytics path.

## Options Considered

### Option A — Opaque random key, hashed with a password-grade KDF (bcrypt / argon2 / PBKDF2)

- **Approach**: high-entropy random key issued by the script; stored as a slow-KDF hash; verified per request.
- **Advantages**: the standard reflex for anything called a "credential"; deliberately expensive to brute
  force; well-understood by reviewers.
- **Disadvantages**: password-grade KDFs are **intentionally slow** — typically tens to hundreds of
  milliseconds by design. Paid on **every** authenticated request, that consumes a large fraction of PVT-002's
  200 ms budget for no security gain, because the threat the slowness defends against does not exist here
  (see Rationale).
- **Risks**: either the latency budget is blown, or the KDF cost is tuned down until it provides neither the
  KDF's benefit nor a fast hash's speed.
- **Implementation impact**: small, plus a per-request latency cost and likely a verification cache, which
  reintroduces its own complexity.
- **Assessment implications**: mixed — looks rigorous, but a reviewer who thinks it through will ask why a
  password KDF is protecting a 256-bit random value.

### Option B — Opaque random key, hashed with a fast cryptographic hash (SHA-256), constant-time comparison

- **Approach**: ≥256 bits from a CSPRNG, presented as a bearer token; stored as SHA-256; verified by hashing
  the presented value and comparing in constant time.
- **Advantages**: negligible verification cost, leaving PVT-002's budget for real work. Hash-only storage as
  CL-001 requires, and non-reversible. Revocation is a row update on the credential, needing no account model.
  Constant-time comparison closes the timing side channel. This is the standard guidance for **high-entropy
  tokens** as distinct from human-chosen passwords.
- **Disadvantages**: offers no protection against a weak key — the security rests entirely on the key having
  full entropy, so generation must be right.
- **Risks**: if key generation were ever weakened to something human-memorable, the fast hash would become the
  wrong choice.
- **Implementation impact**: smallest.
- **Assessment implications**: strong, provided the reasoning is recorded — which is the purpose of this ADR.

### Option C — Signed tokens (JWT or similar)

- **Approach**: issue signed tokens carrying creator identity; verify the signature per request.
- **Advantages**: stateless verification; no per-request store lookup.
- **Disadvantages**: revocation requires a denylist, which restores the state that statelessness was meant to
  avoid. Adds signing-key management and rotation. Carries no benefit for a provisioned-operator model with a
  handful of long-lived identities, and expiry/refresh machinery is meaningless here since EX-005 excludes the
  account lifecycle that would use it.
- **Risks**: the familiar JWT pitfalls — algorithm confusion, unvalidated claims — for no requirement.
- **Assessment implications**: would read as pattern application rather than fit-for-purpose judgment.

### Option D — Mutual TLS

- **Approach**: client certificates for creators.
- **Advantages**: strongest authentication of the four; no bearer secret in a header.
- **Disadvantages**: substantial setup burden — a CA, certificate issuance, and client configuration — pushed
  onto anyone trying to run the demonstration. Disproportionate for a single-host prototype.
- **Risks**: reviewer cannot exercise the authenticated paths at all.
- **Assessment implications**: negative on runnability, which plan §12 and ADR-012 both treat as a governance
  concern.

## Decision

**Option B.**

- **Credential**: opaque, ≥256 bits from a cryptographically secure RNG, generated by the operator script only,
  rendered as **`crk_` + base64url of the random bytes**.
- **Transport**: **`Authorization: Bearer <key>`**, as declared in `contracts/openapi.yaml`.
- **Storage**: SHA-256 **of the full presented string, prefix included**. No plaintext anywhere — not in the
  database, not in configuration, not in the repository, and never written by the application at any log level,
  including startup and shutdown (FR-URL-017, non-waivable).
- **Expiry**: the provisioning script takes a **REQUIRED** expiry parameter — a duration, or the explicit
  literal **`never`**. **There is no default.**

### Three owner refinements (2026-09-20)

Folded in before acceptance. Each is recorded with its reasoning because each changes a security-relevant
detail.

**1. Transport: `Authorization: Bearer` rather than a custom header.** The owner adopted the independent
reviewer's point:

> Proxies, frameworks, and log scrubbers redact the `Authorization` header by default; a custom header receives
> no such default protection, so this is free reduction of exactly the leak risk FR-URL-017 treats as
> non-waivable.

The earlier draft specified `X-Creator-Key`. The correction is strictly a gain: it costs nothing and recruits
every default redaction behaviour in the stack to defend a clause that cannot be waived.

**2. Key form: `crk_` prefix on the rendered key.** Two reasons, both recorded by the owner:

- **A leaked key is identifiable as a key on sight** — the GitHub/Stripe pattern. Someone who finds it in a
  paste, a log, or a commit knows immediately what they are holding and that it must be revoked.
- **Our own secret scan gains a reliable pattern to match.** `POL-SEC-002` scans the repository and telemetry;
  a fixed prefix converts that from hunting anonymous high-entropy strings — which is unreliable in both
  directions — into matching a known token shape.

The prefix **carries no information and does not reduce entropy**: the random component remains ≥256 bits, and
the stored form is the SHA-256 of the full presented string.

**3. Expiry is a required provisioning parameter, with no default.** The owner's reasoning:

> No one gets to not think about credential lifetime at provisioning — an unconsidered eternal key must be
> impossible; explicitly choosing `never` is permitted, silently receiving it is not.

Mechanics: `creator_credential` carries a **nullable `expires_at`**, null reachable **only** by passing the
explicit literal `never`. The authentication filter rejects an expired credential with **the same response
shape as a revoked one**, so an attacker learns nothing from the distinction.

**This is deliberately not an account lifecycle** — EX-005 stands. There is no renewal flow and no notification.
Rotation remains **provision-new-then-revoke-old**.
- **Verification**: hash the presented value; compare in **constant time**.
- **Revocation**: set `revoked_at` on the credential row; no account lifecycle required.
- **Authorization**: creation requires a valid creator. Analytics retrieval requires the caller to be the
  link's **owner**; a non-owner and an anonymous caller both receive the **same** not-found response as a
  caller asking about a code that does not exist, so the endpoint cannot be used as an ownership oracle
  (FR-URL-011).
- **Redirects**: no credential is read, required, or consulted. The redirect path does not touch the
  authentication filter at all (FR-URL-018).

## Rationale

The interesting question is A versus B, and it turns on a distinction worth stating precisely: **password-grade
KDFs exist to compensate for low-entropy secrets.** Humans choose guessable passwords, so bcrypt and argon2
make each guess expensive. An API key is a 256-bit value drawn from a CSPRNG — it is not guessable, not
dictionary-attackable, and not reusable from another breach. The entropy already does the work the KDF would
otherwise do, so paying a slow KDF on every request buys nothing while spending a meaningful share of PVT-002's
latency budget. A fast cryptographic hash with constant-time comparison is the appropriate construction for
high-entropy tokens, and it keeps the stored form non-reversible exactly as CL-001 requires.

This reasoning is recorded explicitly because the *reflex* answer is Option A, and "we used bcrypt" would pass
casual review while being the wrong tool. The dependency is also recorded honestly: Option B is correct **only
while keys are full-entropy random values**. If key generation were ever changed to anything human-chosen or
shorter, this decision would have to be revisited — which is why the ≥256-bit CSPRNG requirement is part of the
decision rather than an implementation detail.

Options C and D are rejected for disproportion rather than insecurity. JWT's statelessness is defeated by the
revocation denylist it would need; mTLS's strength is real but its setup burden would stop reviewers from
exercising the authenticated paths at all, and runnability is a governance concern here.

## Consequences

**Positive**: negligible verification cost; hash-only storage; trivial revocation; no ownership oracle; the
redirect path stays entirely free of authentication concerns. The `Authorization` header recruits default
redaction behaviour across proxies, frameworks and log scrubbers. The `crk_` prefix makes a leaked key
self-identifying and gives the secret scan a deterministic pattern. Required expiry makes an unconsidered
eternal credential structurally impossible.

**Negative**: security depends wholly on key entropy, making the generator a critical component. A bearer
secret is still exposed to anything that logs requests **without** default redaction — the header choice
reduces that exposure but does not eliminate it, which is why FR-URL-017's prohibition is non-waivable and the
secret scan covers captured telemetry. Required expiry adds a parameter the operator cannot skip, which is the
intended friction rather than a cost to be minimised.

**Operational**: keys are displayed once by the operator script to the operator's terminal and cannot be
recovered afterwards — a lost key is replaced, not retrieved. That is deliberate and follows from hash-only
storage. An expiring key simply stops working on its date; there is no warning, because there is no
notification infrastructure and EX-005 excludes building one. Operators choosing a duration accept that.

**Testing**: authenticated and anonymous creation tests; anonymous redirect test; owner, non-owner, and
anonymous analytics tests asserting the non-owner and anonymous responses are **indistinguishable** from
not-found; a constant-time comparison unit test; a test asserting no endpoint exists that issues keys; a secret
scan over repository and captured telemetry asserting zero key material. Added by the refinements: an
**expired-key rejection** test; a **`never`** case test proving a null `expires_at` is reachable only that way;
a test asserting the **script refuses to run without the expiry parameter**; a test asserting expired and
revoked credentials produce the **same response shape**; and a generator test asserting the `crk_` prefix and
≥256-bit random component.

**Governance**: satisfies CL-001's approved model. Any future change to the authentication mechanism is a
security-sensitive change and therefore requires the security-sensitive-action gate (plan §5) plus a
change-control record.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Weak key generation invalidating the fast-hash choice | ≥256-bit CSPRNG is part of this decision; asserted by unit test; revisiting generation requires revisiting this ADR |
| Key leaking through logs or error responses | FR-URL-017 non-waivable prohibition; `POL-SEC-002` secret scan over repository **and** telemetry; no key in any response body |
| Timing side channel in comparison | Constant-time comparison mandated and unit-tested |
| Ownership oracle via differing refusals | Non-owner and anonymous both return the same not-found shape; asserted by test |
| Key sprawl with no rotation story | `revoked_at` supports revocation; **required expiry** bounds every credential's lifetime by construction; rotation is provision-new-then-revoke-old, documented in `quickstart.md` |
| Operator picks `never` reflexively, defeating the point | Accepted and deliberate — `never` is a **recorded explicit choice**, not a silent default. The requirement is that the decision be made, not which way it goes |
| Expiring key fails in production with no warning | Disclosed: no notification infrastructure exists and EX-005 excludes building one. An operator choosing a duration accepts that; `never` is available for credentials where surprise expiry is unacceptable |
| Prefix mistaken for entropy reduction | Recorded explicitly: `crk_` carries no information and the random component remains ≥256 bits; the stored hash covers the full presented string |
| Someone "hardens" this to bcrypt later without reading the reasoning | The rationale above is the record; the latency consequence and the entropy dependency are both stated |

## Reversibility

**High.** Verification sits behind an authentication filter and a credential repository. Substituting Option A
is a hashing-function change plus a re-provisioning migration; substituting C or D is a filter change. Nothing
in the domain or the orchestration core depends on the mechanism.

## Traceability

- **Requirements**: FR-URL-018 (creator identity, public redirects), FR-URL-019 (provisioning and key
  handling), FR-URL-011 (owner-only analytics, no ownership oracle), FR-URL-016 (per-creator rate limiting
  needs a caller identity), FR-URL-017 (no key material in telemetry), NFR-SEC-002, NFR-SEC-005.
- **Specification**: CL-001, EX-005 (no self-service or account lifecycle), KE-23 (Creator), KE-24
  (CreatorCredential), SC-017, T-03, T-04, T-05, T-06.
- **Plan**: §5 security-sensitive-action gate, §8 authentication and secrets management, §14 Slice 3.
- **Contracts**: `contracts/openapi.yaml` — `creatorApiKey` security scheme; the deliberate `404` on the
  analytics path.
- **Expected tasks**: Slice 3 operator script, credential storage and verification, authentication filter,
  ownership check, and the tests named above.
- **Related**: ADR-002 (credential storage), ADR-007 (enumeration resistance complements ownership),
  ADR-012 (script runs without the application).

## Validation

Executable: authenticated versus anonymous creation; anonymous redirect succeeding with no credential; owner
versus non-owner versus anonymous analytics with the latter two indistinguishable from not-found; constant-time
comparison unit test; an absence test asserting no key-issuance endpoint exists; and a secret scan over the
repository and captured telemetry returning zero findings. The entropy requirement is validated by asserting
generated key length and RNG source.

Added by the refinements:

| Refinement | Validation |
|---|---|
| `Authorization: Bearer` transport | Contract test asserting the security scheme is `http`/`bearer` and that a credential presented in any other header is refused |
| `crk_` prefix | Generator test asserting the prefix and a ≥256-bit random component; a secret-scan test asserting the scan **matches** a planted `crk_`-prefixed string, proving the pattern works rather than assuming it |
| Required expiry | Script test asserting it **exits non-zero without the expiry parameter**; a `never` test asserting null `expires_at` is reachable only via the literal; an expired-key rejection test; a test asserting expired and revoked responses are byte-identical |
