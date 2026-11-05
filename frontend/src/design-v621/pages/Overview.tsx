// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { Link } from "react-router-dom";
import { PageHead } from "../components/PageHead";
import { PriorityBadge } from "../components/PriorityBadge";
import { detailPath } from "../components/RequestResults";
import { StepDistribution } from "../components/StepDistribution";
import { WorkflowRail } from "../components/WorkflowRail";
import { emptyFilters, numbers, selectRows } from "../logic";
import { SERVICES } from "../model";
import { BASE, usePreview } from "../PreviewContext";
import { CreateButton } from "./RequestList";

/** Requests needing attention: high priority first, then oldest first. */
const ATTENTION_LIMIT = 5;

export function Overview() {
  const { tickets } = usePreview();
  const n = numbers(tickets);
  const rows = selectRows(tickets, "queue", "open", emptyFilters()).slice(
    0,
    ATTENTION_LIMIT,
  );
  return (
    <>
      <PageHead
        title="Yêu cầu đang mở"
        description={`${n.open} yêu cầu đang mở · ${n.submitted} chờ tiếp nhận · ${n.processing} đang xử lý`}
        actions={<CreateButton />}
      />
      <div className="home-grid">
        <section aria-labelledby="attention-title">
          <div className="section-head">
            <h2 className="ot-section-title" id="attention-title">
              Cần chú ý
            </h2>
            <span className="section-hint">
              Ưu tiên cao trước, sau đó cũ nhất trước
            </span>
          </div>
          <ul className="att-list">
            {rows.map((r) => (
              <li className="att-row" key={r.id}>
                <span className="ot-id att-id">{r.id}</span>
                <div className="att-main">
                  <Link
                    className="ticket-name"
                    to={detailPath(r.id, "overview")}
                  >
                    {r.title}
                  </Link>
                  <div className="subline">
                    {SERVICES[r.service]} · {r.requester}
                  </div>
                </div>
                <span className="att-rail">
                  <WorkflowRail status={r.status} />
                </span>
                <span className="att-prio">
                  {r.priority === "Cao" && (
                    <PriorityBadge priority={r.priority} />
                  )}
                </span>
              </li>
            ))}
          </ul>
          <p className="foot-note">
            <Link to={`${BASE}/queue`}>Xem toàn bộ hàng đợi</Link>
          </p>
        </section>
        <aside className="sidepanel" aria-label="Phân bố theo bước">
          <StepDistribution tickets={tickets} />
        </aside>
      </div>
    </>
  );
}
