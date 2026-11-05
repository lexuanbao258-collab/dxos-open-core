<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# OpenTrix Visual V7: evidence (all six screens)

Scope: MOCK/DEV React preview at `/design-v621/*` (PR #128). Stage B1 (tokens, Overview, Queue) was approved at `41590fb`; Stage B2 (Mine, Detail, Create, Integration) was approved visually with conditions at `7fb7872`; the handoff commit adds the Create placeholder fix below.

## Before / after

"Before" is the V6.2.1 baseline in `../opentrix-v621/`; "after" is this folder. Same file names, same capture settings (real fonts loaded, animations disabled, full page).

| Screen | 1440 before | 1440 after | 390 before | 390 after |
| --- | --- | --- | --- | --- |
| Overview | `../opentrix-v621/desktop-1440-overview.png` | `desktop-1440-overview.png` | `../opentrix-v621/mobile-390-overview.png` | `mobile-390-overview.png` |
| Queue | `../opentrix-v621/desktop-1440-queue.png` | `desktop-1440-queue.png` | `../opentrix-v621/mobile-390-queue.png` | `mobile-390-queue.png` |
| Mine | `../opentrix-v621/desktop-1440-mine.png` | `desktop-1440-mine.png` | `../opentrix-v621/mobile-390-mine.png` | `mobile-390-mine.png` |
| Detail (DX-1046) | `../opentrix-v621/desktop-1440-detail-DX-1046-from-queue.png` | `desktop-1440-detail-DX-1046-from-queue.png` | `../opentrix-v621/mobile-390-detail-DX-1046-from-queue.png` | `mobile-390-detail-DX-1046-from-queue.png` |
| Create | `../opentrix-v621/desktop-1440-create.png` | `desktop-1440-create.png` | `../opentrix-v621/mobile-390-create.png` | `mobile-390-create.png` |
| Integration | `../opentrix-v621/desktop-1440-integration.png` | `desktop-1440-integration.png` | `../opentrix-v621/mobile-390-integration.png` | `mobile-390-integration.png` |

320px "after" images exist for Overview, Queue, Mine, Detail and Create (`mobile-320-*.png`). A 320px "before" exists only for Queue and Mine (`../opentrix-v621/mobile-320-{queue,mine}.png`); the V6.2.1 run did not keep the others.

`mobile-390-queue-scrolled-viewport.png` is a **viewport** capture after scrolling (not full page): the fixed bottom navigation sits at the bottom edge. In the full-page images the fixed bar appears mid-image; that is a full-page-capture artefact, not a layout bug.

Stage B2 also changed the mobile list rows: the status sentence (Mine) or requester/assignee (Queue) and the date now share one meta line with the date at the right, so `mobile-390-queue.png` and `mobile-320-queue.png` differ from the B1 approval images in that line only.

## Reproduce

```bash
cd frontend
npm ci && npx playwright install chromium
npm run build
npm run test:preview                              # 44 tests
SAVE_EVIDENCE=1 npm run test:preview -- -g axe    # rewrite the images
npm test                                          # Vitest (77 preview tests)
```

CI runs only build and Vitest; Playwright is not in CI. `playwright-summary.json` is the per-test result of the last local run.

## Measured, not assumed

- **Table to rows breakpoint = 1040px** for both Queue and Mine. With Be Vietnam Pro loaded the Queue title column drops below 240px / 2 lines under 1040px; tests check no clipping, title width >= 240px and <= 2 lines from 1041 to 1500px (Mine too).
- **One left edge.** Topbar, environment strip and page content share the same left edge at 390, 1100 and 1440px (global `main` padding no longer leaks in).
- **Mobile row meta.** At 320, 360, 390 and 760px every Mine and Queue row keeps its date on the first line of the meta block, right aligned, with no overflow.
- **Bottom navigation** is `position: fixed` and sits on the viewport bottom edge at 0%, 50% and 100% scroll on Queue, Mine, Detail, Create and Integration at 390x844 (real Chromium scrolling, emulated viewport); the footer always ends above it.
- **Create placeholders** never clip at 320, 360, 390 (IT and Facility, title and location); the title input switches to a shorter example when the long one does not fit (same rule as the search box). This also exposed and fixed a layout bug: below 1000px the form column shrank to its content (29px narrower in Facility mode).
- **200% zoom** is only *emulated* as a 720x450 CSS viewport (no overflow, axe 0 violations) for Overview and Queue.

## Not run

Screen readers (NVDA/VoiceOver), physical devices and Safari/iOS bottom-bar behaviour, real browser zoom, Web Vitals, user testing. axe passing is not a WCAG sign-off.

## Licensing

PNGs and `playwright-summary.json` have REUSE sidecars (`Apache-2.0`, TRICORE contributors). Images are generated from the unmodified preview fixtures (8 synthetic tickets, 3 audit events); no real user data.
