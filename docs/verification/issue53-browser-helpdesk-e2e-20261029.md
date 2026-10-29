# Issue #53 — Browser IT Helpdesk E2E & PostgreSQL postcheck (2026-10-29)

**Status:** `LOCAL_BROWSER_HAPPY_PATH_OBSERVED_PASS / POSTGRES_POSTCHECK_REPORTED_PASS / NEGATIVE_E2E_PENDING / DRAFT / LIVE_BLOCKED / DO_NOT_MERGE`

**Evidence provenance:** Browser results were observed by the operator and reported with screenshots in the 2026-10-29 chat. The independent PostgreSQL read-only checks and focused test counts were reported by the operator's **local Codex run**; the author of this note did not re-execute those tests or acquire the local logs. Treat the former as operator-observed and the latter as Codex-reported, **not** authenticated CI or an independently replayed browser automation. No screenshot, token, credential, runtime dump or test artifact has been committed by this documentation update.

## Environment and code boundaries

- Integration **local worktree** (not necessarily pushed to GitHub): `D:/olp/dxos-browser-integration/.worktrees/helpdesk-browser-e2e`.
- Local branch/HEAD as reported by Codex: `integration/helpdesk-browser-e2e-20261029` @ `7f411174baecbe338700a73b0351be214e7892b4`. This SHA refers to the **local integration checkout**, not the implementation HEAD of PR #74.
- Frontend `https://localhost:8443` (Caddy), BFF `127.0.0.1:8081`, Java backend `127.0.0.1:8080`, Keycloak 26.8.1 issuer `http://127.0.0.1:8182/realms/tricore`.
- Separate disposable PostgreSQL database `helpdesk_disposable_browser_20261029` at `127.0.0.1:55442`, schema `helpdesk_demo`. This is **not** the 2026-10-28 Postman fixture or any Stage 6/staging DB.
- Synthetic actors: `employee-demo`, `coordinator-demo`, `worker-demo` from Keycloak realm `tricore`. All roles/permissions remain **server-side enforced**, not inferred from usernames in the UI.
- Request: `37941103-fc8b-43e7-920b-34ca35784c2c`, one disposable `IT_HELPDESK` request. **Do not replay writes against this terminal request.**

## Browser happy-path evidence (operator-observed)

| Step | Role | Result observed |
|---|---|---|
| Create | Employee | New request visible in list/detail with `SUBMITTED`, workflow version 0, assignment version 0 |
| Triage | Coordinator | `SUBMITTED → TRIAGED`; workflow version 1; `triage` history recorded |
| Assign | Coordinator | Existing eligible assignee selected; assignment version 1; workflow remained `TRIAGED` / version 1 |
| Start | Worker | `TRIAGED → IN_PROGRESS`; workflow version 2; `start_work` recorded |
| Resolve | Worker | `IN_PROGRESS → RESOLVED`; workflow version 3; resolution text visible; `resolve` recorded |
| Confirm | Employee | `RESOLVED → CONFIRMED`; workflow version 4; `confirm` recorded |

**Observed final state:** `CONFIRMED`, workflow version `4`, assignment version `1`, **four** ordered history transitions (`triage`, `start_work`, `resolve`, `confirm`). Assignment is an application write with its own version; it is **not** a fifth Workflow transition. The UI showed no further allowed action at terminal `CONFIRMED`.

The initial coordinator page had no actions until the operator switched to a clean incognito login for `coordinator-demo`; the user then saw the `Triage` button and successfully executed the step. **Root cause of the older missing-actions session was not independently traced**, so do not classify that older issue as a proven software defect or mark an authentication negative gate PASS.

The one-time `VERIFY_PROFILE` flow required synthetic emails for Coordinator and Worker. A redirect produced HTTP 400 during profile completion, but subsequently opening the already authenticated Portal detail route showed applicable actions. The redirect/error needs separate triage; a successful happy path does not explain that error.

## PostgreSQL read-only postcheck (Codex-reported)

