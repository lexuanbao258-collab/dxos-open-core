import { useRef, useState, type FormEvent } from "react";
import {
  ArrowLeft,
  ArrowRight,
  Building2,
  Headphones,
  Info,
  LoaderCircle,
  LockKeyhole,
  Paperclip,
  ShieldCheck,
} from "lucide-react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import {
  kindLabels,
  validateRequest,
  type CreateRequestInput,
  type RequestKind,
} from "../domain/request";
import { usePortal } from "../context";
import { useSessionOperation } from "../hooks/useSessionOperation";
import { PortalError, safeError } from "../services/RequestService";
import { ErrorState, PageHeading } from "../components/ui";

export function NewRequest() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { role, refresh, notify, expireSession, requestService } = usePortal();
  const captureOperation = useSessionOperation();
  const [input, setInput] = useState<CreateRequestInput>({
    kind: params.get("kind") === "facility" ? "facility" : "it",
    title: "",
    description: "",
    priority: "normal",
    location: "",
  });
  const [errors, setErrors] = useState<ReturnType<typeof validateRequest>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [uncertain, setUncertain] = useState(false);
  const lock = useRef(false);
  const formRef = useRef<HTMLFormElement>(null);
  function update<K extends keyof CreateRequestInput>(
    key: K,
    value: CreateRequestInput[K],
  ) {
    setInput((i) => ({ ...i, [key]: value }));
    setErrors((e) => ({ ...e, [key]: undefined }));
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (lock.current || !role || uncertain) return;
    const problems = validateRequest(input);
    setErrors(problems);
    if (Object.keys(problems).length) {
      const field = Object.keys(problems)[0];
      (
        formRef.current?.elements.namedItem(field) as HTMLElement | null
      )?.focus();
      return;
    }
    lock.current = true;
    setBusy(true);
    setError("");
    const operation = captureOperation();
    try {
      const created = await requestService.create(input, role);
      if (!operation.canUpdate()) return;
      refresh();
      notify("Đã tạo yêu cầu demo. Dữ liệu được lưu trong bộ nhớ.");
      navigate(`/requests/${created.id}`);
    } catch (err) {
      if (!operation.isCurrent()) return;
      if (err instanceof PortalError && err.code === "401") {
        expireSession();
        return;
      }
      if (!operation.canUpdate()) return;
      setError(safeError(err));
      if (err instanceof PortalError && err.code === "COMMIT_OUTCOME_UNKNOWN")
        setUncertain(true);
    } finally {
      lock.current = false;
      if (operation.canUpdate()) setBusy(false);
    }
  }
  return (
    <>
      <Link className="back-link" to="/requests">
        <ArrowLeft size={16} />
        Danh sách yêu cầu
      </Link>
      <PageHeading
        title="Bạn cần hỗ trợ gì?"
        description="Mô tả nhu cầu của bạn. Chúng tôi sẽ chuyển đến đúng nhóm phụ trách."
      />
      <div className="form-layout">
        <form
          ref={formRef}
          className="panel request-form"
          noValidate
          onSubmit={submit}
        >
          <fieldset disabled={busy || uncertain}>
            <legend>
              <span className="step-number">1</span>Chọn dịch vụ
            </legend>
            <div className="service-options">
              {(["it", "facility"] as RequestKind[]).map((kind) => (
                <label
                  key={kind}
                  className={`service-option ${input.kind === kind ? "selected" : ""}`}
                >
                  <input
                    type="radio"
                    name="kind"
                    value={kind}
                    checked={input.kind === kind}
                    onChange={() => update("kind", kind)}
                  />
                  {kind === "it" ? (
                    <Headphones size={25} />
                  ) : (
                    <Building2 size={25} />
                  )}
                  <strong>{kindLabels[kind]}</strong>
                  <small>
                    {kind === "it"
                      ? "Thiết bị, phần mềm, tài khoản"
                      : "Văn phòng, phòng họp, tiện ích"}
                  </small>
                </label>
              ))}
            </div>
          </fieldset>
          <fieldset disabled={busy || uncertain}>
            <legend>
              <span className="step-number">2</span>Thông tin yêu cầu
            </legend>
            <label className="field">
              Tiêu đề yêu cầu <span className="required">*</span>
              <input
                name="title"
                maxLength={120}
                value={input.title}
                onChange={(e) => update("title", e.target.value)}
                placeholder={
                  input.kind === "it"
                    ? "Ví dụ: Cấp quyền truy cập kho mã nguồn dự án"
                    : "Ví dụ: Kiểm tra điều hòa phòng họp Lotus"
                }
                aria-required="true"
                aria-invalid={!!errors.title}
                aria-describedby={errors.title ? "title-error" : undefined}
              />
              {errors.title && (
                <span className="field-error" id="title-error">
                  {errors.title}
                </span>
              )}
            </label>
            <label className="field">
              Mô tả chi tiết <span className="required">*</span>
              <textarea
                name="description"
                rows={5}
                maxLength={2000}
                value={input.description}
                onChange={(e) => update("description", e.target.value)}
                placeholder="Vấn đề bạn đang gặp là gì? Khi nào xảy ra và ảnh hưởng đến công việc như thế nào?"
                aria-required="true"
                aria-invalid={!!errors.description}
                aria-describedby={
                  errors.description ? "description-error" : "description-hint"
                }
              />
              <span className="field-hint" id="description-hint">
                Càng cụ thể, đội ngũ càng hỗ trợ bạn nhanh hơn.{" "}
                <span>{input.description.length}/2000</span>
              </span>
              {errors.description && (
                <span className="field-error" id="description-error">
                  {errors.description}
                </span>
              )}
            </label>
            <div className="field-grid">
              <label className="field">
                Vị trí / khu vực <span className="required">*</span>
                <input
                  name="location"
                  value={input.location}
                  maxLength={100}
                  onChange={(e) => update("location", e.target.value)}
                  placeholder="Ví dụ: Tầng 3 · Khu phát triển"
                  aria-required="true"
                  aria-invalid={!!errors.location}
                  aria-describedby={
                    errors.location ? "location-error" : undefined
                  }
                />
                {errors.location && (
                  <span className="field-error" id="location-error">
                    {errors.location}
                  </span>
                )}
              </label>
              <label className="field">
                Mức độ ưu tiên
                <select
                  name="priority"
                  value={input.priority}
                  onChange={(e) =>
                    update(
                      "priority",
                      e.target.value as CreateRequestInput["priority"],
                    )
                  }
                >
                  <option value="normal">Bình thường</option>
                  <option value="high">Cao</option>
                  <option value="urgent">Khẩn cấp</option>
                </select>
              </label>
            </div>
          </fieldset>
          <div className="attachment-block">
            <Paperclip size={22} />
            <div>
              <strong>
                Tệp đính kèm{" "}
                <span className="blocked-tag">BLOCKED FEATURE</span>
              </strong>
              <p>
                Chờ Storage API có kiểm quyền. Chưa hỗ trợ chọn hoặc tải tệp.
              </p>
            </div>
            <LockKeyhole size={18} />
          </div>
          {error && <ErrorState message={error} />}
          {uncertain && (
            <Link className="text-link" to="/requests">
              Kiểm tra danh sách và lịch sử yêu cầu
            </Link>
          )}
          <div className="form-actions">
            <span>
              <ShieldCheck size={15} />
              Chỉ lưu trong phiên mock
            </span>
            <Link to="/requests" className="button secondary">
              Hủy
            </Link>
            <button
              type="submit"
              className="button primary"
              disabled={busy || uncertain}
            >
              {busy ? <LoaderCircle className="spinner" size={17} /> : null}
              {busy ? "Đang gửi…" : "Gửi yêu cầu"}
              <ArrowRight size={16} />
            </button>
          </div>
        </form>
        <aside className="form-aside">
          <div className="info-card">
            <Info size={23} />
            <h3>
              Một yêu cầu rõ ràng,
              <br />
              một khởi đầu thuận lợi.
            </h3>
            <p>
              Chọn đúng dịch vụ và mô tả đầy đủ để nhóm phụ trách tiếp nhận
              nhanh chóng.
            </p>
            <ol>
              <li>Gửi yêu cầu</li>
              <li>Phê duyệt & phân công</li>
              <li>Xử lý & cập nhật tiến độ</li>
              <li>Hoàn thành & lưu lịch sử</li>
            </ol>
          </div>
          <p className="aside-footnote">
            Đây là quy trình UX mẫu. Workflow API thật chưa được mở qua security
            gate.
          </p>
        </aside>
      </div>
    </>
  );
}
