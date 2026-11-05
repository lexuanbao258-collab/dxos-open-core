// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { STEPS, STEP_LABELS, type Step } from "../model";

/** Compact five-bar progress used in lists. */
export function Progress({ status }: { status: Step }) {
  const i = STEPS.indexOf(status);
  return (
    <span className="progress">
      <span className="ot-bars" aria-hidden="true">
        {STEPS.map((s, j) => (
          <span key={s} className={`ot-bar ${j <= i ? "done" : ""}`} />
        ))}
      </span>
      <span className="step-label">{STEP_LABELS[status]}</span>
    </span>
  );
}
