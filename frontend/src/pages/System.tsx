import { useState } from "react";
import {
  ArrowRight,
  Check,
  Code2,
  Database,
  Fingerprint,
  GitBranch,
  Globe,
  LockKeyhole,
  LogOut,
  ShieldCheck,
  Workflow,
} from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { usePortal } from "../context";
import type { DemoRole, Fault } from "../domain/request";
import { demoControls } from "../services/portalService";
import { HttpApiClient } from "../services/HttpApiClient";
import { safeError } from "../services/RequestService";
import { PageHeading } from "../components/ui";

export function Session() {
  const { role, setRole, notify, sessionExpired, expireSession } = usePortal();
  const navigate = useNavigate();
  const [choice, setChoice] = useState<DemoRole>(role || "employee");
  const expired =
    sessionExpired ||
    new URLSearchParams(location.search).get("expired") === "1";
  return (
    <>
      <PageHeading
        eyebrow="DX-OS Internal Service Portal"
        title="Không gian dịch vụ của đội ngũ"
        description="Trải nghiệm cách một nền tảng kết nối mọi nhu cầu hỗ trợ nội bộ."
      />
      <div className="session-layout">
        <section className="session-story">
          <span className="session-symbol">
            <Workflow size={44} strokeWidth={1.2} />
          </span>
          <h2>
            Công việc thông suốt.
            <br />
            Đội ngũ kết nối.
          </h2>
          <p>
            Một nơi để gửi yêu cầu, phối hợp xử lý và theo dõi từng bước tiến.
          </p>
          <div className="session-benefits">
            <span>
              <Check size={17} />
              Hỗ trợ CNTT & cơ sở vật chất
            </span>
            <span>
              <Check size={17} />
              Phê duyệt và xử lý nhất quán
            </span>
            <span>
              <Check size={17} />
              Lịch sử rõ ràng, dễ theo dõi
            </span>
          </div>
          <div className="session-signature">
            TRICORE <span>Powered by DX-OS Open-Core</span>
          </div>
        </section>
        <section className="panel session-card">
          <span className="service-icon indigo">
            <Fingerprint size={27} />
          </span>
          <h2>{role ? "Phiên demo đang hoạt động" : "Bắt đầu trải nghiệm"}</h2>
          <p className="muted">
            Chọn vai trò để khám phá portal bằng dữ liệu mô phỏng.
          </p>
          {expired && (
            <div className="inline-notice" role="alert">
              401 mô phỏng · Phiên đã hết hạn. Chọn vai trò để vào lại demo.
            </div>
          )}
          <div className="session-status">
            <span className={`status-dot ${role ? "green" : ""}`} />
            {role
              ? "Đã vào phiên demo · Không có token xác thực"
              : "Chưa đăng nhập · Chế độ demo"}
          </div>
          <fieldset className="role-picker">
            <legend>Vai trò trải nghiệm</legend>
            <label className={choice === "employee" ? "selected" : ""}>
              <input
                type="radio"
                name="role"
                checked={choice === "employee"}
                onChange={() => setChoice("employee")}
              />
              <span>
                <strong>Nhân viên</strong>
                <small>Ngọc Ánh · Tạo và theo dõi yêu cầu của mình</small>
              </span>
            </label>
            <label className={choice === "processor" ? "selected" : ""}>
              <input
                type="radio"
                name="role"
                checked={choice === "processor"}
                onChange={() => setChoice("processor")}
              />
              <span>
                <strong>Người xử lý</strong>
                <small>Lê Xuân Bảo · Phê duyệt và xử lý mẫu</small>
              </span>
            </label>
          </fieldset>
          <button
            className="button primary full-width"
            onClick={() => {
              setRole(choice);
              notify("Đã vào phiên demo. Đây không phải đăng nhập SSO thực.");
              navigate("/");
            }}
          >
            {role ? "Tiếp tục với vai trò đã chọn" : "Vào phiên demo"}
            <ArrowRight size={17} />
          </button>
          {role && (
            <div className="session-tools">
              <button
                className="text-link"
                onClick={() => {
                  setRole(null);
                  notify("Đã kết thúc phiên demo.");
                }}
              >
                <LogOut size={15} />
                Kết thúc phiên
              </button>
              <button
                className="text-link"
                onClick={() => {
                  expireSession();
                  navigate("/session?expired=1");
                }}
              >
                Mô phỏng hết hạn
              </button>
            </div>
          )}
          <div className="blocked-login">
            <LockKeyhole size={16} />
            <span>
              SSO / OIDC · <strong>BLOCKED API</strong>
              <small>Chờ Identity/Gateway và security gate #22/#23/#24.</small>
            </span>
          </div>
          <p className="mock-footnote">
            MOCK / DEVELOPMENT · Vai trò này không chứng minh quyền truy cập
            thực.
          </p>
          <Link className="text-link" to="/login">
            Xem giao diện đăng nhập email/mật khẩu <ArrowRight size={14} />
          </Link>
        </section>
      </div>
    </>
  );
}

