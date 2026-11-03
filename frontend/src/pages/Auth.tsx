import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  ArrowRight,
  BookOpen,
  Check,
  Eye,
  EyeOff,
  GraduationCap,
  LoaderCircle,
  LockKeyhole,
  Mail,
  ShieldCheck,
  UserRound,
} from "lucide-react";
import { Link } from "react-router-dom";
import "./auth.css";

const EMAIL_KEY = "dxos-remembered-email-v1";
const validEmail = (value: string) =>
  value.length <= 254 && /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/.test(value);

function rememberedEmail() {
  try {
    const email = localStorage.getItem(EMAIL_KEY) || "";
    return validEmail(email) ? email : "";
  } catch {
    return "";
  }
}

function PasswordField({
  id,
  label,
  hint,
  error,
  registration = false,
}: {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  registration?: boolean;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="auth-field">
      <label htmlFor={id}>{label}</label>
      <div className="auth-input-wrap">
        <LockKeyhole size={18} aria-hidden="true" />
        <input
          id={id}
          name={id}
          type={visible ? "text" : "password"}
          autoComplete={registration ? "new-password" : "current-password"}
          required
          maxLength={128}
          aria-invalid={!!error}
          aria-describedby={
            [hint && `${id}-hint`, error && `${id}-error`]
              .filter(Boolean)
              .join(" ") || undefined
          }
        />
        <button
          type="button"
          className="auth-eye"
          aria-label={`${visible ? "Ẩn" : "Hiện"} ${label.toLowerCase()}`}
          aria-pressed={visible}
          aria-controls={id}
          onClick={() => setVisible(!visible)}
        >
          {visible ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </div>
      {hint && (
        <p className="auth-hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="auth-error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}

export function AuthPage({ mode }: { mode: "login" | "register" }) {
  const registration = mode === "register";
  const [email] = useState(rememberedEmail);
  const [remember, setRemember] = useState(!!email);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [blocked, setBlocked] = useState("");
  const [storageNotice, setStorageNotice] = useState("");
  const [pending, setPending] = useState(false);
  const locked = useRef(false);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    const previousTitle = document.title;
    document.title = `${registration ? "Đăng ký" : "Đăng nhập"} | DX-OS`;
    heading.current?.focus();
    return () => {
      clearTimeout(timer.current);
      document.title = previousTitle;
    };
  }, [registration]);

  function saveEmail(value: string) {
    try {
      if (value) localStorage.setItem(EMAIL_KEY, value);
      else localStorage.removeItem(EMAIL_KEY);
      setStorageNotice("");
    } catch {
      setStorageNotice(
        "Trình duyệt không cho phép cập nhật email đã ghi nhớ. Bạn có thể quản lý dữ liệu trong cài đặt trình duyệt.",
      );
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (locked.current) return;
    const form = event.currentTarget;
    // Credentials stay in the form's transient memory. No auth service or transport is invoked.
    const data = new FormData(form);
    const submittedEmail = String(data.get("email") || "").trim();
    const password = String(data.get("password") || "");
    const next: Record<string, string> = {};
    if (!validEmail(submittedEmail))
      next.email = "Nhập email hợp lệ, ví dụ: ban@example.edu.vn.";
    if (!password || password.length > 128)
      next.password = "Nhập mật khẩu từ 1 đến 128 ký tự.";
    if (registration) {
      const name = String(data.get("fullName") || "").trim();
      if (name.length < 2 || name.length > 100)
        next.fullName = "Họ và tên cần từ 2 đến 100 ký tự.";
      if (password.length < 12 || password.length > 128)
        next.password = "Mật khẩu cần từ 12 đến 128 ký tự.";
      if (!data.get("confirmation") || data.get("confirmation") !== password)
        next.confirmation = "Mật khẩu xác nhận chưa khớp.";
      if (!["student", "lecturer"].includes(String(data.get("accountType"))))
        next.accountType = "Vui lòng chọn Sinh viên hoặc Giảng viên.";
    }
    setErrors(next);
    setBlocked("");
    if (Object.keys(next).length) {
      const firstInvalid = [
        "fullName",
        "email",
        "password",
        "confirmation",
        "accountType",
      ].find((name) => next[name]);
      form.querySelector<HTMLInputElement>(`[name="${firstInvalid}"]`)?.focus();
      return;
    }
    if (!registration) saveEmail(remember ? submittedEmail : "");
    // Clear both secret fields before presenting the local-only blocked state.
    for (const name of ["password", "confirmation"]) {
      const input = form.elements.namedItem(name);
      if (input instanceof HTMLInputElement) input.value = "";
    }
    locked.current = true;
    setPending(true);
    timer.current = setTimeout(() => {
      setPending(false);
      locked.current = false;
      setBlocked(
        `BLOCKED FEATURE · ${registration ? "Đăng ký tài khoản" : "Đăng nhập email/mật khẩu"} chưa khả dụng. Chưa có API Identity được phê duyệt. ${registration ? "Chưa tạo tài khoản." : "Chưa đăng nhập."} Bạn có thể vào phiên Demo để trải nghiệm.`,
      );
    }, 250);
  }

  return (
    <div className="auth-shell">
      <a href="#auth-content" className="skip-link">
        Đến biểu mẫu
      </a>
      <aside
        className="auth-story"
        aria-label="DX-OS University Service Portal"
      >
        <Link
          className="auth-brand"
          to="/login"
          aria-label="DX-OS · Trang đăng nhập"
        >
          <img src="/favicon.svg" alt="" width="42" height="42" />
          <span>
            DX<span className="auth-brand-dash">-</span>OS
            <small>University Service Portal</small>
          </span>
        </Link>
        <div className="auth-story-content">
          <span className="auth-campus">
            <GraduationCap size={19} aria-hidden="true" /> Không gian dịch vụ
            đại học
          </span>
          <h2>
            Kết nối nhu cầu.
            <br />
            Tiếp nối hành trình.
          </h2>
          <p>
            Một nơi để sinh viên và giảng viên gửi yêu cầu hỗ trợ, phối hợp và
            theo dõi tiến độ.
          </p>
          <div className="auth-service-preview">
            <div className="auth-preview-heading">
              <BookOpen size={20} aria-hidden="true" />
              <span>Dịch vụ trong tầm tay</span>
            </div>
            <ul>
              <li>
                <Check size={17} aria-hidden="true" />
                Hỗ trợ CNTT và cơ sở vật chất
              </li>
              <li>
                <Check size={17} aria-hidden="true" />
                Theo dõi từng bước xử lý
              </li>
              <li>
                <Check size={17} aria-hidden="true" />
                Lịch sử yêu cầu rõ ràng
              </li>
            </ul>
            <div className="auth-preview-footer">
              <span className="auth-preview-dot" /> Cùng một trải nghiệm DX-OS
            </div>
          </div>
        </div>
        <p className="auth-story-footer">
          DX-OS Open-Core <span>Cổng dịch vụ đại học</span>
        </p>
      </aside>

      <main className="auth-main" id="auth-content" tabIndex={-1}>
        <div className="auth-topline">
          <span>Bản xem trước giao diện</span>
          <Link to="/session">
            Khám phá Demo <ArrowRight size={15} aria-hidden="true" />
          </Link>
        </div>
        <section className="auth-card" aria-labelledby="auth-title">
          <span className="auth-form-icon">
            <UserRound size={23} aria-hidden="true" />
          </span>
          <h1 id="auth-title" ref={heading} tabIndex={-1}>
            {registration ? "Tạo tài khoản DX-OS" : "Chào mừng trở lại"}
          </h1>
          <p className="auth-intro">
            {registration
              ? "Đăng ký để kết nối với dịch vụ đại học."
              : "Đăng nhập để truy cập không gian dịch vụ của bạn."}
          </p>
          <div className="auth-preview-note">
            <ShieldCheck size={19} aria-hidden="true" />
            <p>
              <strong>Email/mật khẩu chưa khả dụng.</strong> Chỉ xem trước biểu
              mẫu. Không nhập mật khẩu thật.
            </p>
          </div>

          <form
            noValidate
            onSubmit={submit}
            aria-label={registration ? "Đăng ký tài khoản" : "Đăng nhập"}
            aria-busy={pending}
          >
            <fieldset className="auth-fields" disabled={pending}>
              <legend className="sr-only">
                Thông tin {registration ? "đăng ký" : "đăng nhập"}
              </legend>
              {registration && (
                <div className="auth-field">
                  <label htmlFor="fullName">Họ và tên</label>
                  <div className="auth-input-wrap">
                    <UserRound size={18} aria-hidden="true" />
                    <input
                      id="fullName"
                      name="fullName"
                      autoComplete="name"
                      placeholder="Nhập họ và tên"
                      required
                      maxLength={100}
                      aria-invalid={!!errors.fullName}
                      aria-describedby={
                        errors.fullName ? "fullName-error" : undefined
                      }
                    />
                  </div>
                  {errors.fullName && (
                    <p className="auth-error" id="fullName-error">
                      {errors.fullName}
                    </p>
                  )}
                </div>
              )}
              <div className="auth-field">
                <label htmlFor="email">Email</label>
                <div className="auth-input-wrap">
                  <Mail size={18} aria-hidden="true" />
                  <input
                    id="email"
                    name="email"
                    type="email"
                    autoComplete={registration ? "email" : "username"}
                    autoCapitalize="none"
                    spellCheck={false}
                    defaultValue={registration ? "" : email}
                    placeholder="ban@example.edu.vn"
                    required
                    maxLength={254}
                    aria-invalid={!!errors.email}
                    aria-describedby={errors.email ? "email-error" : undefined}
                  />
                </div>
                {errors.email && (
                  <p className="auth-error" id="email-error">
                    {errors.email}
                  </p>
                )}
              </div>
              <PasswordField
                id="password"
                label="Mật khẩu"
                registration={registration}
                hint={
                  registration
                    ? "Từ 12 đến 128 ký tự. Chỉ dùng dữ liệu giả để xem trước."
                    : undefined
                }
                error={errors.password}
              />
              {registration ? (
                <>
                  <PasswordField
                    id="confirmation"
                    label="Xác nhận mật khẩu"
                    registration
                    error={errors.confirmation}
                  />
                  <fieldset
                    className="auth-account-types"
                    aria-describedby={
                      errors.accountType
                        ? "accountType-error"
                        : "accountType-hint"
                    }
                    aria-invalid={!!errors.accountType}
                  >
                    <legend>Bạn là</legend>
                    <label>
                      <input
                        type="radio"
                        name="accountType"
                        value="student"
                        defaultChecked
                      />
                      <GraduationCap size={18} aria-hidden="true" />
                      Sinh viên
                    </label>
                    <label>
                      <input type="radio" name="accountType" value="lecturer" />
                      <BookOpen size={18} aria-hidden="true" />
                      Giảng viên
                    </label>
                  </fieldset>
                  <p className="auth-hint" id="accountType-hint">
                    Chỉ dành cho sinh viên và giảng viên. Quyền xử lý do đơn vị
                    quản trị cấp.
                  </p>
                  {errors.accountType && (
                    <p className="auth-error" id="accountType-error">
                      {errors.accountType}
                    </p>
                  )}
                </>
              ) : (
                <div className="auth-options">
                  <label>
                    <input
                      type="checkbox"
                      checked={remember}
                      onChange={(event) => {
                        setRemember(event.target.checked);
                        if (!event.target.checked) saveEmail("");
                      }}
                    />
                    Ghi nhớ email
                  </label>
                  <button
                    className="auth-text-button"
                    type="button"
                    onClick={() =>
                      setBlocked(
                        "BLOCKED FEATURE · Quên mật khẩu chưa khả dụng. Chờ API khôi phục tài khoản được phê duyệt.",
                      )
                    }
                  >
                    Quên mật khẩu?
                  </button>
                </div>
              )}
              <button
                type="submit"
                className="button primary auth-submit"
                disabled={pending}
              >
                {pending ? (
                  <>
                    <LoaderCircle size={18} aria-hidden="true" />
                    Đang kiểm tra biểu mẫu…
                  </>
                ) : (
                  <>
                    {registration ? "Đăng ký tài khoản" : "Đăng nhập"}
                    <ArrowRight size={18} aria-hidden="true" />
                  </>
                )}
              </button>
            </fieldset>
            {pending && (
              <p className="sr-only" role="status">
                Đang kiểm tra biểu mẫu…
              </p>
            )}
            {blocked && (
              <div className="auth-blocked" role="alert">
                {blocked}
              </div>
            )}
            {storageNotice && (
              <p className="auth-hint" role="status">
                {storageNotice}
              </p>
            )}
          </form>
          <p className="auth-switch">
            {registration ? "Đã có tài khoản?" : "Chưa có tài khoản?"}{" "}
            <Link to={registration ? "/login" : "/register"}>
              {registration ? "Đăng nhập" : "Đăng ký"}
            </Link>
          </p>
          <div className="auth-divider">
            <span>Trải nghiệm bằng dữ liệu mô phỏng</span>
          </div>
          <Link to="/session" className="button secondary auth-demo">
            Vào phiên Demo <ArrowRight size={17} aria-hidden="true" />
          </Link>
          <p className="auth-demo-hint">
            Không cần tài khoản. Không tạo phiên đăng nhập thật.
          </p>
        </section>
        <footer className="auth-footer">
          <LockKeyhole size={14} aria-hidden="true" />
          DX-OS · University Service Portal
        </footer>
      </main>
    </div>
  );
}
