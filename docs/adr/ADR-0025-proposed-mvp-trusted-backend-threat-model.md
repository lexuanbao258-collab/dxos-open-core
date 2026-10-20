# ADR-0025 (PROPOSED) — DX-OS MVP trusted-backend SQL-actor threat model

**Status: PROPOSED FOR TEAM/SECURITY REVIEW — NOT ACCEPTED; DOES NOT SUPERSEDE ADR-0024.**
**Date:** 2026-10-20
**Issue/PR:** [#23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23), stacked Draft [#50](https://github.com/lexuanbao258-collab/dxos-open-core/pull/50).
**Owner proposal:** [Issue #23 handoff](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6288471117).
**Reviewer:** Yến for Identity/Application boundary; separate PostgreSQL/security review required for SQL privileges and risks.

> This is a decision request, **not implementation permission**. Existing ADR-0024 C3 secured CREATE and stronger HMAC/transaction-bound proof remain unresolved baseline until Bảo formally adopts a reviewed replacement. **D2/D3/D4 and Stage 6.2 stay BLOCKED.** No production credential, migration, SQL grant, endpoint or code is authorized here.

## Context

The original [ADR-0024](https://github.com/lexuanbao258-collab/dxos-open-core/blob/ff824fc827cdef448fff82d907cef0d50113d81a/docs/adr/ADR-0024-workflow-create-and-sql-principal-attestation.md) proposes a transaction-bound, canonical HMAC-SHA-256 actor proof so a thief possessing **only** the online runtime PostgreSQL password cannot forge another verified principal when calling protected SQL port functions. Key custody, Java/PostgreSQL canonicalization, rotation, PID/XID, expiry, replay and privileges all need independent verification. For an isolated student-run OLP demo, the team is considering a narrower trusted backend model without removing required authorization and data correctness.

## Decision options — NOT RATIFIED

**Option A (stronger, pending baseline):** retain ADR-0024 HMAC/PID/XID attestation and the stolen-runtime-DB-credential actor-spoof resistance claim. Do not implement until design and tests are independently approved.

**Option B (simplified, proposed MVP):** a controlled Spring Backend is the trusted actor-provenance enforcement point. OIDC/JWKS verified principal is authoritative INSIDE the server, never the browser/SQL caller. Application passes canonical actor/target/action into protected SQL ports, and SQL rechecks current membership/visibility/explicit ACL after tenant guard locking in the same physical port transaction. PostgreSQL enforces narrow grants and state/history atomicity but **does NOT independently cryptographically attest actor identity**.

**Security claim LOST under Option B:** an attacker who steals the online runtime DB credentials and can reach PostgreSQL may be able to invoke allowed SQL functions with a **forged actor argument**, even though direct table DML is denied. Narrow EXECUTE grants and SECURITY DEFINER functions do not by themselves restore ADR-0024's proof-of-actor guarantee. DB credential secrecy, private network isolation and trusted server process are explicit assumptions. Do not claim stolen-credential actor spoofing is prevented or independently tested PASS.

**Still mandatory under either option:**
- Valid OIDC verified principal and exact JWT authority; never trust browser actor/role/tenant headers.
- Exact tenant/resource membership, DEC-VIS visibility, requested explicit operation ACL, DEC-CREATE binding. DEC-D2-04 unauthorized binding non-disclosure is pending Yến review.
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

If Option B is adopted, publish NOT SUPPORTED / ACCEPTED RISK for stolen-runtime-DB actor spoofing; do not silently mark skipped previous cryptographic tests PASS.

## Decision and supersession protocol

1. Bảo must confirm real deployment and DB exposure assumptions, acceptance of lost proof and whether mandatory OLP security principles permit Option B.
2. Yến independently reviews Identity/Application trust boundary, CREATE ordering and SQL actor provenance limitations.
3. Independent PostgreSQL/security reviewer signs off actual SQL privileges, injection surface, concurrency/unknown-COMMIT, audit, backup/recovery and controlled deployment.
4. Only AFTER explicit owner decision on Issue #23 and reviewed exact HEAD: formally supersede/revise ADR-0024, select applicable PG17 evidence profile and authorize a separately-scoped Stage 6.2 plan.
5. If rejected, maintain Option A and all D4 cryptography/negative-test requirements.

**Current status:** undecided; D4 BLOCKED, Stage 6.1 NOT PASS, S6.2 BLOCKED, PR #50 Draft.
