// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import type { Priority } from "../model";

/** Only "Cao" is emphasised; normal priority is quiet text. */
export function PriorityBadge({ priority }: { priority: Priority }) {
  return priority === "Cao" ? (
    <span className="ot-badge ot-badge-high">Cao</span>
  ) : (
    <span className="prio-normal">{priority}</span>
  );
}
