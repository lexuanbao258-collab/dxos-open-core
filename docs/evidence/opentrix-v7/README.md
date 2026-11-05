<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# OpenTrix Visual V7: evidence (Overview and Queue)

Scope: MOCK/DEV React preview at `/design-v621/*` (PR #128). Stage 1 of the V7 restyle: shared tokens, Overview and Queue. Mine, Detail, Create and Integration are **not yet redesigned** (they only inherit the new tokens) and are not part of this evidence.

## Before / after

"Before" is the V6.2.1 baseline in `../opentrix-v621/`; "after" is this folder. Same file names, same capture settings (real fonts loaded, animations disabled, full page).

| Screen | Before | After |
| --- | --- | --- |
| Overview 1440 | `../opentrix-v621/desktop-1440-overview.png` | `desktop-1440-overview.png` |
| Queue 1440 | `../opentrix-v621/desktop-1440-queue.png` | `desktop-1440-queue.png` |
| Overview 390 | `../opentrix-v621/mobile-390-overview.png` | `mobile-390-overview.png` |
| Queue 390 | `../opentrix-v621/mobile-390-queue.png` | `mobile-390-queue.png` |
| Queue 320 | `../opentrix-v621/mobile-320-queue.png` | `mobile-320-queue.png` |

## Reproduce

```bash
cd frontend
npm ci && npx playwright install chromium
npm run build
npm run test:preview                              # 35 tests
SAVE_EVIDENCE=1 npm run test:preview -- -g axe    # rewrite the images (Overview and Queue only)
npm test                                          # Vitest (58 preview tests)
```

CI runs only build and Vitest; Playwright is not in CI. `playwright-summary.json` is the per-test result of the last local run.

## Measured, not assumed

- **Table to rows breakpoint = 1040px.** With Be Vietnam Pro loaded, the 7-column Queue table does not clip down to ~920px, but the title column drops below 240px and wraps to 2 lines under 1040px (233px at 1000, 206px at 960). The Playwright test `queue table is never clipped…` checks table width, title width (>= 240px) and lines (<= 2) from 1041 to 1500px.
- **Leaked global CSS fixed.** `main { padding: 34px 35px … }` from `src/styles.css` pushed preview content 35px right of the topbar. A test asserts topbar brand, notice strip and `h1` share one left edge at 390, 1100 and 1440px.
- **200% zoom** is only *emulated* as a 720x450 CSS viewport (no overflow, axe 0 violations); real browser zoom was not run.

## Not run

Screen readers (NVDA/VoiceOver), real devices, real browser zoom, Web Vitals, user testing. axe passing is not a WCAG sign-off.

## Licensing

PNGs and `playwright-summary.json` have REUSE sidecars (`Apache-2.0`, TRICORE contributors). Images are generated from the unmodified preview fixtures (8 synthetic tickets, 3 audit events); no real user data.
