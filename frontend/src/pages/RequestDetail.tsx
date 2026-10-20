import { useRef, useState } from "react";
import {
  ArrowLeft,
  Building2,
  Headphones,
  LockKeyhole,
  MessageSquare,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { Link, useParams } from "react-router-dom";
import { useRequests } from "../hooks/useRequests";
import { usePortal } from "../context";
import { formatDate, kindLabels, type RequestAction } from "../domain/request";
import { requestService } from "../services/portalService";
import { PortalError, safeError } from "../services/RequestService";
import {
  ErrorState,
  Loading,
  Modal,
  PageHeading,
  Priority,
  StatusBadge,
  Timeline,
} from "../components/ui";

export function RequestDetail() {
  const { id } = useParams();
  const { data, loading, error, reload } = useRequests(id);
  const { role, refresh, notify, expireSession } = usePortal();
  const [action, setAction] = useState<RequestAction | null>(null);
  const [note, setNote] = useState("");
  const [actionError, setActionError] = useState("");
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  if (loading) return <Loading />;
  if (error || !data[0])
    return (
      <ErrorState
        message={error || "Không tìm thấy yêu cầu hoặc yêu cầu không khả dụng."}
        onRetry={reload}
      />
    );
  const item = data[0];
  const actions = role ? requestService.actions(item, role) : [];
  async function execute() {
    if (!action || !role || lock.current || uncertain) return;
    if (note.trim().length < 5) {
      setActionError("Ghi chú cần từ 5 đến 1.000 ký tự.");
      return;
    }
    lock.current = true;
    setBusy(true);
    setActionError("");
    try {
      await requestService.transition(
        item.id,
        action.id,
        note,
        item.version,
        role,
      );
      setAction(null);
      refresh();
      notify("Đã cập nhật yêu cầu demo và lịch sử xử lý.");
    } catch (err) {
      setActionError(safeError(err));
      if (err instanceof PortalError && err.code === "COMMIT_OUTCOME_UNKNOWN")
        setUncertain(true);
      if (err instanceof PortalError && err.code === "401") {
        expireSession();
      }
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="back-link" to="/requests">
        <ArrowLeft size={16} />
        Danh sách yêu cầu
      </Link>
      <PageHeading
        eyebrow={`${item.id} · ${kindLabels[item.kind]}`}
        title={item.title}
        description={`Tạo bởi ${item.requester} · ${formatDate(item.createdAt)}`}
      >
        <StatusBadge status={item.status} />
      </PageHeading>
      <div className="detail-layout">
        <div>
          <section className="panel detail-content">
            <div className="panel-heading">
              <h2>Nội dung yêu cầu</h2>
              {item.kind === "it" ? (
                <Headphones size={21} />
              ) : (
                <Building2 size={21} />
              )}
            </div>
            <p className="request-description">{item.description}</p>
            <div className="attachment-block">
              <LockKeyhole size={20} />
              <div>
                <strong>
                  Tệp đính kèm{" "}
                  <span className="blocked-tag">BLOCKED FEATURE</span>
                </strong>
                <p>Storage API chưa sẵn sàng để tích hợp an toàn.</p>
              </div>
            </div>
          </section>
          <section className="panel detail-timeline">
            <div className="panel-heading">
              <h2>
                Lịch sử xử lý{" "}
                <span className="count-badge">{item.timeline.length}</span>
              </h2>
              <span className="muted">Mới nhất trước</span>
            </div>
            <Timeline events={item.timeline} />
          </section>
        </div>
        <aside>
          <section className="panel metadata">
            <div className="panel-heading">
              <h2>Thông tin yêu cầu</h2>
            </div>
            <dl>
              <dt>Trạng thái</dt>
              <dd>
                <StatusBadge status={item.status} />
              </dd>
              <dt>Ưu tiên</dt>
              <dd>
                <Priority priority={item.priority} />
              </dd>
              <dt>Dịch vụ</dt>
              <dd>{kindLabels[item.kind]}</dd>
              <dt>Người yêu cầu</dt>
              <dd>{item.requester}</dd>
              <dt>Phòng ban</dt>
              <dd>{item.department}</dd>
              <dt>Nhóm phụ trách</dt>
              <dd>{item.assignee}</dd>
              <dt>Vị trí</dt>
              <dd>{item.location}</dd>
              <dt>Cập nhật gần nhất</dt>
              <dd>{formatDate(item.updatedAt)}</dd>
              <dt>Phiên bản mock</dt>
              <dd>v{item.version}</dd>
            </dl>
          </section>
          <section className="panel action-panel">
            <h2>
              <MessageSquare size={18} />
              Thao tác xử lý
            </h2>
            {actions.length ? (
              <>
                <p>
                  Các thao tác dưới đây là phản hồi mô phỏng, không gọi Workflow
                  API.
                </p>
                {actions.map((a) => (
                  <button
                    key={a.id}
                    disabled={uncertain}
                    className={`button ${a.tone === "danger" ? "danger" : "primary"}`}
                    onClick={() => {
                      setAction(a);
                      setNote("");
                      setActionError("");
                    }}
                  >
                    {a.label}
                  </button>
                ))}
              </>
            ) : (
              <p>
                {role !== "processor"
                  ? "Chuyển sang vai trò Người xử lý trong Trạng thái phiên để trải nghiệm thao tác demo."
                  : "Yêu cầu đã kết thúc. Không còn thao tác xử lý."}
              </p>
            )}
            {uncertain && (
              <ErrorState message="Kết quả chưa xác định. Kiểm tra lịch sử; không gửi lại thao tác." />
            )}
            <button className="text-link" onClick={reload}>
              <RefreshCw size={14} />
              Tải lại chi tiết
            </button>
          </section>
          <p className="aside-footnote">
            <ShieldCheck size={16} />
            Lịch sử này dùng mock data, chưa phải audit trail từ Core.
          </p>
        </aside>
      </div>
      {action && (
        <Modal
          title={`${action.label} · ${item.id}`}
          onClose={() => {
            if (!busy) setAction(null);
          }}
        >
          <p className="muted">
            Thao tác chỉ thay đổi dữ liệu mock trong bộ nhớ.
          </p>
          <label className="field">
            Ghi chú xử lý
            <textarea
              autoFocus
              rows={4}
              maxLength={1000}
              value={note}
              disabled={busy || uncertain}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Mô tả quyết định hoặc kết quả xử lý…"
            />
          </label>
          {actionError && <ErrorState message={actionError} />}
          <div className="modal-actions">
            <button
              className="button secondary"
              disabled={busy}
              onClick={() => setAction(null)}
            >
              Đóng
            </button>
            <button
              className={`button ${action.tone === "danger" ? "danger" : "primary"}`}
              disabled={busy || uncertain}
              onClick={execute}
            >
              {busy
                ? "Đang cập nhật…"
                : `Xác nhận ${action.label.toLowerCase()}`}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
