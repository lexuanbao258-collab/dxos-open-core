# DX-OS Open-Core — OLP 2026 MVP demo runbook (evidence-gated)

**Status: DEMO REHEARSAL / MIXED EVIDENCE. NOT FULL-STACK OR OLP RELEASE READY.**  
**Owner:** Bảo. **Tracking:** [Issue #40](https://github.com/lexuanbao258-collab/dxos-open-core/issues/40), [Portal PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51), [Storage PR #43](https://github.com/lexuanbao258-collab/dxos-open-core/pull/43), [Workflow #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23), [Business #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53).  
**Prepared:** 2026-10-22. This is a DOCS-ONLY operator runbook in Draft deployment PR #44. No API endpoint, DB role, migration, staging credentials, or security gate is altered.

## 1. The only honest demo promise right now

- **Can demonstrate:** independent Layer-2 React/Vite Portal **MOCK / DEVELOPMENT** IT Helpdesk (employee submit → processor approve/start/resolve → original employee confirm), its timeline, Facility regression, error states, accessibility and no real API calls. This behavior is evidenced by Windows operator tests at PR #51 HEAD `076b9cba12af2841ec1b3dc2efd6530b8f15384b`: **31/31 Vitest, 14/14 Chromium smoke, build PASS, security pattern scan PASS**. These are operator logs, not CI. Initial Playwright missing-browser launch errors were resolved by installing Chromium.
- **Can discuss/show separate technical evidence:** PostgreSQL 17/Workflow Core candidate tests and role/ACL security candidate source; LocalFileSystemStorageAdapter single-host Windows/Linux contract tests at PR #43 HEAD `01d14d35ac43a44e111051dc81e9480df69a7ad5`. These are **NOT evidence that Portal uses those components via live HTTP**.
- **MUST NOT claim:** Portal → Gateway → Workflow Core → PostgreSQL end-to-end, verified user/tenant ACL in the browser, actual OIDC Portal login, authorized attachment upload/download, completed full-stack Docker Compose, AWS deployment, CI acceptance, or release-ready open-source licensing.
- **Project scope:** one main IT Helpdesk journey; Facility as a second **mock UX**; real second workflow-definition reuse remains a separate acceptance gate. No procurement/RAM scenario, BPMN designer, third Core port, extra ACL tables, HMAC/PID/XID, or new storage provider.

## 2. Prepare the current Portal checkout (Windows / JDK not required)

**Recommended:** use the existing Portal checkout, **not** the Stage 6 PostgreSQL worktree. Before running, check for local modifications; never overwrite them.

```powershell
Set-Location D:\olp\dxos-pr51-review
git status --short
git rev-parse HEAD
# Verified demo SHA: 076b9cba12af2841ec1b3dc2efd6530b8f15384b
Set-Location frontend
node --version       # requires Node >=22.12.0
npm.cmd ci
npm.cmd run build
npm.cmd run test
npx.cmd playwright install chromium
npm.cmd run test:smoke
npm.cmd run check:security
npm.cmd run dev
```

Open **http://127.0.0.1:5173/session**. Port 5173 is a local Vite development server, not a production Portal deployment. A current clean checkout at another SHA must record its **own** test evidence; do not recycle the 31/14 counts blindly. The confirmed review checkout SHA above is a historical baseline, not a promise that the branch stays fixed.

**For a fresh judge/reviewer clone** (separate empty folder), instead use:

```powershell
git clone --single-branch -b feature/issue39-portal-ui https://github.com/lexuanbao258-collab/dxos-open-core.git D:\olp\dxos-portal-rehearsal
Set-Location D:\olp\dxos-portal-rehearsal
git rev-parse HEAD
Set-Location frontend
npm.cmd ci
npm.cmd run build
npm.cmd run test
npx.cmd playwright install chromium
npm.cmd run test:smoke
npm.cmd run check:security
npm.cmd run dev
```

This fresh clone is **Portal-only**. It does not install the Backend, PostgreSQL, Keycloak or Local Storage automatically.

## 3. 15-minute mock rehearsal (NOT the final real E2E showcase)

| Minute | Operator action | What can be truthfully said |
|---|---|---|
| 0–2 | Draw the intended three-layer split: Portal (Layer 2) → authenticated Gateway/API → Headless Core/Data/Identity (Layer 1). | Planned trust boundary; not yet fully wired together |
| 2–4 | Employee **Ngọc Ánh** enters demo session, opens Dashboard, creates an **IT Support** request. | Client validation and in-memory fixture; demo role **not** Keycloak/OIDC |
| 4–7 | Switch through **Trạng thái phiên** to processor **Lê Xuân Bảo**, approve, start and complete IT work. | Mock `pending → approved → processing → resolved`; processor cannot confirm |
| 7–9 | Switch back to employee, open own request, select **Xác nhận hoàn thành**. | `RESOLVED → CONFIRMED`, **two distinct timeline events**. `CONFIRMED` is the mock demo endpoint; **not** backend `CLOSED` |
| 9–11 | Open list, detail/history, empty state and response to simulated 401/403/404/uncertain commit. | Safe **UI simulation**, not proof of server-side ACL, tenant isolation or durable audit |
| 11–12 | Briefly show Facility request follows existing `completed` mock path without employee confirmation. | Same Portal UI components reused, **not yet** proof of two Core Workflow definitions through HTTP |
| 12–14 | Show terminal/JUnit evidence and source PR links: 45 Portal tests; separately #43 Storage tests and #23 PG17 candidate evidence. | Clearly label **independent component evidence**; show exact SHA, date, environment, counts |
| 14–15 | Open **Trạng thái tích hợp → Kiểm tra chặn LIVE** and state unfinished gates. | API correctly `LIVE_BLOCKED`; no misrepresentation of a complete OLP submission |

Follow detailed [Portal Demo Guide at verified PR #51 SHA](https://github.com/lexuanbao258-collab/dxos-open-core/blob/076b9cba12af2841ec1b3dc2efd6530b8f15384b/frontend/DEMO_GUIDE.md) for exact menu/buttons. No secrets are needed for Portal-only MOCK.

## 4. Independent backend/Storage demonstrations — do not conflate with Portal

- **Workflow Core and PostgreSQL 17:** candidate SQL/ACL/read/race tests are evidenced in [Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6322510218), not enabled production migrations or a live HTTP API. For a local disposable workflow test, use only the explicitly approved local test runbook and disposable PG environment. **Never use shared staging PostgreSQL port 55426 for Stage 6 mutation/evidence tests**; the fixed real-LOGIN Stage 6 suite restricts itself to disposable local PG17 at port 55427.
- **Local Storage:** PR #43 `01d14d3` has separate Windows/Linux operator-reported 25/25 focused tests, fresh-JVM and container-recreation persistence evidence (reported [here](https://github.com/lexuanbao258-collab/dxos-open-core/pull/43#issuecomment-6322876224)); the adapter is **not** the Portal attachment API. Keep storage root private, persistent, owned by the Backend UID 10001 on Linux; OS ACLs, quota/headroom, backup/restore and cleanup remain deployment gates.
- **Deploy scaffold:** PR #44 `infra/compose.staging.yml` starts **only an isolated PG17 database by default**. Its optional Backend `app` profile requires **reviewed** identity, schema, DB runtime grants, local provider and safe credentials. Do **not** run `--profile app up` merely to inflate demo readiness, or deploy the full stack on the small existing EC2 `t3.micro`. Never use `down -v` on data to be retained.

## 5. Narrowest path to a REAL OLP Layer-2 → Layer-1 demo

**Gate A — Identity fixture (Yến):** approved Keycloak issuer and three synthetic verified A/B/C actors (issuer + subject → canonical actor) with exact JWT workflow authorities. Public Issues contain only PASS/BLOCKED, no passwords/JWTs/private actor references. Currently unverified.

**Gate B — Physical ACL/DB security (Bảo coordinates; qualified independent PostgreSQL/security reviewer signs):** keep [MVP Scope-Cut V2](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6292834608) and ADR-0025 Option B. Verify D2 V5 physical fixture and tenant/ACL/policy audit, D3 actual restricted runtime LOGIN with exactly six `wf_port_*` functions and no protected table direct access, SQL definer owner/path/PUBLIC/SET ROLE controls, cross-tenant, revoke races and transaction/CAS. Record accepted residual risk: stolen runtime DB credentials **with DB connectivity can forge SQL actor arguments**. Gate remains BLOCKED until independent review; don't call candidate tests production acceptance.

**Gate C — Protected Workflow HTTP (#22/#23):** only after approved DB setup and server-side verified principal → resource visibility/explicit operation ACL and final SQL ACL recheck, wire existing six-port adapter to HTTP; prove real 401, visible 403, hidden/missing 404, unavailable 503, stale-version, history and no unauthorized write with OIDC/JWKS filter chain and disposable PG17. Request prototype `/api/v1/requests/**` has unscoped list/get/write patterns; it **cannot be used as a shortcut** to announce authorized multi-tenant LIVE.

**Gate D — First thin Portal consumption (Ánh, after A–C):** introduce only the smallest reviewed `RequestService` HTTP adapter for **authorized READ detail + history**; keep writes/attachments blocked until separately tested; map approved DTO, don't send mock-only `priority/location/kind` unchanged. After read gates, add one write flow at a time through Gateway. No client-side actor headers or bypassing a Core boundary.

**Gate E — Release packaging (#40):** reproducible clean clone and independently reviewed compose/backend/Keycloak/Portal route; verify one complete IT Helpdesk real E2E, reuse with second Workflow Definition without Core algorithm changes, persistence after restart, authorized Storage path if claimed. LICENSE/SPDX/source attribution [#38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38) and API documentation [#37](https://github.com/lexuanbao258-collab/dxos-open-core/issues/37) must be settled before OLP release/tag.

**Release decision:** until A–E and the official OLP requirements are met, **MOCK REHEARSAL PASS ≠ OLP FULL E2E PASS**. Keep PRs #44/#43/#51 Draft, Workflow HTTP OFF and shared/staging DB unchanged.
