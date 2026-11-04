<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# DX-OS Internal Service Portal — V4 UI Redesign Candidate

**Ngày:** 04/11/2026  
**Baseline đối chiếu GitHub:** [`lexuanbao258-collab/dxos-open-core@0e789cffa11447f3acef25d0e582bf25ed46c7b4`](https://github.com/lexuanbao258-collab/dxos-open-core/tree/0e789cffa11447f3acef25d0e582bf25ed46c7b4/frontend)  
**Phạm vi:** chỉ giao diện Tầng 2 (DX-OS Portal), không phải website giới thiệu TRAKZOR, không phải backend.

## 1. Chọn hướng thiết kế

Thiết kế V3 trước là một bảng phẳng quá khô. V4 dùng lại hình thái workspace doanh nghiệp trong `frontend/src/App.tsx` hiện tại, nhưng giản lược: sidebar sáng, header thấp, nhãn môi trường nhìn thấy được, bảng nghiệp vụ thống nhất, progress 5 nấc và một vùng nội dung phụ có nhiệm vụ rõ ràng. Không làm mới bằng gradient, icon trang trí, giả số KPI hoặc màu riêng cho từng trạng thái.

**V4 là ứng viên thiết kế để nhận xét, không phải thay thế frontend đang chạy.**

## 2. Nội dung

- `preview/index.html`, `preview/styles.css`, `preview/app.js`: bản xem trước HTML tĩnh nhưng **có tương tác**, chạy offline bằng trình duyệt, hỗ trợ desktop/mobile, tìm kiếm, lọc, chuyển màn, chọn vai trò trình bày, form validation, bàn phím, màn lỗi.
- `src/design-v4/PortalV4.tsx`, `model.ts`, `portal-v4.css`: source component React 19/TypeScript để tích hợp thành **route xem trước riêng** trong repo hiện hành.
- `screenshots/`: ảnh Chromium 1440, 390 và các màn chính.
- `qa/browser-check.json`: số đo layout và tương tác từ HTML preview.
- `INTEGRATION_GUIDE.md`: cách đưa vào branch riêng không thay đổi business logic.
- `REVIEW_REQUEST.md`: nội dung review độc lập.

## 3. Chạy preview

Mở trực tiếp `preview/index.html` hoặc chạy:

```powershell
python -m http.server 8080 --directory preview
```

Truy cập `http://localhost:8080`. Không có request tới DX-OS backend. **Không nhập dữ liệu nhạy cảm** vì đây là bản xem trước UI.

Các route có thể chọn trực tiếp qua menu: Tổng quan, Danh sách, Bàn xử lý, Lịch sử, Tạo yêu cầu, Trạng thái tích hợp, Phiên demo. Dữ liệu chỉ là 8 record từ handoff. Dấu vết chi tiết chỉ có DX-1046. Không thêm ticket mới, không tạo tên người/hiệu quả/feedback giả.

**Font:** Bản xuất không kèm file font. Khi tích hợp vào repo, sử dụng Be Vietnam Pro đã được nạp bởi `frontend/src/main.tsx` từ `@fontsource`; trình xem HTML độc lập sẽ dùng font hệ thống nếu máy không có font tương ứng.

## 4. Chính sách bảo mật giữ nguyên

- Mọi màn có nhãn `MOCK / DEV`; không tuyên bố LIVE.
- Các giới hạn `BLOCKED` và issue theo **bản bàn giao thiết kế**, chưa được reconfirm với HEAD phát hành mới.
- `allowedActions` **không có** trong 8 bản ghi mẫu ⇒ không hiển thị thao tác nghiệp vụ thật, không tự suy quyền theo role/status.
- `DX-1046` có `expectedVersion=2` theo dấu vết mẫu; không suy luận version của các bản ghi còn lại.
- Form chỉ validate phía client, không gửi lệnh.
- Không sửa `RequestService`, `MockRequestService`, `HttpApiClient`, `PortalProvider`, backend hoặc tests hiện hành.

## 5. Kiểm thử đã làm / chưa làm

| Kiểm tra | Trạng thái |
|---|---|
| HTML preview, JavaScript parse | PASS |
| 8 viewport × 8 trang (64 tổ hợp): tràn ngang / page error | PASS (0 phát hiện) |
| 11 hành vi tương tác trên preview | PASS |
| React TSX syntax transpile | PASS (0 parse diagnostics) |
| React + Vite build trong source repo | NOT RUN (không có clone/dependencies tại môi trường test) |
| Unit tests 365/365 trong repo | NOT RUN, không kế thừa kết quả |
| axe / screen reader / user test thiết bị thật | NOT RUN |
| Backend/browser fullstack E2E | NOT RUN; không được tuyên bố LIVE |

## 6. Kết luận

**DESIGN REVIEW CANDIDATE**, không merge, deploy hay bật tích hợp LIVE. Ánh có thể tích hợp source này ở route `/design-preview/*` trong branch riêng, sau đó chụp Chromium và chạy bộ unit/axe cũ trước khi thay route chính thức.
