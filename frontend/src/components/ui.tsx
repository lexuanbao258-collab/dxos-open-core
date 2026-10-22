import { useEffect, useRef, type ReactNode } from "react";
import {
  AlertCircle,
  ArrowRight,
  Check,
  Circle,
  Clock3,
  Inbox,
  LoaderCircle,
  X,
  type LucideIcon,
} from "lucide-react";
import { Link } from "react-router-dom";
import {
  formatDate,
  kindLabels,
  priorityLabels,
  statusLabels,
  type ServiceRequest,
  type TimelineEvent,
} from "../domain/request";

export function Icon({
  icon: Component,
  size = 18,
}: {
  icon: LucideIcon;
  size?: number;
}) {
  return <Component size={size} strokeWidth={1.75} aria-hidden="true" />;
}
export function StatusBadge({ status }: { status: ServiceRequest["status"] }) {
  return (
    <span className={`badge status-${status}`}>
      <Icon
        icon={
          status === "completed" || status === "confirmed"
            ? Check
            : status === "processing"
              ? LoaderCircle
              : status === "pending" || status === "resolved"
                ? Clock3
                : Circle
        }
        size={13}
      />
      {statusLabels[status]}
    </span>
  );
}
export function Priority({
  priority,
}: {
  priority: ServiceRequest["priority"];
}) {
  return (
    <span className={`priority priority-${priority}`}>
      <span aria-hidden="true" />
      {priorityLabels[priority]}
    </span>
  );
}
export function PageHeading({
  eyebrow,
  title,
  description,
  children,
}: {
  eyebrow?: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h1>{title}</h1>
        <p className="muted">{description}</p>
      </div>
      {children}
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading-state" role="status">
      <LoaderCircle className="spinner" size={26} />
      <p>Đang tải dữ liệu demo…</p>
      <div className="skeleton" />
      <div className="skeleton short" />
    </div>
  );
}
export function Empty({
  title = "Chưa có yêu cầu phù hợp",
  description = "Thử đổi từ khóa hoặc xóa bộ lọc để xem thêm yêu cầu.",
  children,
}: {
  title?: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty">
      <span className="empty-icon">
        <Inbox size={28} />
      </span>
      <h3>{title}</h3>
      <p>{description}</p>
      {children}
    </div>
  );
}
export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div className="error-state" role="alert">
      <AlertCircle size={22} />
      <div>
        <strong>Chưa thể hoàn tất</strong>
        <p>{message}</p>
        {onRetry && (
          <button className="button secondary small" onClick={onRetry}>
            Tải lại dữ liệu
          </button>
        )}
        <Link className="text-link error-link" to="/session">
          Trạng thái phiên
        </Link>
      </div>
    </div>
  );
}
export function RequestTable({
  items,
  compact = false,
}: {
  items: ServiceRequest[];
  compact?: boolean;
}) {
  if (!items.length) return <Empty />;
  return (
    <div className="table-scroll">
      <table>
        <caption className="sr-only">Danh sách yêu cầu dịch vụ nội bộ</caption>
        <thead>
          <tr>
            <th>Yêu cầu</th>
            <th>Trạng thái</th>
            <th>Ưu tiên</th>
            {!compact && <th>Người yêu cầu</th>}
            <th>Cập nhật</th>
            <th>
              <span className="sr-only">Chi tiết</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((r) => (
            <tr key={r.id}>
              <td>
                <Link className="request-title" to={`/requests/${r.id}`}>
                  <span className="request-id">
                    {r.id} <span>· {kindLabels[r.kind]}</span>
                  </span>
                  <strong>{r.title}</strong>
                </Link>
              </td>
              <td>
                <StatusBadge status={r.status} />
              </td>
              <td>
                <Priority priority={r.priority} />
              </td>
              {!compact && (
                <td>
                  <span className="person">
                    <span className="avatar tiny">
                      {r.requester.split(" ").at(-1)?.slice(0, 1)}
                    </span>
                    {r.requester}
                  </span>
                </td>
              )}
              <td className="date-cell">
                {new Intl.DateTimeFormat("vi-VN", {
                  day: "2-digit",
                  month: "2-digit",
                  year: "numeric",
                }).format(new Date(r.updatedAt))}
              </td>
              <td>
                <Link
                  className="icon-button"
                  to={`/requests/${r.id}`}
                  aria-label={`Xem ${r.id}`}
                >
                  <ArrowRight size={16} />
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function Timeline({ events }: { events: TimelineEvent[] }) {
  return (
    <ol className="timeline">
      {[...events]
        .sort((a, b) => Date.parse(b.at) - Date.parse(a.at))
        .map((event, i) => (
          <li key={event.id}>
            <span className={`timeline-dot ${i === 0 ? "latest" : ""}`}>
              <Check size={12} />
            </span>
            <div>
              <div className="timeline-title">
                <strong>{event.title}</strong>
                <time dateTime={event.at}>{formatDate(event.at)}</time>
              </div>
              <p>{event.note}</p>
              <span className="timeline-actor">{event.actor}</span>
            </div>
          </li>
        ))}
    </ol>
  );
}
export function Modal({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current!;
    const previousFocus = document.activeElement as HTMLElement | null;
    dialog.showModal();
    dialog.querySelector<HTMLTextAreaElement>("textarea")?.focus();
    return () => {
      dialog.close();
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby="dialog-title"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="modal-heading">
        <h2 id="dialog-title">{title}</h2>
        <button
          className="icon-button"
          onClick={onClose}
          aria-label="Đóng hộp thoại"
        >
          <X size={20} />
        </button>
      </div>
      {children}
    </dialog>
  );
}
