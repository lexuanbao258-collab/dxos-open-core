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
  // Every row in the baseline is BLOCKED; the count comes from the data.
  const total = INTEGRATION_ROWS.length;
  return (
    <>
      <PageHead
        title="Trạng thái tích hợp"
        description="Thông tin theo baseline thiết kế, không phải trạng thái LIVE tự động cập nhật."
      />
      <p className="int-summary">
        <strong>{total} thành phần</strong> · {total} chưa nghiệm thu
      </p>
      <p className="int-note">
        Đối chiếu trên nguồn bàn giao dùng để dựng V5; được đưa vào giao diện V6
        ngày 05/11/2026. Trạng thái thực tế ở GitHub có thể thay đổi và cần
        nghiệm thu độc lập trước khi gỡ nhãn BLOCKED.
      </p>
      <div className="int-surface">
        <table className="int-table">
          <caption className="ot-sr-only">
            Trạng thái các thành phần tích hợp
          </caption>
          <thead>
            <tr>
              <th scope="col">Thành phần</th>
              <th scope="col">Trạng thái</th>
              <th scope="col">Điều kiện</th>
              <th scope="col">Issue / PR</th>
            </tr>
          </thead>
          <tbody>
            {INTEGRATION_ROWS.map(([name, ref, condition]) => (
              <tr key={name}>
                <th scope="row" className="int-name">
                  {name}
                </th>
                <td>
                  <span className="blocked">BLOCKED</span>
                </td>
                <td className="int-condition">{condition}</td>
                <td className="int-ref">{ref}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <section className="int-principles" aria-labelledby="principles-title">
        <h2 className="ot-section-title" id="principles-title">
          Nguyên tắc tích hợp
        </h2>
        <ul>
          <li>
            Quyền thao tác do máy chủ cấp qua{" "}
            <span className="mono">allowedActions</span>.
          </li>
          <li>
            Lệnh ghi phải kèm <span className="mono">expectedVersion</span>;
            phiên bản thay đổi thì dừng để đối soát.
          </li>
          <li>
            Kết quả ghi chưa xác định thì khóa thao tác, đối soát và không gửi
            lại lệnh.
          </li>
          <li>
            Bản xem trước này không gọi API nghiệp vụ và không lưu dữ liệu.
          </li>
        </ul>
      </section>
      <section className="qa-layout" aria-labelledby="qa-title">
        <h2 id="qa-title">
          Công cụ xem trạng thái giao diện{" "}
          <span className="qa-tag">Chỉ dùng khi xem trước</span>
        </h2>
        <p>
          Chọn tình huống mô phỏng; sau đó mở “Yêu cầu của tôi” hoặc “Bàn xử
          lý”. Không phát sinh dữ liệu hoặc request mạng.
        </p>
        <label htmlFor="qa-scenario" className="qa-label">
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
      </section>
    </>
  );
}
