// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from "react";

// Glyphs copied from the V6.2.1 design so the preview stays pixel-faithful.
const PATHS = {
  plus: <path d="M12 5v14M5 12h14" />,
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m15.6 15.6 5 5" />
    </>
  ),
  alert: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7.5v5.5M12 16.5v.01" />
    </>
  ),
  check: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="m8 12.5 2.8 2.8L16 9.8" />
    </>
  ),
  filter: <path d="M4 7h16M7 12h10M10 17h4" />,
  back: <path d="m15 5-7 7 7 7" />,
  home: <path d="M3 10 12 3l9 7v11H3zM9 21v-8h6v8" />,
  ticket: <path d="M6 4h12v16H6zM9 9h6M9 13h6M9 17h4" />,
  queue: <path d="M4 6h16M4 12h16M4 18h16" />,
  reload: (
    <>
      <path d="M20 7v5h-5M4 17v-5h5" />
      <path d="M6.7 9a7 7 0 0 1 12-2L20 12M4 12l1.3 5a7 7 0 0 0 12 2" />
    </>
  ),
} satisfies Record<string, ReactNode>;

export type IconName = keyof typeof PATHS;

export function Icon({ name }: { name: IconName }) {
  return (
    <svg className="ot-icon" viewBox="0 0 24 24" aria-hidden="true">
      {PATHS[name]}
    </svg>
  );
}
