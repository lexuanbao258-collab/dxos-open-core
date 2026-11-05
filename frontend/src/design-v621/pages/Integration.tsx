// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { PageHead } from "../components/PageHead";
import { INTEGRATION_ROWS, type QaScenario } from "../model";
import { usePreview } from "../PreviewContext";

const SCENARIOS: ReadonlyArray<readonly [QaScenario, string]> = [
  ["normal", "Bình thường"],
  ["loading", "Đang tải"],
  ["empty", "Không có dữ liệu"],
  ["error", "Lỗi đọc"],
  ["success", "Thành công (mô phỏng)"],
  ["unknown", "Kết quả ghi chưa xác định"],
];

export function Integration() {
  const { qa, setQa, announce } = usePreview();
  return (
    <>
      <PageHead
        title="Trạng thái tích hợp"
        description="Thông tin theo baseline thiết kế, không phải trạng thái LIVE tự động cập nhật."
      />
      <div className="info-panel" style={{ marginTop: 0 }}>
        <p>
          Đối chiếu trên nguồn bàn giao dùng để dựng V5; được đưa vào giao diện
          V6 ngày 05/11/2026. Trạng thái thực tế ở GitHub có thể thay đổi và cần
          nghiệm thu độc lập trước khi gỡ nhãn BLOCKED.
        </p>
      </div>
      <div className="integrations">
        <div className="integration-head">
          <span>Thành phần</span>
          <span>Trạng thái</span>
          <span>Điều kiện</span>
          <span>Issue / PR</span>
        </div>
        {INTEGRATION_ROWS.map(([name, ref, condition]) => (
          <div className="integration-row" key={name}>
            <strong>{name}</strong>
            <span>
              <span className="blocked">BLOCKED</span>
            </span>
            <span className="ot-description">{condition}</span>
            <span className="ot-ref">{ref}</span>
          </div>
        ))}
      </div>
      <div className="info-panel">
        <h2>Nguyên tắc tích hợp</h2>
        <p>
          Quyền thao tác từ phản hồi máy chủ, lệnh ghi có{" "}
          <span className="mono">expectedVersion</span>; nếu phiên bản thay đổi
          hoặc không xác định kết quả ghi, phải khóa thao tác để đối soát. Bản
          xem trước này không gọi API nghiệp vụ, không lưu dữ liệu và không tải
          tài nguyên từ bên thứ ba: font Be Vietnam Pro và IBM Plex Mono được
          phục vụ cục bộ.
        </p>
      </div>
      <div className="qa-layout">
        <h2>Công cụ xem trạng thái giao diện</h2>
        <p>
          Chọn tình huống mô phỏng; sau đó mở “Yêu cầu của tôi” hoặc “Bàn xử
          lý”. Không phát sinh dữ liệu hoặc request mạng.
        </p>
        <label
          htmlFor="qa-scenario"
          style={{
            display: "block",
            fontSize: 13,
            fontWeight: 600,
            marginBottom: 7,
          }}
        >
          Tình huống
        </label>
        <select
          className="ot-select qa-select"
          id="qa-scenario"
          value={qa}
          onChange={(e) => {
            const next = e.target.value as QaScenario;
            setQa(next);
            announce(
              "Tình huống giao diện: " +
                (SCENARIOS.find(([v]) => v === next)?.[1] ?? next),
            );
          }}
        >
          {SCENARIOS.map(([value, text]) => (
            <option key={value} value={value}>
              {text}
            </option>
          ))}
        </select>
      </div>
    </>
  );
}
