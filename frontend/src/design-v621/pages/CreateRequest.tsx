// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { useRef, useState, type FormEvent } from "react";
import { PageHead } from "../components/PageHead";
import { validateCreate, type CreateErrors } from "../logic";
import { usePreview } from "../PreviewContext";

type Field = "title" | "description" | "location";
const INITIAL = {
  service: "helpdesk.v1",
  title: "",
  description: "",
  location: "",
  priority: "Bình thường",
};

/**
 * Client-side validation only. This form never creates a ticket, never calls
 * a service and has no retry path, so there is no write to guard beyond the
 * `unknown` simulation which locks submission entirely.
 */
export function CreateRequest() {
  const { qa, announce } = usePreview();
  const [values, setValues] = useState(INITIAL);
  const [errors, setErrors] = useState<CreateErrors>({});
  const [checked, setChecked] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const refs = {
    title: useRef<HTMLInputElement>(null),
    description: useRef<HTMLTextAreaElement>(null),
    location: useRef<HTMLInputElement>(null),
  };
  const facility = values.service === "facility.v1";
  const locked = qa === "unknown";
  const set = (patch: Partial<typeof INITIAL>) =>
    setValues((v) => ({ ...v, ...patch }));

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (locked) return;
    const next = validateCreate(values);
    setErrors(next);
    const first = (["title", "description", "location"] as Field[]).find(
      (k) => next[k],
    );
    if (first) {
      setChecked(false);
      setInvalid(true);
      refs[first].current?.focus();
      return;
    }
    setInvalid(false);
    setChecked(true);
    announce("Biểu mẫu hợp lệ. Chưa gửi yêu cầu.");
  }

  function onReset(event: FormEvent) {
    event.preventDefault();
    setValues(INITIAL);
    setErrors({});
    setChecked(false);
    setInvalid(false);
  }

  const err = (k: Field) => errors[k];
  return (
    <>
      <PageHead
        title="Tạo yêu cầu"
        description="Chọn loại dịch vụ và mô tả vấn đề cần hỗ trợ."
      />
      <div className="ot-form-layout">
        <form
          className="ot-form"
          id="create-form"
          noValidate
          onSubmit={onSubmit}
          onReset={onReset}
        >
          <fieldset
            className="form-field"
            style={{ border: 0, padding: 0, margin: "0 0 20px" }}
          >
            <legend>
              Dịch vụ <span className="ot-required">*</span>
            </legend>
            <div className="choice-wrap">
              <label className="choice">
                <input
                  type="radio"
                  name="service"
                  value="helpdesk.v1"
                  checked={!facility}
                  onChange={() => set({ service: "helpdesk.v1" })}
                />
                <span>
                  <strong>Hỗ trợ CNTT</strong>
                  <small>Máy tính, tài khoản, phần mềm</small>
                </span>
              </label>
              <label className="choice">
                <input
                  type="radio"
                  name="service"
                  value="facility.v1"
                  checked={facility}
                  onChange={() => {
                    set({ service: "facility.v1" });
                    setErrors((e) => ({ ...e, location: undefined }));
                  }}
                />
                <span>
                  <strong>Cơ sở vật chất</strong>
                  <small>Phòng họp, đèn, điều hòa</small>
                </span>
              </label>
            </div>
          </fieldset>
          <div className="form-field">
            <label htmlFor="title">
              Tiêu đề <span className="ot-required">*</span>
            </label>
            <input
              id="title"
              name="title"
              ref={refs.title}
              className="ot-input"
              placeholder="Ví dụ: Không truy cập được kho mã nguồn"
              maxLength={120}
              value={values.title}
              onChange={(e) => set({ title: e.target.value })}
              aria-invalid={Boolean(err("title"))}
              aria-describedby="title-error"
            />
            <span className="ot-field-error" id="title-error" aria-live="polite">
              {err("title")}
            </span>
          </div>
          <div className="form-field">
            <label htmlFor="description">
              Mô tả chi tiết <span className="ot-required">*</span>
            </label>
            <textarea
              id="description"
              name="description"
              ref={refs.description}
              className="ot-textarea"
              maxLength={2000}
              placeholder="Mô tả vấn đề và thông tin cần thiết để xử lý"
              value={values.description}
              onChange={(e) => set({ description: e.target.value })}
              aria-invalid={Boolean(err("description"))}
              aria-describedby="description-error"
            />
            <span
              className="ot-field-error"
              id="description-error"
              aria-live="polite"
            >
              {err("description")}
            </span>
          </div>
          <div className="form-row">
            <div className="form-field">
              <label htmlFor="location" id="location-label">
                {facility ? (
                  <>
                    Vị trí / khu vực{" "}
                    <span className="ot-required" aria-hidden="true">
                      *
                    </span>
                  </>
                ) : (
                  "Thiết bị / hệ thống liên quan (không bắt buộc)"
                )}
              </label>
              <input
                id="location"
                name="location"
                ref={refs.location}
                className="ot-input"
                maxLength={100}
                placeholder={
                  facility ? "Ví dụ: Phòng họp Lotus" : "Ví dụ: Laptop cá nhân"
                }
                value={values.location}
                onChange={(e) => set({ location: e.target.value })}
                required={facility}
                aria-required={facility}
                aria-invalid={Boolean(err("location"))}
                aria-describedby="location-error"
              />
              <span
                className="ot-field-error"
                id="location-error"
                aria-live="polite"
              >
                {err("location")}
              </span>
            </div>
            <div className="form-field">
              <label htmlFor="priority">
                Ưu tiên <span className="ot-required">*</span>
              </label>
              <select
                id="priority"
                name="priority"
                className="ot-select"
                value={values.priority}
                onChange={(e) => set({ priority: e.target.value })}
              >
                <option>Bình thường</option>
                <option>Cao</option>
              </select>
            </div>
          </div>
          <div className="ot-form-actions">
            <button
              className="btn btn-primary"
              type="submit"
              id="submit-form"
              disabled={locked}
            >
              Kiểm tra biểu mẫu
            </button>
            <button className="btn" type="reset">
              Làm lại
            </button>
          </div>
          {locked && (
            <div className="state-box unknown" role="alert">
              <strong>Kết quả ghi chưa xác định</strong>
              <p>
                Thao tác đang khóa; hãy kiểm tra lịch sử và đối soát. Không gửi
                lại lệnh.
              </p>
            </div>
          )}
          <div id="form-feedback" aria-live="polite">
            {invalid && (
              <p className="ot-field-error" role="alert">
                Vui lòng kiểm tra các trường đã đánh dấu.
              </p>
            )}
            {checked && (
              <div className="success-inline" role="status">
                <strong>Biểu mẫu hợp lệ</strong>
                <p>
                  Đã kiểm tra trên trình duyệt. Chưa gửi yêu cầu lên máy chủ,
                  chưa tạo ticket.
                </p>
              </div>
            )}
          </div>
        </form>
        <aside className="ot-form-note">
          <h2>Trước khi gửi yêu cầu</h2>
          <p>
            Mô tả ngắn gọn vấn đề, ảnh hưởng thực tế và thông tin liên quan. Chọn
            đúng loại dịch vụ để biểu mẫu hiển thị trường phù hợp.
          </p>
          <p>
            <strong>Bản xem trước:</strong> nút bên trái chỉ kiểm tra nội dung
            nhập trên trình duyệt; không tạo ticket hay gửi dữ liệu đến hệ
            thống.
          </p>
        </aside>
      </div>
    </>
  );
}