const integrations = [
  {
    icon: Fingerprint,
    name: "Identity / SSO",
    label: "Phiên demo, chưa xác minh danh tính",
    issue: "#24",
    detail:
      "Login thực cần OIDC Authorization Code + PKCE và boundary xác thực được phê duyệt.",
  },
  {
    icon: Globe,
    name: "API Gateway",
    label: "Không có HTTP request tới Core",
    issue: "#22 / #23",
    detail:
      "Chờ route registration, trusted context và kiểm thử security filter chain.",
  },
  {
    icon: Workflow,
    name: "Workflow Engine",
    label: "Controller chưa registered",
    issue: "#22 / #23 / #24",
    detail:
      "Create, activate, transition, snapshot và history đều bị chặn. Không tự retry lệnh ghi.",
  },
  {
    icon: Database,
    name: "Data Management",
    label: "Dữ liệu chỉ lưu trong bộ nhớ",
    issue: "#25 / #26",
    detail:
      "Portal không truy cập database hoặc storage trực tiếp. Attachments là BLOCKED FEATURE.",
  },
  {
    icon: Code2,
    name: "OpenAPI / DTO mapping",
    label: "Chưa phê duyệt contract HTTP cho portal",
    issue: "#37",
    detail:
      "Request model hiện là view model mock; không khẳng định tương thích DTO backend.",
  },
];
export function Integration() {
  const { notify, refresh } = usePortal();
  const [fault, setFault] = useState<Fault>("network");
  const [liveMessage, setLiveMessage] = useState("");
  return (
    <>
      <PageHeading
        eyebrow="Nền tảng & kết nối"
        title="Trạng thái tích hợp"
        description="Minh bạch từng kết nối. Giao diện mock đã sẵn sàng, HTTP end-to-end còn bị chặn."
      />
      <section className="integration-banner">
        <span className="service-icon teal">
          <ShieldCheck size={28} />
        </span>
        <div>
          <h2>MOCK / DEVELOPMENT</h2>
          <p>
            Ứng dụng Tầng 2 độc lập. Chưa có tích hợp LIVE và chưa đạt
            Definition of Done của Issue #39.
          </p>
        </div>
        <span className="blocked-tag">LIVE BLOCKED</span>
      </section>
      <section className="panel architecture-strip">
        <div>
          <Code2 size={24} />
          <strong>Portal · Tầng 2</strong>
          <small>UI → RequestService → Mock fixtures</small>
        </div>
        <span className="architecture-arrow">→</span>
        <div className="architecture-gate">
          <LockKeyhole size={23} />
          <strong>Security gate</strong>
          <small>HTTP / API đang chặn</small>
        </div>
        <span className="architecture-arrow">→</span>
        <div>
          <Database size={24} />
          <strong>DX-Core · Tầng 1</strong>
          <small>Identity · Gateway · Workflow · Data</small>
        </div>
      </section>
      <div className="integration-list">
        {integrations.map((item) => (
          <section className="panel integration-item" key={item.name}>
            <span className="integration-icon">
              <item.icon size={23} />
            </span>
            <div>
              <h2>{item.name}</h2>
              <strong>{item.label}</strong>
              <p>{item.detail}</p>
            </div>
            <div className="integration-state">
              <span className="blocked-tag">BLOCKED API</span>
              <span>{item.issue}</span>
            </div>
          </section>
        ))}
      </div>
      <div className="integration-tools">
        <section className="panel developer-panel">
          <h2>
            <GitBranch size={19} />
            Kiểm tra UI trong môi trường demo
          </h2>
          <p>
            Áp dụng một lỗi cho lần gọi mock tiếp theo. Đây là mô phỏng, không
            phải bằng chứng security end-to-end.
          </p>
          <label className="field">
            Kịch bản lỗi
            <select
              value={fault}
              onChange={(e) => setFault(e.target.value as Fault)}
            >
              <option value="network">Mất kết nối mô phỏng</option>
              <option value="401">401 · Phiên hết hạn</option>
              <option value="403">403 · Không đủ quyền</option>
              <option value="404">404 · Không tìm thấy / không khả dụng</option>
              <option value="unknown">
                Kết quả ghi chưa xác định (chỉ thao tác ghi)
              </option>
            </select>
          </label>
          <button
            className="button secondary"
            onClick={() => {
              demoControls.setFault(fault);
              notify("Đã đặt lỗi cho lần gọi mock tiếp theo.");
            }}
          >
            Áp dụng lỗi mô phỏng
          </button>
          <Link className="text-link" to="/requests">
            Mở danh sách yêu cầu <ArrowRight size={14} />
          </Link>
          <p className="field-hint">
            Với lỗi kết quả ghi chưa xác định: áp dụng lỗi, mở form tạo yêu cầu
            trong cùng tab rồi gửi. Thao tác ghi sẽ không tự gửi lại.
          </p>
        </section>
        <section className="panel developer-panel">
          <h2>
            <LockKeyhole size={19} />
            LIVE integration mặc định đóng
          </h2>
          <p>
            Không có biến môi trường hoặc công tắc UI để mở LIVE. Cần review
            contract và security trước khi triển khai HTTP adapter.
          </p>
          <button
            className="button secondary"
            onClick={async () => {
              try {
                await new HttpApiClient().request("GET", "/");
              } catch (error) {
                setLiveMessage(safeError(error));
              }
            }}
          >
            Kiểm tra chặn LIVE
          </button>
          {liveMessage && (
            <p className="inline-notice" role="status">
              {liveMessage}
            </p>
          )}
          <button
            className="text-link reset-demo"
            onClick={() => {
              demoControls.reset();
              refresh();
              notify("Đã khôi phục dữ liệu demo ban đầu.");
            }}
          >
            Khôi phục dữ liệu demo
          </button>
          <p className="mock-footnote">
            Không tải tệp · Không lưu token · Không có credentials
          </p>
        </section>
      </div>
    </>
  );
}
