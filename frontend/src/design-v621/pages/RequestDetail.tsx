// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { Link, useParams, useSearchParams } from "react-router-dom";
import { PageHead } from "../components/PageHead";
import { AUDIT_TICKET_ID, PREVIEW_AUDIT } from "../fixtures";
import { Icon } from "../icons";
import { auditLabel, nextStepNote, stepLabel } from "../logic";
import { SERVICES, STEPS, STEP_LABELS } from "../model";
import { BASE, usePreview } from "../PreviewContext";

const ORIGINS = {
  queue: ["/queue", "Bàn xử lý"],
  overview: ["", "Tổng quan"],
  mine: ["/mine", "Yêu cầu của tôi"],
} as const;

export function RequestDetail() {
  const { id = "" } = useParams();
  const [params] = useSearchParams();
  const { tickets } = usePreview();
  const fromParam = params.get("from");
  const origin: keyof typeof ORIGINS =
    fromParam === "queue" || fromParam === "overview" ? fromParam : "mine";
  const [backPath, backLabel] = ORIGINS[origin];
  const ticket = tickets.find((t) => t.id === id);
  const back = (
    <Link className="detail-back" to={`${BASE}${backPath}`}>
      <Icon name="back" /> {backLabel}
    </Link>
  );

  if (!ticket) {
    return (
      <>
        {back}
        <PageHead title="Không tìm thấy yêu cầu" />
      </>
    );
  }

  const step = STEPS.indexOf(ticket.status);
  const hasAudit = ticket.id === AUDIT_TICKET_ID;
  const hasActions = ticket.allowedActions.length > 0;

  return (
    <>
      {back}
      <div className="detail-head">
        <h1>{ticket.title}</h1>
        <div className="detail-topline">
          <span className="mono">{ticket.id}</span>
          <span>·</span>
          <span>{SERVICES[ticket.service]}</span>
          <span>·</span>
          <span>{ticket.priority}</span>
        </div>
      </div>
      <div className="ot-detail-layout">
        <div>
          <section className="detail-block">
            <h2>Tiến độ xử lý</h2>
            <div className="current-step">
              <strong>
                {stepLabel(ticket.status)} · Bước {step + 1}/5
              </strong>
            </div>
            <div className="wide-progress">
              <span className="ot-sr-only">
                Quy trình 5 bước, hiện ở bước {step + 1} trên 5:{" "}
                {stepLabel(ticket.status)}.
              </span>
              {STEPS.map((s, i) => (
                <div key={s} className={`pitem ${i === step ? "current" : ""}`}>
                  <div className={`ot-pbar ${i <= step ? "done" : ""}`} />
                  <span>{STEP_LABELS[s]}</span>
                </div>
              ))}
            </div>
            <div className="next-note">{nextStepNote(ticket.status)}</div>
          </section>
          <section className="detail-block">
            <h2>Lịch sử xử lý {hasAudit ? "(3)" : ""}</h2>
            {hasAudit ? (
              <div className="audit">
                {[...PREVIEW_AUDIT].reverse().map((a) => (
                  <div className="audit-item" key={a.time}>
                    <time>{a.time}</time>
                    <strong>{auditLabel(a.what)}</strong>
                    <p>{a.actor}</p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="bodycopy">
                Chưa có dữ liệu dấu vết được cung cấp cho yêu cầu này.
              </p>
            )}
          </section>
          <section className="detail-block">
            <h2>Tệp đính kèm</h2>
            <p className="bodycopy">
              Chưa hỗ trợ trong bản xem trước —{" "}
              <strong style={{ color: "var(--amber)" }}>BLOCKED #26</strong>.
            </p>
          </section>
        </div>
        <aside className="detail-aside">
          <h2>Thông tin yêu cầu</h2>
          <dl className="details">
            <dt>Người gửi</dt>
            <dd>{ticket.requester}</dd>
            <dt>Nhóm phụ trách</dt>
            <dd>{ticket.assignee ?? "Chưa có dữ liệu"}</dd>
            <dt>Cập nhật</dt>
            <dd>{ticket.updated}</dd>
            <dt>Phiên bản</dt>
            <dd>
              {ticket.version !== undefined ? (
                <span className="mono">v{ticket.version}</span>
              ) : (
                "Chưa có dữ liệu"
              )}
            </dd>
          </dl>
          {!hasActions && (
            <div className="no-actions">
              Không có thao tác xử lý nào được máy chủ cấp cho bản xem trước này.
            </div>
          )}
          <p className="minor-alert">
            Các thao tác ghi chỉ khả dụng khi máy chủ trả{" "}
            <span className="mono">allowedActions</span> và{" "}
            <span className="mono">expectedVersion</span> hợp lệ. Dữ liệu hiện
            tại không có quyền ghi.
          </p>
        </aside>
      </div>
    </>
  );
}
