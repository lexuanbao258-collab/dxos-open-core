// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { Link, useParams, useSearchParams } from "react-router-dom";
import { PageHead } from "../components/PageHead";
import { PriorityBadge } from "../components/PriorityBadge";
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
      <header className="detail-head">
        <h1>{ticket.title}</h1>
        <div className="detail-topline">
          <span className="ot-id">{ticket.id}</span>
          <span>{SERVICES[ticket.service]}</span>
          <PriorityBadge priority={ticket.priority} />
        </div>
      </header>
      <section className="detail-progress" aria-labelledby="progress-title">
        <h2 className="ot-section-title" id="progress-title">
          Tiến độ xử lý
        </h2>
        <p className="current-step">
          <strong>
            {stepLabel(ticket.status)} · Bước {step + 1}/5
          </strong>
        </p>
        <span className="ot-sr-only">
          Quy trình 5 bước, hiện ở bước {step + 1} trên 5:{" "}
          {stepLabel(ticket.status)}.
        </span>
        <ol className="hero-rail" aria-hidden="true">
          {STEPS.map((s, i) => (
            <li
              key={s}
              className={`hero-step ${
                i < step ? "is-done" : i === step ? "is-current" : "is-todo"
              }`}
            >
              <span className="hero-seg" />
              <span className="hero-label">{STEP_LABELS[s]}</span>
            </li>
          ))}
        </ol>
        <p className="next-note">{nextStepNote(ticket.status)}</p>
      </section>
      <div className="ot-detail-layout">
        <section aria-labelledby="history-title">
          <h2 className="ot-section-title" id="history-title">
            Lịch sử xử lý {hasAudit ? "(3)" : ""}
          </h2>
          {hasAudit ? (
            <ol className="ot-timeline">
              {[...PREVIEW_AUDIT].reverse().map((a, i) => (
                <li
                  className={`tl-item ${i === 0 ? "is-latest" : ""}`}
                  key={a.time}
                >
                  <strong className="tl-title">{auditLabel(a.what)}</strong>
                  <p className="tl-meta">
                    {a.actor} · <time className="ot-time">{a.time}</time>
                  </p>
                </li>
              ))}
            </ol>
          ) : (
            <p className="bodycopy">
              Chưa có dữ liệu dấu vết được cung cấp cho yêu cầu này.
            </p>
          )}
        </section>
        <aside className="detail-aside" aria-label="Thông tin và thao tác">
          <h2 className="ot-section-title">Thông tin yêu cầu</h2>
          <dl className="info-list">
            <div>
              <dt>Người gửi</dt>
              <dd>{ticket.requester}</dd>
            </div>
            <div>
              <dt>Nhóm phụ trách</dt>
              <dd>{ticket.assignee ?? <NoData />}</dd>
            </div>
            <div>
              <dt>Cập nhật</dt>
              <dd>
                <span className="ot-time">{ticket.updated}</span>
              </dd>
            </div>
            <div>
              <dt>Phiên bản</dt>
              <dd>
                {ticket.version !== undefined ? (
                  <span className="ot-id">v{ticket.version}</span>
                ) : (
                  <NoData />
                )}
              </dd>
            </div>
          </dl>
          <section className="actions-block" aria-labelledby="actions-title">
            <h3 id="actions-title">Thao tác xử lý</h3>
            {!hasActions && <p>Chưa có thao tác nào được máy chủ cấp.</p>}
            <details>
              <summary>Vì sao không có thao tác?</summary>
              <p>
                Các thao tác ghi chỉ khả dụng khi máy chủ trả{" "}
                <span className="mono">allowedActions</span> và{" "}
                <span className="mono">expectedVersion</span> hợp lệ. Dữ liệu
                hiện tại không có quyền ghi.
              </p>
            </details>
          </section>
          <p className="attach-row">
            <span>Tệp đính kèm</span>
            <strong className="blocked-ref">BLOCKED #26</strong>
            <span className="attach-note">
              Chưa hỗ trợ trong bản xem trước.
            </span>
          </p>
        </aside>
      </div>
    </>
  );
}

/** Missing value: muted text, never styled like real data. */
function NoData() {
  return <span className="no-data">Chưa có dữ liệu</span>;
}
