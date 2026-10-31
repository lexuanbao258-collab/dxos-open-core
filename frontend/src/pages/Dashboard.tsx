import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  Building2,
  CheckCheck,
  Clock3,
  Headphones,
  Layers3,
  Plus,
  Sparkles,
} from "lucide-react";
import { Link } from "react-router-dom";
import { usePortal } from "../context";
import { useRequests } from "../hooks/useRequests";
import {
  ErrorState,
  Loading,
  PageHeading,
  RequestTable,
  Timeline,
} from "../components/ui";

export function Dashboard() {
  const { role } = usePortal();
  const { data, loading, error, reload } = useRequests();
  const completed = data.filter((r) => r.status === "completed").length;
  const processing = data.filter((r) => r.status === "processing").length;
  const pending = data.filter((r) => r.status === "pending").length;
  const recent = data
    .flatMap((r) =>
      r.timeline.map((event) => ({
        ...event,
        title: `${event.title} · ${r.id}`,
      })),
    )
    .sort((a, b) => b.at.localeCompare(a.at))
    .slice(0, 3);
  return (
    <>
      <PageHeading
        eyebrow="Không gian làm việc của bạn"
        title={`Chào buổi sáng, ${role === "processor" ? "Bảo" : "Ánh"} 👋`}
        description="Mọi yêu cầu, một nơi quản lý. Cùng bắt đầu một ngày làm việc hiệu quả."
      >
        <Link to="/requests/new" className="button primary">
          <Plus size={18} />
          Tạo yêu cầu mới
        </Link>
      </PageHeading>
      <div className="overview-layout">
        <section className="welcome-panel">
          <div className="welcome-copy">
            <span className="welcome-label">
              <span /> Dịch vụ nội bộ, liền mạch hơn
            </span>
            <h2>
              Bớt chờ đợi.
              <br />
              Thêm thời gian sáng tạo.
            </h2>
            <p>
              Từ hỗ trợ công nghệ đến không gian làm việc,
              <br className="desktop-break" /> đội ngũ của bạn luôn sẵn sàng hỗ
              trợ.
            </p>
            <Link className="welcome-link" to="/requests/new">
              Bạn cần hỗ trợ gì hôm nay? <ArrowRight size={17} />
            </Link>
          </div>
          <div className="portal-illustration" aria-hidden="true">
            <div className="orbit orbit-one" />
            <div className="orbit orbit-two" />
            <div className="illustration-core">
              <Layers3 size={43} strokeWidth={1.3} />
              <span>DX-Core</span>
            </div>
            <span className="floating-node node-it">
              <Headphones size={23} />
            </span>
            <span className="floating-node node-facility">
              <Building2 size={23} />
            </span>
            <span className="floating-node node-check">
              <CheckCheck size={21} />
            </span>
            <span className="illustration-caption">
              Kết nối nhu cầu. Tạo giá trị.
            </span>
          </div>
        </section>
        <section className="quick-services">
          <div className="section-title">
            <h2>Dịch vụ nhanh</h2>
            <span>02 dịch vụ</span>
          </div>
          <Link className="service-link" to="/requests/new?kind=it">
            <span className="service-icon indigo">
              <Headphones size={23} />
            </span>
            <span>
              <strong>Hỗ trợ CNTT</strong>
              <small>Thiết bị, tài khoản & phần mềm</small>
            </span>
            <ArrowUpRight size={18} />
          </Link>
          <Link className="service-link" to="/requests/new?kind=facility">
            <span className="service-icon teal">
              <Building2 size={23} />
            </span>
            <span>
              <strong>Cơ sở vật chất</strong>
              <small>Không gian & tiện ích văn phòng</small>
            </span>
            <ArrowUpRight size={18} />
          </Link>
          <p className="quick-note">
            <Sparkles size={14} />
            Cùng một nền tảng DX-Core
          </p>
        </section>
      </div>
      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <section className="metrics" aria-label="Thống kê yêu cầu">
            <div className="metric">
              <div>
                <span>Tổng yêu cầu</span>
                <Layers3 size={18} />
              </div>
              <strong>{String(data.length).padStart(2, "0")}</strong>
              <p>Trong không gian demo của bạn</p>
              <div className="mini-bars" aria-hidden="true">
                {[35, 53, 40, 67, 55, 78, 65, 90, 75, 100].map((n, i) => (
                  <i key={i} style={{ height: `${n}%` }} />
                ))}
              </div>
            </div>
            <div className="metric">
              <div>
                <span>Chờ phê duyệt</span>
                <Clock3 size={18} />
              </div>
              <strong>
                {String(pending).padStart(2, "0")}
                <small>yêu cầu</small>
              </strong>
              <p>
                <span className="metric-dot amber" />
                Đang chờ người phụ trách
              </p>
            </div>
            <div className="metric">
              <div>
                <span>Đang xử lý</span>
                <Headphones size={18} />
              </div>
              <strong>
                {String(processing).padStart(2, "0")}
                <small>yêu cầu</small>
              </strong>
              <p>
                <span className="metric-dot indigo-dot" />
                Đội ngũ đang hỗ trợ bạn
              </p>
            </div>
            <div className="metric">
              <div>
                <span>Đã hoàn thành</span>
                <CheckCheck size={18} />
              </div>
              <strong>
                {String(completed).padStart(2, "0")}
                <small>yêu cầu</small>
              </strong>
              <p className="metric-success">
                <ArrowDownRight size={14} />
                {data.length ? Math.round((completed / data.length) * 100) : 0}%
                yêu cầu trong demo
              </p>
            </div>
          </section>
          <section className="panel recent-panel">
            <div className="panel-heading">
              <div>
                <h2>
                  Yêu cầu gần đây{" "}
                  <span className="count-badge">{data.length}</span>
                </h2>
                <p>Theo dõi tiến độ các yêu cầu của bạn.</p>
              </div>
              <Link className="text-link" to="/requests">
                Xem tất cả <ArrowRight size={15} />
              </Link>
            </div>
            <RequestTable items={data.slice(0, 5)} compact />
          </section>
          <div className="dashboard-bottom">
            <section className="panel activity-panel">
              <div className="panel-heading">
                <h2>Hoạt động mới nhất</h2>
                <Link className="text-link" to="/history">
                  Xem lịch sử <ArrowRight size={15} />
                </Link>
              </div>
              <Timeline events={recent} />
            </section>
            <section className="platform-note">
              <span className="service-icon teal">
                <Layers3 size={24} />
              </span>
              <h2>
                Xây một lần.
                <br />
                Phục vụ nhiều quy trình.
              </h2>
              <p>
                IT Support và Facility dùng chung giao diện, mô hình yêu cầu và
                lịch sử xử lý.
              </p>
              <Link className="text-link" to="/integration">
                Tìm hiểu trạng thái tích hợp <ArrowUpRight size={15} />
              </Link>
              <span className="mock-footnote">
                MOCK DATA · Chưa tích hợp HTTP thực
              </span>
            </section>
          </div>
        </>
      )}
    </>
  );
}
