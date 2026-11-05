// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { serviceCounts, stepCounts } from "../logic";
import {
  SERVICES,
  STEPS,
  STEP_LABELS,
  type PreviewTicket,
  type ServiceId,
} from "../model";

/**
 * Distribution of ALL preview tickets across the five workflow steps. It is a
 * count chart (bar length = count), deliberately unlike the per-ticket
 * WorkflowRail. Values are derived from the fixtures, never hardcoded.
 */
export function StepDistribution({
  tickets,
}: {
  tickets: readonly PreviewTicket[];
}) {
  const counts = stepCounts(tickets);
  const byService = serviceCounts(tickets);
  const max = Math.max(1, ...STEPS.map((s) => counts[s]));
  return (
    <section className="dist" aria-labelledby="dist-title">
      <h2 className="ot-section-title" id="dist-title">
        Số yêu cầu theo bước
      </h2>
      <p className="dist-sub">
        Tất cả {tickets.length} yêu cầu mẫu, mọi dịch vụ.
      </p>
      <ul className="dist-list">
        {STEPS.map((s) => (
          <li key={s}>
            <span className="dist-label">{STEP_LABELS[s]}</span>
            <span className="dist-bar" aria-hidden="true">
              <span style={{ width: `${(counts[s] / max) * 100}%` }} />
            </span>
            <span className="dist-count">{counts[s]}</span>
          </li>
        ))}
      </ul>
      <p className="dist-note">
        {(Object.keys(SERVICES) as ServiceId[])
          .map((id) => `${SERVICES[id]} ${byService[id]}`)
          .join(" · ")}
        . Hai dịch vụ dùng chung Workflow Core với 5 bước.
      </p>
    </section>
  );
}
