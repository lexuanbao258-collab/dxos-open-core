# A05 Phase 2A — unbound BFF adapters

Frontend baseline: `c73bbc306a698141169304e5da848ea0686700db` (PR #71).
BFF source reference: PR #70, `d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27`.
Backend source comparison: PR #74, `f943d33c7b7c003fc44d0234e4c912f24dcee847`;
Issue #53 business contract. Existing DTOs and their contract pin are unchanged.

These modules are not imported by App/main/portalService. MOCK remains the
default, legacy HttpApiClient remains LIVE_BLOCKED, and Phase 2B is BLOCKED.
No default client, environment enable flag or import-time network call exists.

`BffSessionClient` requires explicit fetch injection and an explicit `load()`.
It probes only GET `/bff/session`, validates the exact boolean/CSRF schema and
holds CSRF only in instance memory. There is no principal/role projection or
OAuth token storage. Starting a load clears the previous session immediately.
Each load/invalidation retires previous scopes. Obsolete loads reject and cannot
overwrite newer session state; obsolete API responses reject before returning
data or invalidating a successor. This does not claim cross-tab coordination.

After a successful authenticated load, `createHelpdeskBffTransport` captures that
session generation. A new generation requires a new transport and Helpdesk client.
The factory rejects before construction without a verified session. Retired
transports reject before network; POST always needs the captured session's CSRF.
It accepts only the nine reviewed method/path pairs, optional list after/limit,
and maps the wire namespace to `/bff/api/v1/mvp/requests`. Fetch is same-origin,
cookie-based, no-store, with redirect rejection and no retry. Headers are rebuilt
from an allowlist; no caller identity, authority or OAuth headers are forwarded.

HTTP statuses survive empty, malformed, non-JSON and unreadable bodies (body is
undefined). Existing HelpdeskApiClient retains responsibility for DTO decoding,
401/403/404/409 and unknown-write errors. Transport failures propagate without
replay. A current 401 retires the session; 403 does not infer expiry or fetch a
new CSRF token. No local failure is fabricated as an HTTP response. Treatment of
400/413/415/422 is unchanged. The proposed local session error contract below
clarifies preflight failures; unknown-write recovery remains owner-gated.

`browserAuthContract` supplies interface/data only, no executable login/logout.
Browser OIDC logout redirect, CSRF-403 and session-expiry behavior are all
**PENDING YEN CONFIRMATION**. No API redirect is followed as a login mechanism.

Before Phase 2B: Bảo/Yến must ratify the SHA combination and error disposition,
approve same-origin HTTPS ingress and real session/login/logout behavior, decide
unknown-create/reload reconciliation, and supply an isolated acceptance fixture.
Fake-fetch unit tests do not prove real BFF/Keycloak/Backend/browser E2E.

## Phase 2A verification (2026-11-01, uncommitted working tree)

- `npm.cmd run build`: PASS.
- `npm.cmd test`: 520 PASS / 0 FAIL / 0 SKIP, 16 files; 164 new adapter cases.
- `npm.cmd run check:security`: PASS, 69 source/bundle files (heuristic scan).
- `npm.cmd run test:smoke -- --output ./playwright-artifacts`: 17 PASS /
  0 FAIL / 1 SKIP. The default configuration deliberately skips the separate
  fake Helpdesk preview journey. Real BFF/Keycloak E2E was not run.
- Smoke ran headless outside the sandbox after the sandbox runner stalled.
  Its working directory was `frontend/test-results/a05-smoke-20261101/frontend`,
  using the original Playwright config/tests and built Portal. Generated
  screenshots stayed below that ignored directory; tracked evidence is unchanged.
- The first completed smoke run had 16 PASS / 1 FAIL / 1 SKIP: the existing IT
  test expected a terminal message immediately after resolve. Its assertion now
  matches the existing pending-confirmation UI; application/mock behavior is
  unchanged. The subsequent complete smoke run passed as recorded above.
- `git diff --check` and whitespace checks of all seven new files: PASS.

No adapter is wired into the application. Browser logout redirect, CSRF-403,
session-expiry policy and the Phase 2B owner gates above remain pending.

## A05-R06 — proposed consumer contract (pending Yến re-review)

Review: [Yến, PR #98](https://github.com/lexuanbao258-collab/dxos-open-core/pull/98#pullrequestreview-5726963024),
reviewed SHA `8c1fd1d96e87a52ed3583ae2873221c371aecc58`.
This working-tree proposal changes the exported frontend error/transport contract;
it is not an approved integration decision. Do not commit/push this proposal until
Yến ratifies the consumer contract. Keep PR #98 DRAFT and Phase 2B/LIVE BLOCKED.

`MvpSessionError` is separate from `MvpApplicationError`; the existing
`MvpErrorCode` union and Backend/BFF wire envelopes remain unchanged. Its `code`
is `SESSION_REQUIRED` or `STALE_SESSION`, and its `phase` records local evidence:
`before-fetch` proves fetch was not invoked; `after-fetch` means fetch was invoked
and does not prove that the server received or committed a POST. Only the
transport's own session checks may supply that evidence. Exceptions originating
inside fetch, including objects carrying these codes/phases, are ordinary
transport failures and cannot assert that a write was never sent.

| Situation | Consumer result | Session/UI effect |
| --- | --- | --- |
| No verified session at transport construction | Synchronous `MvpSessionError(SESSION_REQUIRED, before-fetch)` | No fetch or invalidation callback |
| Retired transport before GET/POST, including before CSRF access | `MvpSessionError(STALE_SESSION, before-fetch)` | No fetch or invalidation callback |
| GET becomes stale after fetch starts, including fetch/body failure | `MvpSessionError(STALE_SESSION, after-fetch)` | Discard old data; no invalidation callback |
| POST becomes stale after fetch starts, even an old 2xx/401/403/503 reply | `MvpApplicationError(COMMIT_OUTCOME_UNKNOWN)`; cause is `MvpSessionError(STALE_SESSION, after-fetch)` | No replay, reconciliation or invalidation callback |
| Current-session HTTP 401, even an unreadable/empty body | Existing `UNAUTHENTICATED` | Retire that session and close that API client; callback once |
| Current-session HTTP 403, including CSRF rejection | Existing `FORBIDDEN` | Keep session; no inferred expiry or CSRF refresh |
| Current-session GET network failure | Existing `NETWORK_FAILURE` | No retry or session probe |
| Attempted POST network/abort/redirect failure or unreadable success | Existing `COMMIT_OUTCOME_UNKNOWN` | No retry or reconciliation |

Because construction requires a verified session, `SESSION_REQUIRED` normally
occurs before there is an API client. Regression tests use lazy injection solely
to carry the real factory rejection through `HelpdeskApiClient.send()`; production
construction remains explicit and performs no I/O. Existing clients whose own
401 has closed them continue to reject with `UNAUTHENTICATED` without transport.
`executeWriteOnce` preserves typed `before-fetch` errors; all lifecycle failures
after fetch starts remain uncertain when treated as writes.

The transport validates its captured generation after body consumption and adds
an optional `HelpdeskHttpResponse.assertSessionCurrent` guard. API client invokes
it synchronously immediately after its transport await, before inspecting status,
decoding DTOs or invoking `onSessionInvalid`. A current 401's scope invalidation
returns a guard valid only until the next generation change. This closes the
transport-to-client await gap: an old 401 cannot reach a callback that would retire
a replacement session. Session-aware wrappers must preserve this response guard.
`BffSessionScope.invalidate()` therefore now returns `() => void`; stale scopes
remain unable to invalidate their successors.

Future consumers must distinguish these lifecycle errors from HTTP authentication
rejection and from network failure, and must ignore obsolete results for the new
session. No automatic retry, refresh, reauthentication, login navigation or write
reconciliation is prescribed or implemented. A new approved session still needs
explicit loading and a new transport/client. The new error class/phases, scope
return value and response guard require Yến's re-review before commit/push.

[Bảo's related A05-B04 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/98#pullrequestreview-5726971099)
also requires Ánh/Yến's cross-boundary error-contract sign-off. Its broader local
`INVALID_INPUT`/serialization classification is addressed by the A05-B04 follow-up
below; independent owner sign-off is still pending.

### Initial A05-R06 local verification (2026-11-01, before A05-B04 follow-up)

- `npm.cmd run build`: PASS (TypeScript and Vite).
- `npm.cmd test`: 629 PASS / 0 FAIL / 0 SKIP across 17 files; 109 new regression
  cases in `sessionErrorBoundary.test.ts`. Coverage includes all nine API routes,
  preflight lifecycle errors, CSRF ownership, stale fetch/body results, the
  transport/client await gap, and synchronous/asynchronous fetch exceptions.
- `npm.cmd run check:security`: PASS, 70 source/bundle files (heuristic scan).
- `npm.cmd run test:smoke -- --output ./playwright-artifacts`: 17 PASS / 0 FAIL /
  1 SKIP, exit 0. The skipped case requires the separate fake Helpdesk preview
  configuration. These are MOCK Portal browser tests, not provider E2E evidence.
- Smoke used the original `frontend/playwright.config.ts`, original tests and
  built Portal, with a package wrapper at
  `frontend/test-results/a05-r06-20261101/frontend/package.json`. Its cwd keeps
  screenshots and runner artifacts entirely beneath ignored `frontend/test-results/`.
  The sandbox run reported all cases but stalled at shutdown and was interrupted;
  the approved headless rerun outside the sandbox completed with the results above.
- `git diff --check`: PASS; the new untracked regression file also passed
  `git diff --no-index --check`. All seven changed/new TypeScript files pass
  Prettier's formatting check.

Only frontend files changed. No composition root, MOCK/LIVE switch, Backend, BFF
server, Core, Storage or DB was changed. No commit/push, PR approval/merge or
deployment was performed. A05-R06 is ready for contract re-review, not independently
closed; A05-HO-01..04 and Phase 2B/LIVE remain blocked.

## A05-B04 — confirmed predispatch failures (2026-11-01, uncommitted)

The follow-up preserves the eight existing local A05-R06 files and all 109
session-boundary regression cases. It adds no public error class, code, phase,
HTTP status, DTO field or wire envelope. `MvpSessionError` and the session
ownership guards above retain their existing proposed semantics.

An internal `WeakSet` in `http/predispatch.ts` records error identity only where
owned synchronous input validation or JSON preparation has confirmed that fetch
has not been invoked. `HelpdeskApiClient.send()` and `executeWriteOnce()` preserve
these failures. Known local input validators also record this evidence, so putting
an API call inside `executeWriteOnce()` preserves its existing `INVALID_INPUT`.
Input validation remains synchronous; it does not become an HTTP 401 or a session
expiry signal.

| Evidence | Consumer result | API fetch count |
| --- | --- | --- |
| Invalid local input, path, method or body | Existing `HelpdeskClientError(INVALID_INPUT)` | 0 |
| Cyclic/BigInt JSON or throwing getter/`toJSON` | Original serialization error instance, including native `TypeError` | 0 |
| Object whose `toJSON` yields no JSON string | Existing `INVALID_INPUT`; no bodyless POST | 0 |
| Serializer throws a primitive | `TypeError` with the primitive as cause | 0 |
| Fetch throws synchronously or rejects, even with `INVALID_INPUT` or a previously marked error | GET `NETWORK_FAILURE`; POST `COMMIT_OUTCOME_UNKNOWN` | 1 |
| Plain unclassified error in `executeWriteOnce()` | Existing conservative `COMMIT_OUTCOME_UNKNOWN` | No predispatch evidence inferred |

The identity marker is internal and is not attached to public error objects.
Names, codes or caller-supplied phases are not accepted as evidence. BFF fetch
exceptions are wrapped after invocation so they cannot reuse earlier predispatch
evidence. No retry, session probe, refresh or reconciliation was added. Current
HTTP 401/403 behavior, all nine routes, DTO decoders, optimistic versions and
same-origin/session/CSRF requirements are preserved. The adapter remains unbound;
MOCK is the default and `LIVE_BLOCKED` remains enforced.

`predispatchErrorBoundary.test.ts` adds 58 cases through the public API client and
the actual BFF transport. A test-only request rewrite injects invalid paths/bodies
that the public client normally prevents, without accessing private client methods.
Cases verify GET/POST fetch counts 0 versus 1, original error identity, cyclic and
nonserializable JSON, synchronous/asynchronous fetch errors, status-only 401/403,
lost POST response and no replay. The unchanged 109-case A05-R06 matrix verifies
retired/replaced sessions, discarded stale GETs, unknown attempted POSTs and that
old responses cannot invalidate a successor, including the transport/client await
gap.

Current verification:

- `npm.cmd run build`: PASS (TypeScript + Vite).
- `npm.cmd test`: 687 PASS / 0 FAIL / 0 SKIP across 18 files: 520 baseline + 109
  preserved A05-R06 + 58 A05-B04. The focused boundary run passed all 167 cases.
- `npm.cmd run check:security`: PASS on 72 source/bundle files (heuristic).
- `npm.cmd run test:smoke`: 17 PASS / 0 FAIL / 1 SKIP, exit 0, outside the sandbox.
  The skip requires the separate fake Helpdesk preview configuration. These are
  MOCK browser tests, not Keycloak/BFF/Backend/PostgreSQL E2E acceptance.
- Smoke used the original config/tests and current production build via an ignored
  package wrapper at `frontend/test-results/a05-b04-20261101/frontend/package.json`.
  Its script explicitly supplies `--output ./playwright-artifacts`; screenshots
  are under `frontend/test-results/a05-b04-20261101/docs/evidence/issue39-portal/`.
  Earlier startup attempts without the output override were interrupted: the
  default Playwright output cleanup removed prior ignored `frontend/test-results/`
  artifacts, including the initial A05-R06 runner artifacts referenced above, then
  stalled on the runner's own working directory. Those old artifacts were not
  recovered. All eight original source/test/document files remain present; the
  109-case session test file and original session/transport guards were preserved.
- `git diff --check`: PASS. All ten changed/new TypeScript files also pass the
  Prettier check; the three untracked files have no whitespace errors in their
  `git diff --no-index --check` output (exit 1 denotes the added-file diff).

Both findings are prepared for independent source/contract re-review. Formal
closure still requires Ánh/Yến's cross-boundary contract sign-off and Bảo's API
re-review; this working-tree change does not supply approval. A05-R06's proposed
public lifecycle class/phases and ownership guards still need ratification.
Composite SHA/DTO pin selection, APP-P0-01 and A05-HO-01..04/provider/browser E2E
remain owner decisions before Phase 2B/LIVE. No commit, push, merge, deployment,
new PR or changes outside the frontend source scope were made.
