<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Hướng dẫn tích hợp an toàn vào frontend GitHub

## Bước 1 — Baseline và branch

Trong `D:\olp\dxos-open-core`, kiểm tra Git status sạch, fetch HEAD và đọc `AGENTS.md`/`CONTRIBUTING.md` nếu có trong checkout. **Không chép đè backend, service, context, hooks hoặc API client.**

```powershell
git status --short
git rev-parse HEAD
git switch -c feature/portal-v4-design-preview
```

Không chạy `git switch` khi đang có thay đổi dở không được bảo toàn; dùng worktree riêng khi cần.

## Bước 2 — Copy chính xác 3 file React

Copy thư mục `src/design-v4/` của gói này sang `frontend/src/design-v4/` trong repo.

Trong `frontend/src/App.tsx`, thêm:

```tsx
import { PortalV4 } from "./design-v4/PortalV4";
```

Thêm route **ở mức trực tiếp bên trong `<Routes>`**, trước route có `<Layout />`:

```tsx
<Routes>
  <Route path="/design-preview/*" element={<PortalV4 />} />
  <Route path="/login" element={<AuthPage key="login" mode="login" />} />
  {/* Các route hiện tại giữ nguyên */}
</Routes>
```

Giữ nguyên `PortalProvider` bọc `Routes`, và không sửa nhánh đặc biệt `main.tsx` của `integration-local`.

**Lý do không đặt dưới `<Layout/>`:** App.tsx cũ đã có sidebar/topbar riêng; nếu đặt bên dưới sẽ bị lồng hai giao diện.

## Bước 3 — Build và regression

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run build
npm.cmd test -- --run
npm.cmd run test:smoke
```

`npm test` đã dùng `vitest run`; tham số `-- --run` có thể không cần thiết tuỳ npm. Nếu test:smoke yêu cầu browser hoặc môi trường cụ thể, ghi nhận NOT RUN chứ không sửa môi trường/giả PASS.

Truy cập `/design-preview/` trên dev server để kiểm tra ở 320, 390, 768, 1024 và 1440. V4 chỉ đọc các fixtures tĩnh; không gọi API. Hãy chụp ảnh cả 2 role.

## Bước 4 — Chuyển từ thiết kế sang frontend nghiệp vụ

Chỉ sau khi design review được chốt:

1. Xác định route chuyển đổi và tính năng của bản Portal cũ cần giữ lại.
2. Dùng `useRequests` / `RequestService` để lấy dữ liệu trong nhánh MOCK nhưng không suy `allowedActions` từ role/status cho LIVE.
3. Khi HTTP DTO được duyệt, adapter phải map chính xác allowedActions/expectedVersion; nếu thiếu thì không cho thao tác ghi.
4. Gặp `CONFLICT` phải tải lại / khóa thao tác; gặp `COMMIT_OUTCOME_UNKNOWN` phải khóa ghi và reconcile, không retry tự động.
5. Test lại ứng xử trong `integration-local` riêng, không dùng UI preview làm bằng chứng LIVE.

## Những khác biệt nguồn phải xử lý trước khi thay giao diện chính

- Bản mock hiện tại trong Git sử dụng trạng thái hiển thị như `pending`, `approved`, `processing`; handoff v3/v4 sử dụng các bước `SUBMITTED`, `TRIAGED`, `IN_PROGRESS`, `RESOLVED`, `CONFIRMED`. **Không gộp hoặc tự map** khi chưa có hợp đồng chuẩn.
- MockService hiện trả `actions(request, role)` tính từ trạng thái và vai trò. Điều này **không tương đương** `allowedActions` từ server trong phiên thật.
- Danh sách dữ liệu từ handoff (Ngọc Ánh, DX-1048...) khác `frontend/src/fixtures/requests` hiện tại. Route `/design-preview/` giữ data riêng để không làm hỏng regression hiện có.
- Dòng `PR #63` và issue trong handoff là **baseline lịch sử**. README / CHANGELOG mới hơn có cập nhật; owner phải reconfirm trước khi phát hành.
