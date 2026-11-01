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
new CSRF token. No local failure is fabricated as an HTTP response. Existing
client/wrapper treatment of preflight failures and 400/413/415/422 is unchanged;
future composition must preflight its session and must not claim a write failed
to commit based on an obsolete reply. Unknown-write recovery remains owner-gated.

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
