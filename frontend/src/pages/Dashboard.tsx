import {
  ArrowRight,
  ArrowUpRight,
  Building2,
  CheckCircle2,
  Clock3,
  Headphones,
  Layers3,
  Plus,
  Workflow,
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

/** University-focused enterprise dashboard. All figures derive from mock requests. */
export function Dashboard() {
  const { role } = usePortal();
  const { data, loading, error, reload } = useRequests();
  const pending = data.filter((item) => item.status === "pending").length;
  const processing = data.filter((item) => item.status === "processing").length;
  const completed = data.filter((item) => item.status === "completed").length;
  const recent = data
    .flatMap((item) =>
      item.timeline.map((event) => ({
        ...event,
        title: `${event.title} · ${item.id}`,
      })),
    )
    .sort((a, b) => b.at.localeCompare(a.at))
    .slice(0, 4);

  // Each bucket is computed from mock fixtures, never from fabricated KPI numbers.
  const days = Array.from({ length: 7 }, (_, index) => {
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    start.setDate(start.getDate() - (6 - index));
    const count = data.filter((item) => {
      const date = new Date(item.createdAt);
      return date >= start && date < new Date(start.getTime() + 86400000);
    }).length;
    return {
      label: new Intl.DateTimeFormat("vi-VN", { day: "2-digit", month: "2-digit" }).format(start),
      count,
    };
  });
  const ceiling = Math.max(1, ...days.map((day) => day.count));

  return (
    <>
      <PageHeading
        eyebrow="DX-OS / UNIVERSITY SERVICE PORTAL"
        title={`Chào buổi sáng, ${role === "processor" ? "cán bộ xử lý" : "bạn"} 👋`}
        description="Quản lý dịch vụ hỗ trợ học tập và vận hành trường đại học trên một nền tảng."
      >
        <Link to="/requests/new" className="button primary">
          <Plus size={18} />
          Tạo yêu cầu mới
        </Link>
      </PageHeading>

      <div className="campus-dashboard-intro">
        <p>
          Tổng quan các yêu cầu hỗ trợ CNTT và cơ sở vật chất.
          Số liệu bên dưới được tính từ dữ liệu mô phỏng trong phiên hiện tại.
        </p>
        <span className="intro-chip">MOCK / DEVELOPMENT</span>
      </div>

      {loading ? (
        <Loading />
      ) : error ? (
        <ErrorState message={error} onRetry={reload} />
      ) : (
        <>
          <section className="campus-metrics" aria-label="Thống kê yêu cầu demo">
            <div className="metric">
              <div><span>Tổng số yêu cầu</span><Layers3 size={19} /></div>
              <strong>{String(data.length).padStart(2, "0")}</strong>
              <p>Tất cả dịch vụ trong phiên demo</p>
            </div>
            <div className="metric">
              <div><span>Chờ phê duyệt</span><Clock3 size={19} /></div>
              <strong>{String(pending).padStart(2, "0")}</strong>
              <p>Các yêu cầu đang đợi quyết định</p>
            </div>
            <div className="metric">
              <div><span>Đang xử lý</span><Workflow size={19} /></div>
              <strong>{String(processing).padStart(2, "0")}</strong>
              <p>Đang được nhóm phụ trách hỗ trợ</p>
            </div>
            <div className="metric">
              <div><span>Đã hoàn thành</span><CheckCircle2 size={19} /></div>
              <strong>{String(completed).padStart(2, "0")}</strong>
              <p>{data.length ? Math.round((completed / data.length) * 100) : 0}% số yêu cầu hiển thị</p>
            </div>
          </section>

          <div className="campus-dashboard-grid">
            <section className="panel campus-chart-panel" aria-label="Biểu đồ yêu cầu bảy ngày">
              <div className="panel-heading">
                <div>
                  <h2>Xu hướng tiếp nhận yêu cầu</h2>
                  <p>Yêu cầu mới trong 7 ngày gần nhất · MOCK DATA</p>
                </div>
                <Clock3 size={18} aria-hidden="true" />
              </div>
              <div className="campus-chart-meta">
                <span className="campus-overline">Hoạt động gần đây</span>
                <span className="muted">7 ngày</span>
              </div>
              <div className="campus-chart-count">
                <strong>{days.reduce((sum, day) => sum + day.count, 0)}</strong>
                <span>yêu cầu được tạo trong giai đoạn</span>
              </div>
              <div className="campus-chart" role="img" aria-label={`Số yêu cầu trong 7 ngày gần nhất: ${days.map((day) => `${day.label}: ${day.count}`).join("; ")}`}>
                {days.map((day) => (
                  <div className="campus-chart-bar" key={day.label} aria-hidden="true">
                    <div className="campus-chart-track">
                      <div className="campus-chart-fill" style={{ height: `${Math.max(3, (day.count / ceiling) * 100)}%` }} />
                    </div>
                    <span className="campus-chart-label">{day.label}</span>
                  </div>
                ))}
              </div>
              <div className="campus-chart-legend">Số yêu cầu mới · dữ liệu mô phỏng</div>
            </section>

            <section className="panel campus-activity-panel">
              <div className="panel-heading">
                <div><h2>Hoạt động mới nhất</h2><p>Nhật ký từ các yêu cầu demo</p></div>
                <Link className="text-link" to="/history">Xem tất cả <ArrowRight size={15} /></Link>
              </div>
              <Timeline events={recent} />
            </section>
          </div>

          <div className="campus-services-header">
            <div><h2>Dịch vụ đại học</h2><p>Hai nhóm dịch vụ minh họa trên cùng cấu trúc DX-Core</p></div>
            <Link className="text-link" to="/integration">Kiến trúc tích hợp <ArrowUpRight size={15} /></Link>
          </div>
          <div className="campus-service-grid">
            <Link to="/requests/new?kind=it" className="campus-service-card">
              <span className="service-icon indigo"><Headphones size={23} /></span>
              <span><strong>Hỗ trợ CNTT</strong><small>Wi-Fi, phòng máy, thiết bị và tài khoản học tập</small></span>
              <ArrowUpRight size={18} />
            </Link>
            <Link to="/requests/new?kind=facility" className="campus-service-card">
              <span className="service-icon teal"><Building2 size={23} /></span>
              <span><strong>Cơ sở vật chất</strong><small>Phòng học, giảng đường, máy chiếu và hạ tầng</small></span>
              <ArrowUpRight size={18} />
            </Link>
          </div>

          <section className="panel recent-panel campus-recent">
            <div className="panel-heading">
              <div>
                <h2>Yêu cầu gần đây <span className="count-badge">{data.length}</span></h2>
                <p>Theo dõi trạng thái các yêu cầu trong không gian demo.</p>
              </div>
              <Link className="text-link" to="/requests">Xem tất cả <ArrowRight size={15} /></Link>
            </div>
            <RequestTable items={data.slice(0, 5)} compact />
          </section>
        </>
      )}
    </>
  );
}
