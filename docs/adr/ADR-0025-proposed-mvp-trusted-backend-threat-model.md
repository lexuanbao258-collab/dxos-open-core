# ADR-0025 — DX-OS MVP trusted-backend SQL-actor threat model

**Status: OWNER-ACCEPTED MVP SCOPE (Option B) — INDEPENDENT IDENTITY/POSTGRESQL SECURITY SIGN-OFF PENDING; DOES NOT YET FORMALLY SUPERSEDE ADR-0024.**
**Date:** 2026-10-20
**Issue/PR:** [#23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23), stacked Draft [#50](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50).
**Owner decision:** [Issue #23 Option B ratification](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6289594814); [original proposal](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6288471117).
**Reviewer:** Yến for Identity/Application boundary; separate PostgreSQL/security review required for SQL privileges and risks.

> The owner **selects the narrower Trusted Backend / Option B for OLP MVP**, specifically deferring HMAC/PID/XID, nonce/replay, cryptographic key rotation and cross-language MAC tests. This does **not** approve SQL actor authenticity, implementation, or security sign-off. ADR-0024's stronger DB-password-compromise guarantee is **not available under Option B**, and ADR-0024 is not formally superseded until the required reviews/decision-record synchronization are complete. **D2/D3 and D4 security sign-off remain BLOCKED; Stage 6.2 BLOCKED.** No production credential, migration, SQL grant, endpoint or code authorized.

## Context

The original [ADR-0024](https://github.com/lexuanbao258-collab/dxos-open-core/blob/ff824fc827cdef448fff82d907cef0d50113d81a/docs/adr/ADR-0024-workflow-create-and-sql-principal-attestation.md) proposes a transaction-bound, canonical HMAC-SHA-256 actor proof so a thief possessing **only** the online runtime PostgreSQL password cannot forge another verified principal when calling protected SQL port functions. Key custody, Java/PostgreSQL canonicalization, rotation, PID/XID, expiry, replay and privileges all need independent verification. For the isolated student-run OLP MVP, the owner has now selected a narrower trusted backend model without removing required authorization and data correctness, subject to independent security review.

## Decision options — MVP OWNER DECISION RECORDED

**Option A (stronger, deferred from MVP):** retain ADR-0024 HMAC/PID/XID attestation and the stolen-runtime-DB-credential actor-spoof resistance claim for a potential post-MVP security profile. Not a delivery requirement for the student MVP if Option B receives appropriate security review.

**Option B (OWNER-SELECTED for student MVP; security sign-off pending):** a controlled Spring Backend is the trusted actor-provenance enforcement point. OIDC/JWKS verified principal is authoritative INSIDE the server, never the browser/SQL caller. Application passes canonical actor/target/action into protected SQL ports, and SQL rechecks current membership/visibility/explicit ACL after tenant guard locking in the same physical port transaction. PostgreSQL enforces narrow grants and state/history atomicity but **does NOT independently cryptographically attest actor identity**.

**Security claim LOST under Option B:** an attacker who steals the online runtime DB credentials and can reach PostgreSQL may be able to invoke allowed SQL functions with a **forged actor argument**, even though direct table DML is denied. Narrow EXECUTE grants and SECURITY DEFINER functions do not by themselves restore ADR-0024's proof-of-actor guarantee. DB credential secrecy, private network isolation and trusted server process are explicit assumptions. Do not claim stolen-credential actor spoofing is prevented or independently tested PASS.

**Still mandatory under either option:**
- Valid OIDC verified principal and exact JWT authority; never trust browser actor/role/tenant headers.
- Exact tenant/resource membership, DEC-VIS visibility, requested explicit operation ACL, DEC-CREATE binding. DEC-D2-04 unauthorized binding non-disclosure was AGREED by Yến **at design-contract scope only** in [PR #50 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50#issuecomment-6289513010); Java/SQL evidence remains outstanding.
- Non-login protected-table owner, minimum online SQL privileges, no direct protected V3/V5 table DML, no schema/role escalation, PUBLIC EXECUTE or unguarded definer helpers; separately authenticated admin/provisioner.
- ADR-0023 Option A: final post-lock authorization, Workflow write/history and confirmed COMMIT inside one physical JDBC port transaction; all policy mutators lock same tenant guard; READ/HISTORY guarded before materialization; unknown COMMIT never blindly retried.
- C3 server-owned opaque ID and no hidden collision or definition probe.
- Isolation of backend/DB network, credential rotation, no untrusted SQL injection path/dynamic SQL, parameterized calls and no secrets in frontend/logs.
- Reviews of V5 DDL/manifest, SQL grants, races, fault injection and disposable PostgreSQL 17 evidence BEFORE any secured live HTTP is exposed.

## Distinguishing acceptance evidence

| Acceptance class | Option A (attested) | Option B (trusted backend) |
|---|---|---|
| Verified OIDC and exact authority in server | MUST test | MUST test |
| Tenant/resource ACL/IDOR/BOLA and binding secrecy | MUST test | MUST test |
| Effective SQL role deny-direct-table grants | MUST test | MUST test |
| Final ACL+Workflow mutation/history+COMMIT, revoke races | MUST test | MUST test |
| Forged actor via stolen runtime DB credential | MUST prove denial before materialization/write | **Known unmitigated risk** unless another independently verified control exists; never mark PASS |
| Java/SQL canonical HMAC/PID/XID/replay/rotation | MUST test | **NOT IMPLEMENTED / NOT CLAIMED** |
| Public HTTP activation | Remains BLOCKED until all gates pass | Remains BLOCKED until reviewed Option B sign-off + all remaining gates pass |

Because Option B is owner-selected, publish **NOT SUPPORTED / OWNER-ACCEPTED RISK (SECURITY REVIEW PENDING)** for stolen-runtime-DB actor spoofing. Never mark deferred HMAC/PID/XID/replay/key tests PASS or silently substitute SQL/client-provided actor as independently verified proof.

## Decision and supersession protocol

1. **DONE — owner scope/risk decision:** Bảo selects Option B for the OLP MVP and explicitly accepts losing resistance to actor forgery following stolen runtime DB credentials **plus DB access**; confirm detailed deployment assumptions and OLP security compatibility during independent review.
2. Yến independently reviews Identity/Application trust boundary, CREATE ordering and SQL actor provenance limitations.
3. Independent PostgreSQL/security reviewer signs off actual SQL privileges, injection surface, concurrency/unknown-COMMIT, audit, backup/recovery and controlled deployment.
4. Only AFTER reviews accept the weaker model at an exact HEAD: formally scope/supersede the incompatible ADR-0024 HMAC requirement **for the MVP only**, select applicable PG17 evidence profile and separately authorize Stage 6.2. Until then, no SQL actor-proof PASS or migration/HTTP authorization.
5. If independent security review rejects Option B for the required release, **stop and raise the rejected risk decision to the owner**; do not silently restore the high-complexity HMAC work or bypass that security gate.

**Current status:** **Option B OWNER-ACCEPTED for MVP scope, independent security sign-off PENDING; D4 NOT PASS**, D2/D3 remain BLOCKED, Stage 6.1 NOT PASS, S6.2 BLOCKED, PR #50 Draft, Workflow HTTP OFF. This ADR records no implementation or executed PG17 proof.
