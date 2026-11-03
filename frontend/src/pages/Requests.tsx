import { useState } from "react";
import { Filter, Plus, Search, SlidersHorizontal } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";
import { filterRequests, kindLabels, statusLabels } from "../domain/request";
import { usePortal } from "../context";
import { useRequests } from "../hooks/useRequests";
import {
  Empty,
  ErrorState,
  Loading,
  PageHeading,
  RequestTable,
  Timeline,
} from "../components/ui";

export function Requests({
  mode = "list",
}: {
  mode?: "list" | "approvals" | "history";
}) {
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(1);
  const { role } = usePortal();
  const { data, loading, error, reload } = useRequests();
  const query = params.get("q") || "";
  const kind = params.get("kind") || "";
  const status = params.get("status") || "";
  const update = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    value ? next.set(key, value) : next.delete(key);
    setParams(next, { replace: true });
    setPage(1);
  };
  const source =
    mode === "approvals"
      ? data.filter((r) =>
          ["pending", "approved", "processing"].includes(r.status),
        )
      : data;
  const filtered = filterRequests(source, query, kind, status);
  const totalPages = Math.max(1, Math.ceil(filtered.length / 6));
  const currentPage = Math.min(page, totalPages);
  const selected = filtered.slice((currentPage - 1) * 6, currentPage * 6);
  const title =
    mode === "history"
      ? "Lịch sử hoạt động"
      : mode === "approvals"
        ? "Bàn phê duyệt & xử lý"
        : "Danh sách yêu cầu";
  return (
    <>
      <PageHeading
        eyebrow="Dịch vụ nội bộ"
        title={title}
        description={
          mode === "approvals"
            ? "Tiếp nhận, phê duyệt và theo dõi công việc của đội ngũ trong kịch bản demo."
            : mode === "history"
              ? "Mỗi thay đổi đều có dấu vết. Lịch sử mới nhất được hiển thị trước."
              : "Tất cả yêu cầu, trạng thái và thông tin bạn cần theo dõi."
        }
      >
        <Link to="/requests/new" className="button primary">
          <Plus size={18} />
          Tạo yêu cầu mới
        </Link>
      </PageHeading>
      {mode === "approvals" && role !== "processor" ? (
        <section className="panel">
          <Empty
            title="Bàn xử lý dành cho người phụ trách"
            description="Đổi sang vai trò Người xử lý trong phiên demo để trải nghiệm phê duyệt."
          >
            <Link to="/session" className="button primary">
              Chọn vai trò demo
            </Link>
          </Empty>
        </section>
      ) : (
        <section className="panel">
          <div className="list-tabs">
            <span className="selected-tab">
              {mode === "history"
                ? "Nhật ký yêu cầu"
                : mode === "approvals"
                  ? "Cần xử lý"
                  : "Tất cả yêu cầu"}
              <span className="count-badge">{source.length}</span>
            </span>
            <span className="muted">
              <SlidersHorizontal size={16} />
              Dữ liệu demo
            </span>
          </div>
          <div className="filters">
            <div className="search-field">
              <Search size={18} />
              <input
                aria-label="Tìm yêu cầu"
                placeholder="Tìm theo mã, tiêu đề hoặc người yêu cầu…"
                value={query}
                onChange={(e) => update("q", e.target.value)}
              />
            </div>
            <label className="filter-select">
              <Filter size={15} />
              <span className="sr-only">Loại dịch vụ</span>
              <select
                value={kind}
                onChange={(e) => update("kind", e.target.value)}
              >
                <option value="">Tất cả dịch vụ</option>
                {Object.entries(kindLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            <label className="filter-select">
              <span className="sr-only">Trạng thái</span>
              <select
                value={status}
                onChange={(e) => update("status", e.target.value)}
              >
                <option value="">Tất cả trạng thái</option>
                {Object.entries(statusLabels).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            {(query || kind || status) && (
              <button
                className="text-link"
                onClick={() => {
                  setParams({});
                  setPage(1);
                }}
              >
                Xóa bộ lọc
              </button>
            )}
          </div>
          {loading ? (
            <Loading />
          ) : error ? (
            <ErrorState message={error} onRetry={reload} />
          ) : !filtered.length ? (
            <Empty />
          ) : mode === "history" ? (
            <div className="history-list">
              {selected.map((r) => (
                <article key={r.id}>
                  <div className="section-title">
                    <Link to={`/requests/${r.id}`} className="text-link">
                      {r.id} · {r.title}
                    </Link>
                  </div>
                  <Timeline events={r.timeline} />
                </article>
              ))}
            </div>
          ) : (
            <RequestTable items={selected} />
          )}
          {!loading && !error && filtered.length > 0 && (
            <div className="pagination">
              <span>
                Hiển thị {(currentPage - 1) * 6 + 1}–
                {Math.min(currentPage * 6, filtered.length)} trên{" "}
                {filtered.length} yêu cầu
              </span>
              <div>
                <button
                  className="button secondary small"
                  disabled={currentPage === 1}
                  onClick={() => setPage(currentPage - 1)}
                >
                  Trước
                </button>
                <span>
                  {currentPage} / {totalPages}
                </span>
                <button
                  className="button secondary small"
                  disabled={currentPage === totalPages}
                  onClick={() => setPage(currentPage + 1)}
                >
                  Sau
                </button>
              </div>
            </div>
          )}
        </section>
      )}
    </>
  );
}
