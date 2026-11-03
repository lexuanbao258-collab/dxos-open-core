# Helpdesk local closeout — 2026-10-29

MODE: IMPLEMENT + VERIFY. Local provisioning and isolated HTTP/PostgreSQL checks PASS;
real Keycloak negative/browser replay NOT RUN. PR #74 remains Draft; no push, merge or LIVE change.

## Baseline and provenance

- Worktree: `D:/olp/dxos-browser-integration/.worktrees/helpdesk-browser-e2e`.
- Branch: `integration/helpdesk-browser-e2e-20261029`.
- Implementation baseline HEAD: `7f411174baecbe338700a73b0351be214e7892b4`.
- Initial status: only pre-existing `?? portal-bff/bin/`; preserved. Changes below are local; no remote publication is claimed.
- Read the [pinned Browser E2E note](https://github.com/lexuanbao258-collab/dxos-open-core/blob/f943d33c7b7c003fc44d0234e4c912f24dcee847/docs/verification/issue53-browser-helpdesk-e2e-20261029.md).
  GitHub GET also confirmed PR #74 open/Draft at `f943d33c7b7c003fc44d0234e4c912f24dcee847`.
  No remote mutation was performed.
- The browser happy path is **operator-observed** evidence from that note. No screenshot,
  browser automation, real SSO negative, or CI run was acquired by this closeout.
- Earlier PostgreSQL 18/18 and backend 110 tests remain earlier local evidence. They are
  not added to the execution counts below or relabeled as CI evidence.

## Root cause and change

The original seed omitted `helpdesk_actors.display_name`; the protected demo actors
had NULL labels. Schema permits this (`backend/src/main/resources/db/helpdesk/schema.sql:9`).
`HelpdeskService.java:210` reads the directory labels, nullable JSON fields are omitted
at lines 43–44, and `frontend/src/features/helpdesk/HelpdeskPortal.tsx:41` supplies the fallback.

Changes are restricted to local provisioning, tests, runner and these instructions:

- `scripts/helpdesk-local-actor-seed.ps1`: emit the existing canonical issuer/sub mapping,
  membership and fixed actor flags. Optional `TrustedDemoRealmFile` supplies names only
  when the loopback issuer realm and all three exact username/subject pairs match the
  trusted demo realm provisioning input. Names come from `firstName` + `lastName`;
  incomplete, ambiguous or mismatched identities fail before any SQL is emitted.
  With no trusted file, labels remain SQL NULL. SQL strings are escaped.
- `scripts/helpdesk-local-setup.ps1`: reuse that seed before resource creation; preserve
  the old parameter positions and existing new-resource-only guard. No existing volume
  or database is reseeded.
- `scripts/test-helpdesk-local-actor-seed.ps1`: seven assert-based checks for names,
  NULL fallback, exact canonical/authority preservation, mismatched subject/realm,
  SQL escaping and ambiguous users.
- `HelpdeskHttpPostgresTest.java:223`: execute the real PowerShell seed against the
  separate PostgreSQL fixture with explicit synthetic realm user IDs, then test detail
  labels, mismatched-subject 404, denied elevation and unchanged list contract. This
  PowerShell integration case is Windows-only; it executed and passed here.
- The synthetic OIDC fixture now uses `/realms/tricore` in its ephemeral issuer path.
  It is not the running Keycloak issuer and is never presented as real Keycloak evidence.
- `HelpdeskHttpPostgresTest.java:724`: extend the existing embedded production app test
  with socket HTTP negatives and database snapshot assertions after denied/duplicate writes.
- `scripts/helpdesk-negative-local.ps1`: guarded creation and execution on a fresh fixture;
  no dependency or production/Core/API/UI implementation change.
- `scripts/README-helpdesk-local.md`: document the trusted-file and isolated-runner usage.

The trust source is the provisioner's original trusted realm input, not a filename-based
trust decision or a verified login. The regression fixture explicitly declares Employee Demo,
Coordinator Demo and Worker Demo with their exact synthetic IDs. No trusted realm export
for the existing live demo was ingested, no current Keycloak profile was queried or changed,
and no name was inferred from a frontend role, username alone or the current DB role flags.

**List contract remains unchanged:** `HelpdeskService.java:261` intentionally omits names
from list/write projections. Provisioned labels are available on detail. Exposing names
in list requires separate contract review. Existing confirmed records retain NULL labels;
retroactive population requires a separate reviewed data fix and was NOT RUN.

## Isolation and protected evidence

Created resources, all distinct from browser/Stage6/staging resources:

| Resource | Observed value |
|---|---|
| Container | `dxos-helpdesk-negative-closeout_20261029_a` |
| Container ID | `ac7e43f33ddecd35dd8bfcb1b5dff0dcb58ba67c08d2d6e5c6475768cc589d60` |
| PostgreSQL | 17.11, image `postgres:17.11-alpine` |
| Host binding | `127.0.0.1:55439 -> 5432/tcp` |
| Database | `helpdesk_disposable_test` |
| New named volume | `dxos-helpdesk-negative-closeout_20261029_a-data` |
| JDBC pin | `jdbc:postgresql://127.0.0.1:55439/helpdesk_disposable_test` |

Preflight checked all existing container/volume names, Docker endpoint, image and free
port. A creation nonce rejects volume reuse/races. Tests create unique schemas/runtime
roles only in this fixture; the existing suite removes its own test schemas/roles afterward
(postcheck: zero `helpdesk_%` schemas). The new container/database/volume remain retained.
No existing container was stopped, restarted, reset, removed or migrated. Before/after
container IDs, startup times, states and restart counts were identical, including Keycloak,
the browser DB, Stage6 and staging containers.

Credential was generated by a CSPRNG, passed over stdin into a mode-600 tmpfs bootstrap
file, and used only through process environment by tests. Docker configuration contains
the password-file path, not the plaintext password. The bootstrap file was removed after
startup; tmpfs metadata and absence were independently checked. No plaintext host/durable secret file,
credential output or credential commit was created. The test admin is confined to this
container; application test connections use the suite's non-superuser runtime role.

Protected request `37941103-fc8b-43e7-920b-34ca35784c2c` in
`127.0.0.1:55442/helpdesk_disposable_browser_20261029` was SELECTed in
`REPEATABLE READ, READ ONLY`, ending with `ROLLBACK`, before and after the tests:

- `CONFIRMED / COMPLETED`, workflow version 4, assignment version 1, history count 4.
- Fingerprint over request, instance, ordered history, tenant actors/memberships,
  request ACL, registry and definition binding was identical:
  `b2f93acf81292456963db95f909d97db`.
- No POST, UPDATE or reseed targeted this request or its database. Backend/BFF/Keycloak/
  Caddy at 8080/8081/8182/8443 were not restarted; the test app and JWKS server used
  ephemeral loopback ports.

## Executed checks

All logs below are local, ignored build artifacts under
`backend/build/closeout-closeout_20261029_a/`; no CI result is claimed.

| Check | Result | Evidence |
|---|---|---|
| PowerShell actor provisioning assertions | PASS: 7 | `provisioning.log` |
| HTTP/PostgreSQL suite | PASS: 47; FAIL: 0; SKIP: 1; exit 0 | `http-postgres.log`, `http-postgres-results.json` |
| Frontend focused suites | PASS: 75; exit 0 | `frontend.log` |
| BFF focused suites | PASS: 28; exit 0 | `bff.log` |
| Protected request fingerprint | PASS, identical | `protected-request-before.json`, `protected-request-after.json` |
| Existing container metadata | PASS, identical | `protected-containers-before.txt`, `protected-containers-after.txt` |
| Secret storage/absence and log token scan | PASS | `secret-storage-check.txt`, `verification-summary.txt` |
| PowerShell parse and `git diff --check` | PASS | `verification-summary.txt` |

Commands executed:

```powershell
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/test-helpdesk-local-actor-seed.ps1
pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/helpdesk-negative-local.ps1 -RunId closeout_20261029_a
# Runner: backend/gradlew.bat --no-daemon test --tests '*HelpdeskHttpPostgresTest' --console=plain
# From frontend:
npm.cmd test -- src/services/mvp/helpdeskPresentation.test.ts src/services/mvp/http/sameOriginTransport.test.ts src/features/helpdesk/HelpdeskPortal.test.tsx
# From backend:
.\gradlew.bat -p ..\portal-bff test --rerun-tasks --tests '*PortalSecurityTest' --tests '*PortalBackendClientTest' --console=plain
git diff --check
```

The bundled PowerShell 7 executable was used locally; execution policy override was
process-scoped. No machine/user execution policy was changed. JDK was 21.0.11.
The runner refuses an existing container/volume/port/evidence path; it must not be rerun
against this retained fixture. Review artifacts directly; any fresh fixture needs a
separately free port compatible with the suite's explicit endpoint pin.

## Negative evidence boundaries

| Scenario | Result and actual boundary |
|---|---|
| Anonymous/expired token | PASS: socket HTTP 401; snapshots unchanged |
| Visible forbidden/business or technical authority | PASS: socket HTTP 403; snapshots unchanged |
| Cross-request, unrelated actor, cross-tenant IDOR/BOLA | PASS: socket HTTP 404; snapshots unchanged |
| Revoked exact transition grant | PASS: detail actions empty; socket POST 403; snapshots unchanged |
| Stale/duplicate transition and invalid source state | PASS: socket HTTP 409; snapshots unchanged |
| Revoked assignee eligibility | PASS: socket HTTP 403; snapshots unchanged; fixture policy restored |
| Terminal state mutation/duplicate confirm | PASS: socket HTTP 409; business/workflow/history rollback |
| Revoked membership, reassignment, concurrent writes, history/activation failure | PASS: existing MockMvc + real PostgreSQL tests |
| Lost COMMIT acknowledgement before/after actual commit | PASS: two JDBC fault-injection cases; authoritative PG read reconciliation and one commit attempt; not a browser/socket response-drop test |
| BFF/frontend no blind retry and unknown create lock | PASS: isolated client/BFF tests; not live SSO evidence |
| Expiring exact ACL grant | SKIP: current `resource_acl` contract has `granted` boolean, no expiry field; no policy/schema change made |
| Backend create idempotency for manual duplicate POST | NOT CLAIMED: contract has no idempotency key; duplicate transition guards and client no-retry are separate guarantees |
| Real Keycloak B06 | SKIP: `b06RealKeycloakTokensAndPostgres()` requires fresh real tokens; runner intentionally clears inherited B06 tokens |
| Real Browser/BFF negative SSO | NOT RUN; no credential/session acquisition or browser replay |

## Re-review and remaining gates

Review the local diff and new scripts, this report, per-case JSON, sanitized logs and
`SHA256SUMS.txt`. Provisioning and isolated negative HTTP/PG gates now have executed
local evidence. No Core/ACL policy or list DTO change needs approval in this diff.

Remaining: original Keycloak `VERIFY_PROFILE` redirect HTTP 400 documented in the pinned
note remains unexplained; real Keycloak/browser negative coverage, remote CI and human
security/contract review remain open. This closeout does not authorize merge or LIVE.
