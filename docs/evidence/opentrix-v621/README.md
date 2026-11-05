<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# OpenTrix V6.2.1 preview: evidence (baseline)

> Historical V6.2.1 baseline, kept as the "before" for Visual V7. Current evidence and test counts: `../opentrix-v7/README.md`.

Scope: the MOCK/DEV React preview at `/design-v621/*` (PR #128). Not LIVE evidence and not a WCAG sign-off.

## Reproduce

GitHub CI runs only `npm run build` and `npm test` (Vitest) for `frontend/`; Playwright is **not** run in CI. Run it locally:

```bash
cd frontend
npm ci
npx playwright install chromium     # once
npm run build                       # Playwright serves `vite preview` from dist/
npm run test:preview                # 25 tests: layout sweep, axe, keyboard, fonts, placeholder, reduced motion
npm test                            # Vitest, includes the 49 preview tests
SAVE_EVIDENCE=1 npm run test:preview -- -g axe   # rewrite the screenshots in this folder
```

`playwright-summary.json` is the per-test result of the last local run (Playwright 1.64.1, Node 24.19.0, Windows 11, Chromium). Re-running should give the same pass/fail; durations vary.

## Screenshots: provenance and licensing

- 14 PNGs, produced by the `axe + screenshots` tests above from the unmodified preview fixtures (8 synthetic tickets, 3 audit events from the V6.2.1 HTML candidate). They contain no real user data.
- Names follow `<viewport>-<screen>.png`; viewports 1440, 390 and 320 (320 only for list screens).
- Each image and `playwright-summary.json` has a REUSE sidecar (`<file>.license`): `Apache-2.0`, copyright TRICORE contributors.
- The images render Be Vietnam Pro and IBM Plex Mono (SIL OFL-1.1). Rendered text in a screenshot is not a redistribution of the font files; the font licences ship separately (see below).

## Font licences

Both families are self-hosted through `@fontsource` packages (version 5.3.0, `OFL-1.1` in each `package.json`). Licence texts are copied verbatim from the packages into `frontend/public/third-party-licenses/` (served with the app) with SPDX sidecars:

| Font | Copyright | File |
| --- | --- | --- |
| Be Vietnam Pro | 2021 The Be Vietnam Pro Project Authors | `be-vietnam-pro-OFL-1.1.txt` |
| IBM Plex Mono | 2017 IBM Corp. | `ibm-plex-mono-OFL-1.1.txt` |

Neither copyright line declares a Reserved Font Name. `NOTICE` is not edited: it defers third-party notices to Issue #38.

REUSE: since #127 the repository enforces REUSE 3.3. This PR adds `LICENSES/OFL-1.1.txt` (SPDX text, via `reuse download OFL-1.1`) for the font licence sidecars. `reuse lint` on this branch: compliant (388/388 files with copyright and licence information).