Local Codex independently checked the same request in `REPEATABLE READ, READ ONLY` and ended with `ROLLBACK`. Reported **18/18 PASS**:

- Terminal state `CONFIRMED`, lifecycle `COMPLETED`, workflow version 4, assignment version 1, nonempty resolution and assignee.
- Request↔instance↔resource↔tenant, owner, definition/exact binding, memberships, ACL, History and timestamps consistent.
- Assignee was the `worker=true` actor who performed `start_work` and `resolve`; `triage` actor matched Coordinator, `confirm` actor matched requester.
- Four ordered transitions with matching expected versions and actors, one per version 1–4.

These checks were **reported** by Codex, not independently executed in the GitHub review environment. Database records were not modified by the postcheck.

## Root cause of missing display names (Codex-reported)

The requester, coordinator and assignee had valid canonical actors/membership, but their `helpdesk_actors.display_name` fields were **NULL**:

- `backend/src/main/resources/db/helpdesk/schema.sql:9`: permits null until verified provisioning.
- `scripts/helpdesk-local-setup.ps1:122`: local seed omits `display_name`.
- `backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskService.java:43,210`: backend selects actor-directory name and omits JSON field on null.
- `frontend/src/features/helpdesk/HelpdeskPortal.tsx:41`: UI correctly shows fallback `Tên không được cung cấp`.
- `HelpdeskService.java:261`: list DTO omits names by current contract; changing provisioning alone does **not** add names to the list.

**Corrective scope proposed, not executed:** provision a verified display name for canonical synthetic actors in the isolated local fixture (including a reviewed, separate migration for existing test records if needed); add regression tests; assess a separate contract change before exposing names in list responses. Do not synthesize identity from browser input, assert frontend roles, or mutate production identity data.

## Verification counts and boundaries

The latest **Codex-reported** focused verification was:
- Frontend: **75 PASS** (display-name mapping, session, routing, capability, write errors).
- BFF: **28 PASS** (auth/CSRF/expiry/proxy/no retry).
- Backend: **110 PASS** (identity, tenant/ACL, version conflicts, terminal state).
- Negative HTTP/PG integration: **SKIP**, because the isolated negative-test database expected at `127.0.0.1:55439` was unavailable. This is not evidence of negative E2E PASS.
- Git worktree remained unchanged; no code/data edit, and only pre-existing untracked `portal-bff/bin/` was noted. No new CI evidence claimed.

## Outstanding gates and non-destructive acceptance plan

1. **Display name (pending):** review original source/verified provisioning constraints, add minimal local fixture population and tests, and document treatment of existing disposable records. Preserve the exact subject/actor canonical mapping and authorization. The existing confirmed request is evidence and should **not** be altered without explicit approval.
2. **Negative tests (pending):** establish a **new** disposable database/container and fixture for auth/authorization, hidden/forbidden, IDOR, stale `expectedVersion`, revoked assignee and exact-once write/lost-response cases. Match the contract under test; never point destructive tests at `helpdesk_disposable_browser_20261029`, Stage 6/staging or shared containers. Run negative integration through real HTTP only after isolated fixture preflight. Report counts and per-case results with FAIL/SKIP where appropriate.
3. **UI/BFF redirect (pending):** investigate one-time Keycloak profile-update HTTP 400 from sanitized server events/logs without exposing secrets. Do not hide a recurring issue by retry or disable OIDC security.
4. **Review gates:** integration PR review/CI, installation on a clean machine, Facility Request, other OLP release and security gates remain open. PR #74 is Backend MVP documentation, not evidence that merged production code or all Core Stage 6 security has passed.

**Final bounded conclusion:** one real, local, multi-account **positive Browser E2E** to `CONFIRMED` was demonstrated and supported by a reported read-only PostgreSQL postcheck (18/18). This does **not** approve production exposure, PR merge, LIVE enablement, enterprise deployment, Facility Request, or the negative HTTP/PG gate.
