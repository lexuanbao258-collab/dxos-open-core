// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { Link } from "react-router-dom";
import { BASE } from "../PreviewContext";
import { SERVICES, type ListRoute, type PreviewTicket, type QaScenario } from "../model";
import { Progress } from "./Progress";

export const detailPath = (id: string, from: string) =>
  `${BASE}/detail/${encodeURIComponent(id)}?from=${encodeURIComponent(from)}`;

const QA_STATE: Record<Exclude<QaScenario, "normal">, [string, string]> = {
  loading: ["Đang tải danh sách", "Đang xem trạng thái tải dữ liệu mẫu."],
  empty: ["Không có yêu cầu phù hợp", "Thử thay đổi bộ lọc hoặc từ khóa."],
  error: [
    "Không thể đọc danh sách",
    "Đây là trạng thái mô phỏng lỗi đọc, không có kết nối mạng.",
  ],
  success: [
    "Kiểm tra thành công",
    "Không có yêu cầu nào được ghi lên máy chủ.",
  ],
  unknown: [
    "Kết quả ghi chưa xác định",
    "Không được gửi lại thao tác. Cần xem lịch sử và đối soát với máy chủ trước khi tiếp tục.",
  ],
};

/** Simulated list state. `unknown` offers no retry, only the audit trail. */
export function QaStateBox({
  qa,
  route,
}: {
  qa: Exclude<QaScenario, "normal">;
  route: ListRoute;
}) {
  const [title, body] = QA_STATE[qa];
  const cls = qa === "unknown" ? "unknown" : qa === "error" ? "ot-error" : "";
  return (
    <div
      className={`state-box ${cls}`}
      role={qa === "error" || qa === "unknown" ? "alert" : "status"}
    >
      {qa === "loading" && (
        <>
          <div className="ot-skeleton" />
          <div className="ot-skeleton" style={{ maxWidth: 210 }} />
        </>
      )}
      <strong>{title}</strong>
      <p>{body}</p>
      {qa === "unknown" && (
        <Link to={detailPath("DX-1046", route)} className="btn">
          Xem lịch sử mẫu
        </Link>
      )}
    </div>
  );
}

interface Props {
  rows: readonly PreviewTicket[];
  route: ListRoute;
  qa: QaScenario;
  onResetFilters: () => void;
}

export function RequestResults({ rows, route, qa, onResetFilters }: Props) {
  const withQueue = route === "queue";
  if (qa !== "normal") return <QaStateBox qa={qa} route={route} />;
  if (!rows.length) {
    return (
      <div className="state-box">
        <strong>Không có yêu cầu phù hợp</strong>
        <p>Hãy sửa từ khóa hoặc xóa bộ lọc.</p>
        <button className="btn" type="button" onClick={onResetFilters}>
          Xóa bộ lọc
        </button>
      </div>
    );
  }
  return (
    <>
      <table className="data-table">
        <caption className="ot-sr-only">
          {withQueue ? "Hàng đợi" : "Yêu cầu của tôi"} — dữ liệu mẫu
        </caption>
        <thead>
          <tr>
            <th className="name-col" scope="col">
              Yêu cầu
            </th>
            <th scope="col">Dịch vụ</th>
            <th scope="col">Ưu tiên</th>
            <th scope="col">Bước</th>
            {withQueue && <th scope="col">Phụ trách</th>}
            <th scope="col">Cập nhật</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>
                <Link
                  className="row-link"
                  data-ticket={r.id}
                  to={detailPath(r.id, route)}
                >
                  <span className="ot-id">{r.id}</span>
                  <span className="ticket-name">{r.title}</span>
                  <span className="subline">{r.requester}</span>
                </Link>
              </td>
              <td>{SERVICES[r.service]}</td>
              <td>
                <span
                  className={`ot-priority ${r.priority === "Cao" ? "ot-high" : ""}`}
                >
                  {r.priority}
                </span>
              </td>
              <td>
                <Progress status={r.status} />
              </td>
              {withQueue && (
                <td>
                  {r.assignee ?? (
                    <span className="empty-value">Chưa có dữ liệu</span>
                  )}
                </td>
              )}
              <td className="col-updated">{r.updated}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="mobile-list" aria-label="Danh sách yêu cầu trên điện thoại">
        {rows.map((r) => (
          <Link
            key={r.id}
            className="mobile-card"
            data-ticket={r.id}
            to={detailPath(r.id, route)}
          >
            <div className="mobile-card-top">
              <h2>{r.title}</h2>
              {r.priority === "Cao" && (
                <span className="ot-priority ot-high">Cao</span>
              )}
            </div>
            <Progress status={r.status} />
            <div className="mobile-meta">
              <span>
                <span className="mono">{r.id}</span> · {SERVICES[r.service]}
              </span>
              <span>{r.updated.split(" ").slice(-1)[0]}</span>
            </div>
            {withQueue && (
              <div className="mobile-requester">
                {r.requester}
                {r.assignee ? ` · ${r.assignee}` : ""}
              </div>
            )}
          </Link>
        ))}
      </div>
    </>
  );
}
