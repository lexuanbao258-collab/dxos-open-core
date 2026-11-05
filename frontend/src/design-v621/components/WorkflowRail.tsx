// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { STEPS, STEP_LABELS, type Step } from "../model";

/**
 * Workflow Rail: the identity of a single ticket's progress. Five equal
 * segments (done = solid, current = solid + ring, to do = outline) and the
 * step name. Do not reuse it for counts; see StepDistribution.
 */
export function WorkflowRail({ status }: { status: Step }) {
  const current = STEPS.indexOf(status);
  return (
    <span className="rail">
      <span className="rail-track" aria-hidden="true">
        {STEPS.map((s, i) => (
          <span
            key={s}
            className={`rail-seg ${
              i < current ? "is-done" : i === current ? "is-current" : "is-todo"
            }`}
          />
        ))}
      </span>
      <span className="rail-label">{STEP_LABELS[status]}</span>
    </span>
  );
}
