import { useEffect, useState } from "react";
import {
  Activity,
  ArrowUpRight,
  Building2,
  ChevronDown,
  Command,
  FileText,
  LayoutDashboard,
  LifeBuoy,
  Menu,
  Plus,
  Search,
  ShieldCheck,
  Workflow,
  X,
} from "lucide-react";
import {
  Link,
  NavLink,
  Navigate,
  Outlet,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { PortalProvider, usePortal } from "./context";
import { Dashboard } from "./pages/Dashboard";
import { Requests } from "./pages/Requests";
import { NewRequest } from "./pages/NewRequest";
import { RequestDetail } from "./pages/RequestDetail";
import { Session, Integration } from "./pages/System";
import { Empty } from "./components/ui";
import { AuthPage } from "./pages/Auth";
import { HelpdeskPortal } from "./features/helpdesk/HelpdeskPortal";
import type { MvpHelpdeskRequestPort } from "./services/mvp/helpdeskPortalPort";

const navItems = [
  { to: "/", label: "Tổng quan", icon: LayoutDashboard, end: true },
  { to: "/requests", label: "Yêu cầu của tôi", icon: FileText },
  { to: "/approvals", label: "Bàn xử lý", icon: Workflow },
  { to: "/history", label: "Lịch sử hoạt động", icon: Activity },
];
function Layout() {
  const { role, helpdeskService } = usePortal();
  const [menu, setMenu] = useState(false);
  const [query, setQuery] = useState("");
  const [mobile, setMobile] = useState(
    () => matchMedia("(max-width: 760px)").matches,
  );
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    const media = matchMedia("(max-width: 760px)");
    const update = () => {
      setMobile(media.matches);
      setMenu(false);
    };
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    if (!menu || !mobile) return;
    document.querySelector<HTMLAnchorElement>(".sidebar a")?.focus();
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMenu(false);
        requestAnimationFrame(() =>
          document.querySelector<HTMLButtonElement>(".menu-button")?.focus(),
        );
      }
    };
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [menu, mobile]);
  useEffect(() => {
    setMenu(false);
    document.getElementById("main-content")?.focus();
  }, [location.pathname]);
  return (
    <div className="app-shell">
      <a href="#main-content" className="skip-link">
        Đến nội dung chính
      </a>
      {menu && (
        <button
          className="sidebar-overlay"
          aria-label="Đóng điều hướng"
          onClick={() => setMenu(false)}
        />
      )}
      <aside
        className={`sidebar ${menu ? "open" : ""}`}
        aria-label="Điều hướng chính"
        inert={mobile && !menu}
      >
        <Link to="/" className="brand">
          <img src="/favicon.svg" alt="" width="35" height="35" />
          <span>
            DX<span className="brand-dash">-</span>OS
            <small>University Service Portal</small>
          </span>
          <span className="brand-version">β</span>
        </Link>
        <div className="workspace">
          <span className="workspace-mark">
            <Building2 size={18} />
          </span>
          <div>
            <strong>University Workspace</strong>
            <small>Cổng dịch vụ đại học · Demo</small>
          </div>
          <ChevronDown size={14} />
        </div>
        <p className="nav-label">Dịch vụ đại học</p>
        <nav>
          {helpdeskService && (
            <NavLink to="/mvp/helpdesk" className="nav-item">
              <Workflow size={19} />
              <span>IT Helpdesk MVP · kiểm thử</span>
            </NavLink>
          )}
          {navItems.map(({ to, label, icon: ItemIcon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                isActive ? "nav-item active" : "nav-item"
              }
            >
              <ItemIcon size={19} strokeWidth={1.7} />
              <span>{label}</span>
              {to === "/approvals" && (
                <span className="nav-pill" aria-hidden="true">
                  Demo
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <p className="nav-label system-label">Hệ thống</p>
        <nav>
          <NavLink to="/integration" className="nav-item">
            <ShieldCheck size={19} />
            <span>Trạng thái tích hợp</span>
            <span className="status-dot" />
          </NavLink>
          <NavLink to="/session" className="nav-item">
            <Command size={19} />
            <span>Trạng thái phiên</span>
          </NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="demo-card">
            <span className="demo-card-icon">
              <Workflow size={20} />
            </span>
            <strong>Một nền tảng. Nhiều dịch vụ.</strong>
            <p>Hai quy trình mẫu, cùng trải nghiệm DX-Core.</p>
            <Link to="/integration">
              Khám phá kiến trúc <ArrowUpRight size={14} />
            </Link>
          </div>
          <Link to="/session" className="profile">
            <span className="avatar">{role === "processor" ? "XB" : "NÁ"}</span>
            <span>
              <strong>
                {role === "processor"
                  ? "Lê Xuân Bảo"
                  : role
                    ? "Ngọc Ánh"
                    : "Khách trải nghiệm"}
              </strong>
              <small>
                {role === "processor"
                  ? "Người xử lý · Demo"
                  : role
                    ? "Người gửi yêu cầu · Demo"
                    : "Chưa vào phiên demo"}
              </small>
            </span>
            <ChevronDown size={14} />
          </Link>
        </div>
      </aside>
      <div className="main-shell" inert={mobile && menu}>
        <header className="topbar">
          <div className="breadcrumb">
            <button
              className="icon-button menu-button"
              onClick={() => setMenu(!menu)}
              aria-label={menu ? "Đóng menu" : "Mở menu"}
              aria-expanded={menu}
            >
              {menu ? <X size={21} /> : <Menu size={21} />}
            </button>
            <span>Cổng dịch vụ đại học</span>
            <span className="breadcrumb-divider">/</span>
            <strong>
              {location.pathname === "/"
                ? "Tổng quan"
                : location.pathname.includes("new")
                  ? "Tạo yêu cầu"
                  : location.pathname.includes("integration")
                    ? "Tích hợp"
                    : location.pathname.includes("session")
                      ? "Phiên làm việc"
                      : "Không gian làm việc"}
            </strong>
          </div>
          <div className="topbar-right">
            <form
              className="global-search"
              onSubmit={(e) => {
                e.preventDefault();
                navigate(`/requests?q=${encodeURIComponent(query)}`);
              }}
            >
              <Search size={16} />
              <input
                aria-label="Tìm kiếm toàn cục"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Tìm yêu cầu…"
              />
              <kbd>↵</kbd>
            </form>
            <span className="environment">
              <span />
              MOCK / DEVELOPMENT
            </span>
            <Link
              className="icon-button help-link"
              to="/integration"
              aria-label="Thông tin tích hợp"
            >
              <LifeBuoy size={20} />
            </Link>
          </div>
        </header>
        <main id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
        <footer className="footer">
          <span>
            DX-OS Open-Core <span className="footer-separator">/</span> Internal
            Service Portal
          </span>
          <span>Ứng dụng Tầng 2 · Prototype cho OLP 2026</span>
        </footer>
      </div>
    </div>
  );
}
function Protected() {
  const { role } = usePortal();
  return role ? <Outlet /> : <Navigate to="/session" replace />;
}
export function App({
  helpdeskService,
}: { helpdeskService?: MvpHelpdeskRequestPort } = {}) {
  return (
    <PortalProvider helpdeskService={helpdeskService}>
      <Routes>
        <Route path="/login" element={<AuthPage key="login" mode="login" />} />
        <Route
          path="/register"
          element={<AuthPage key="register" mode="register" />}
        />
        <Route element={<Layout />}>
          <Route path="/session" element={<Session />} />
          <Route path="/integration" element={<Integration />} />
          <Route element={<Protected />}>
            <Route path="/mvp/helpdesk/*" element={<HelpdeskPortal />} />
            <Route index element={<Dashboard />} />
            <Route path="/requests" element={<Requests />} />
            <Route path="/requests/new" element={<NewRequest />} />
            <Route path="/requests/:id" element={<RequestDetail />} />
            <Route path="/approvals" element={<Requests mode="approvals" />} />
            <Route path="/history" element={<Requests mode="history" />} />
          </Route>
          <Route
            path="*"
            element={
              <Empty
                title="Không tìm thấy trang"
                description="Đường dẫn này không tồn tại trong portal."
              >
                <Link className="button primary" to="/">
                  Về tổng quan
                </Link>
              </Empty>
            }
          />
        </Route>
      </Routes>
    </PortalProvider>
  );
}
