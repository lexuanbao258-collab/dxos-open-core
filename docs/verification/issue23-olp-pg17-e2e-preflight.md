# DX-OS Stage 6 PG17 and Portal E2E — review-only runbook

Date: 2026-10-27. Status: TEST CANDIDATE, LIVE_BLOCKED. This document authorizes NO database writes, migrations, role provisioning, production Workflow HTTP or release merge.

## Pinned inputs

- Stage 6 D2: PR #57, db6b1a8007d2cf4720e9dff6996572a1e819bc49 — TEST ONLY.
- Stage 6 D3/P1-02: PR #58, 549de8c63442d89dd03ff71a699848c10afc9b98 — operator-reported 88/88 PostgreSQL 17 privilege tests; no independent production approval.
- Backend Helpdesk: PR #63, e12d105c89400a5fd69dd6cc1fca670ff1f04f51 — nine candidate routes; focused B06 real-Keycloak test evidence uses PostgreSQL **18.6**, port **55439**.
- Security NEG-05/NEG-06: PR #67, 583feacfa79eb32ed36ee392628723b2738494d9 — narrowly accepted source/evidence; not full BL-03 acceptance.
- Portal #51/#52 remains MOCK; Y2.2 same-origin BFF architecture is accepted, but real BFF→Portal E2E NOT EXECUTED.

**Critical separation:** PostgreSQL 18.6 B06 evidence does not satisfy Stage 6 PostgreSQL **17.x** SQL/LOGIN approval. Never change the B06 launcher to reuse another database.

## Step A — read-only PG17 preflight

Install Python 3, use Java 21 on PATH, and prepare a **separate clean worktree** at exact PR #58 HEAD. From the branch containing this script, run in PowerShell:

    py -3 .\scripts\olp_e2e_preflight.py --repo-root 'D:\olp\dxos-stage6-review' --expected-head 549de8c63442d89dd03ff71a699848c10afc9b98

Script only reads Git HEAD/dirty status, Java 21, exact Docker container metadata, SHOW server_version_num and SELECT current_database(). It requires Docker container dxos-stage6-pg17-disposable with postgres:17 image, loopback **127.0.0.1:55427**, stage6_test/stage6_disposable_test. Host bind or named volumes are denied; anonymous official PGDATA volume allowed. It **never starts/removes Docker, runs Gradle, writes SQL, provisions roles or prints credentials**. Missing or mismatched container → exit 2, PREFLIGHT=BLOCKED.

Unit tests (mock all subprocess/Docker access):

    py -3 -m unittest discover -s scripts -p "test_olp_e2e_preflight.py" -v

Passing preflight means READY_FOR_MANUAL_TEST_ONLY. It always emits STAGE6_D2_D3_D4_ACCEPTANCE=NOT_VERIFIED, PORTAL_BFF_E2E=NOT_EXECUTED and LIVE=BLOCKED.

## Step B — actual integration testing needs separate approval

Only with explicit approval for an already isolated disposable PostgreSQL 17 environment may an operator run existing Stage6RuntimeLoginPrivilegeIntegrationTest at exact #58 HEAD. It creates and cleans random test schemas/roles (unlike this read-only tool). Guard env settings from existing code: STAGE6_DISPOSABLE_PG17_URL=jdbc:postgresql://127.0.0.1:55427/stage6_disposable_test; STAGE6_DISPOSABLE_PG17_USER=stage6_test, password in private environment; STAGE6_DISPOSABLE_PG17_APPROVAL=YES; STAGE6_LOGIN_ROLE_TEST_APPROVAL=YES; WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE=true. Never set approval flags automatically or touch shared Supabase.

Record actual SHA, commands, Docker/Java/PG/Keycloak versions, XML pass/fail/error/skip, role/SQL grant inventory and cleanup. Separate qualified reviewer must still approve exact six wf_port_* signatures, EXECUTE-only runtime LOGIN, no PUBLIC/direct SQL/SET ROLE escalation, SECURITY DEFINER/search_path, approved offline manifest/operator and ADR-0025 Option B stolen DB credentials **plus network** residual risk. V5 and Workflow HTTP stay OFF.

## Step C — future real E2E matrix (not executed)

| Case | Real-world acceptance | Current status |
| --- | --- | --- |
| Portal login/logout/expiry | Keycloak Code+PKCE through same-origin BFF; secure HttpOnly session; CSRF/redirect/token handling | NOT EXECUTED |
| Request create/list/detail | Actual user JWT and resource/tenant ACL; server-owned IDs | Backend focused evidence only |
| Coordinator triage/assign | Exact authorities, allowedActions, expectedAssignmentVersion | Backend focused evidence only |
| Assigned IT worker start/resolve | Current eligible assignee, expectedVersion, confirmed PG commit/history | Backend focused evidence only |
| Original requester confirm | Ends CONFIRMED, never implied CLOSED | Backend focused evidence only |
| Security negatives | 401, visible 403, hidden/nonexistent 404, stale 409; revoke/assignment races | Partial source/evidence only |
| Lost response/unknown COMMIT | No blind retry, authorized reconciliation; lost create ID remains UNKNOWN if ambiguous | Policy principle in PR #60, no live UX evidence |
| Reusable Core/Storage/restart | 2 distinct immutable definitions on one unchanged binary, real reviewed ObjectStoragePort and durability | Separate Issue #27 requirement OPEN |

First Portal demo may show only the successful IT Helpdesk path, per owner PR #60 demo scope decision. Requester dispute/rework, wrong-scope reject, unsuccessful close and Facility journeys remain **NOT IMPLEMENTED, NOT WAIVED**. Mock UI is never labeled real E2E. Do not create new Workflow states or API routes merely to force a demo.

**Runbook disposition: PREPARED / ACTUAL PG17 STAGE6 AND PORTAL E2E UNVERIFIED / LIVE_BLOCKED.**
