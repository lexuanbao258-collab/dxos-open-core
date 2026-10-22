# Email/password UI evidence · Issue #39 / Draft PR #52

Screenshots are captured from the production frontend preview on localhost by
`frontend/tests/auth.spec.ts`, using synthetic data only.

| Page | Desktop (1440px) | Tablet (834px) | Mobile (390px) | Small mobile (320px) |
| --- | --- | --- | --- | --- |
| Login | [Screenshot](login-desktop.png) | [Screenshot](login-tablet.png) | [Screenshot](login-mobile.png) | [Screenshot](login-small-mobile.png) |
| Register | [Screenshot](register-desktop.png) | [Screenshot](register-tablet.png) | [Screenshot](register-mobile.png) | [Screenshot](register-small-mobile.png) |

Both routes remain frontend previews. Valid submission clears password fields
and displays **BLOCKED FEATURE**. Recovery is also blocked. No Identity API,
account creation, real session, token, password persistence or credential request
is implemented. The separate Demo link opens the existing `/session` screen.

Browser tests check responsive overflow, keyboard focus, validation, WCAG 2 A/AA
and 2.1 AA with axe, network traffic, storage, route navigation and Demo access.
Unit tests additionally reject tampered admin/processor selections and cover
storage failures, confirmation, loading and existing Demo session preservation.
Automated accessibility checks do not replace a full manual audit.

Regenerate from `frontend/` after building:

```powershell
npm.cmd run build
npm.cmd run test:smoke
```
