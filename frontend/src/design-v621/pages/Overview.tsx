// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { Link } from "react-router-dom";
import { PageHead } from "../components/PageHead";
import { Progress } from "../components/Progress";
import { detailPath } from "../components/RequestResults";
import { numbers } from "../logic";
import { SERVICES, STEP_LABELS, STEPS } from "../model";
import { BASE, usePreview } from "../PreviewContext";
import { CreateButton } from "./RequestList";

export function Overview() {
  const { tickets } = usePreview();
  const n = numbers(tickets);
  const rows = tickets.filter((r) => r.status !== "CONFIRMED").slice(0, 5);
  return (
    <>
      <PageHead
        title="Yêu cầu đang mở"
        description={`${n.open} yêu cầu đang mở · ${n.submitted} chờ tiếp nhận · ${n.processing} đang xử lý`}
        actions={<CreateButton />}
      />
      <div className="home-grid">
        <section aria-label="Các yêu cầu cần xem">
          <h2 className="ot-section-title">Yêu cầu gần đây</h2>
          <div className="list-frame">
            {rows.map((r) => (
              <div className="home-row" key={r.id}>
                <span className="ot-id">{r.id}</span>
                <div>
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
                <Progress status={r.status} />
              </div>
            ))}
          </div>
          <p className="foot-note">
            <Link to={`${BASE}/queue`}>Xem toàn bộ hàng đợi</Link>
          </p>
        </section>
        <aside className="sidepanel" aria-label="Kiến trúc nghiệp vụ">
          <h2 className="ot-section-title">Hai dịch vụ. Một luồng xử lý.</h2>
          <div className="arch-services">
            <div>
              <strong>Hỗ trợ CNTT</strong>
              <small>helpdesk.v1</small>
            </div>
            <div>
              <strong>Cơ sở vật chất</strong>
              <small>facility.v1</small>
            </div>
          </div>
          <div className="arch-joint" />
          <div className="arch-core">
            <strong>Workflow Core</strong>
            <span>5 bước xử lý dùng chung</span>
          </div>
          <p className="side-note">
            Đây là mô hình nghiệp vụ của bản demo, không chứng minh kết nối LIVE.
          </p>
          <div className="work-summary">
            {STEPS.map((s, i) => (
              <div
                key={s}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 11,
                  margin: "10px 0",
                }}
              >
                <span
                  className="mono"
                  style={{ fontSize: 12, color: "var(--muted)" }}
                >
                  0{i + 1}
                </span>
                <span>{STEP_LABELS[s]}</span>
              </div>
            ))}
          </div>
        </aside>
      </div>
    </>
  );
}
