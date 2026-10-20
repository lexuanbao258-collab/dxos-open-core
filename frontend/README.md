# DX-OS Internal Service Portal — Issue #39

**MOCK / DEVELOPMENT · Giao diện prototype Tầng 2. Chưa có HTTP end-to-end thật. Issue #39 phải giữ OPEN.**

Ứng dụng SPA độc lập, tiếng Việt, trình diễn hai dịch vụ IT Support và Facility trên cùng components, request view model, service interface và timeline. Không thay đổi Core, Gateway, Identity, Workflow, Flyway V1–V4 hay PR #35.

## Chạy localhost

Yêu cầu Node.js >= 22.12 (đã kiểm tra bằng Node 24.19.0), npm và trình duyệt hiện đại.

```powershell
Set-Location D:\olp\issue39-ui\frontend
npm.cmd ci
npm.cmd run dev
```

Mở **http://127.0.0.1:5173**. Trên macOS/Linux dùng `npm` thay cho `npm.cmd`. Không cần backend, database, `.env` hoặc credentials.

```powershell
npm.cmd run build
npm.cmd run preview -- --port 4173
```

Production hosting phải có SPA fallback về `index.html` cho các deep links. Không có deployment trong phạm vi này.

## Stack và cấu trúc

React 19, Vite 8, TypeScript strict, React Router 7, Lucide, Be Vietnam Pro được phục vụ cục bộ, CSS tokens; phiên bản chính xác nằm trong `package-lock.json`. Stack được người dùng xác nhận sau audit. Không tải font, analytics hoặc tài nguyên bên thứ ba khi sử dụng app.

```text
src/
  components/ui.tsx           Shared table, badges, timeline, modal, UI states
  domain/request.ts          Shared view model, labels, validation, filtering
  fixtures/requests.ts        Synthetic demo records for both services
  services/RequestService.ts  UI-facing application service interface
  services/portalService.ts   Composition root: interface -> mock service
  services/MockRequestService.ts  In-memory demo responses and fault controls
  services/HttpApiClient.ts   Closed HTTP boundary; rejects before networking
  hooks/useRequests.ts        Loading, read errors, expired demo session handling
  pages/                     Dashboard, list/history/desk, create, detail, system
  context.tsx                Demo role, success announcements, refresh state
  tokens.css / styles.css    Palette, typography, responsive and reduced motion
tests/portal.spec.ts          Browser smoke, WCAG checks and real screenshots
scripts/check-security.mjs    Source/bundle credential-pattern check
```

UI chỉ phụ thuộc `RequestService` qua composition root. `HttpApiClient` tách biệt và luôn trả `LIVE_BLOCKED`; chưa triển khai route, DTO, transport, token hay cờ mở LIVE. Khi API được duyệt, cần adapter riêng map OpenAPI/DTO sang view model, verified session và chiến lược timeout/error theo contract. Không coi view model mock là DTO backend.

Hai workflow UX dùng chung form, bảng, metadata, action dialog và timeline. Những phản hồi cố định trong mock chỉ phục vụ demo; **không phải frontend Workflow engine**, không thực thi definition, không có BPMN editor, persistence hay orchestration Core. Các thao tác được cho phép thật phải do backend quyết định sau security gate.

## Các màn hình

| Route           | Nội dung                                                            |
| --------------- | ------------------------------------------------------------------- |
| `/session`      | Vào/kết thúc phiên demo, đổi vai trò, mô phỏng hết hạn; SSO bị chặn |
| `/`             | Dashboard và truy cập nhanh hai dịch vụ                             |
| `/requests`     | Search không dấu, filter dịch vụ/trạng thái, phân trang             |
| `/requests/new` | Form dùng chung, validation, loading, success/error                 |
| `/approvals`    | Bàn xử lý dành cho vai trò người xử lý mô phỏng                     |
| `/requests/:id` | Chi tiết, phiên bản mock, thao tác xử lý và timeline                |
| `/history`      | Lịch sử từng yêu cầu, mới nhất trước                                |
| `/integration`  | Trạng thái các capability, kiểm tra chặn LIVE và fault controls     |

Loading được mô phỏng với độ trễ 350 ms. Không có kết quả: hướng dẫn đổi bộ lọc. Lỗi đọc có nút tải lại do người dùng chủ động. Form giữ nội dung khi lỗi mạng. Ghi có khóa chống double-submit và không tự retry. Khi `COMMIT_OUTCOME_UNKNOWN`, UI vô hiệu hóa gửi tiếp và hướng dẫn kiểm tra lịch sử. Lỗi 401 mô phỏng kết thúc phiên và hiển thị thông báo; 403 và hidden/missing 404 có thông báo an toàn. Đây không phải bằng chứng kiểm quyền backend.

## Ranh giới bảo mật và BLOCKED API

| Capability               | Điều kiện còn thiếu                                                                                                               |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| Identity / SSO           | Verified browser login, OIDC Authorization Code + PKCE, #24                                                                       |
| Gateway / Workflow HTTP  | Registration, resource ACL, tenant binding và security gate #22/#23/#24; mọi create/activate/transition/snapshot/history đều chặn |
| Data persistence         | HTTP application contract được duyệt; không nối PostgreSQL hoặc Java trực tiếp                                                    |
| Attachments              | Backend Storage API có kiểm quyền #26: **BLOCKED FEATURE**, không có file input hoặc upload giả                                   |
| HTTP DTO / error mapping | OpenAPI và review contract #37                                                                                                    |

Không có secret, admin token, service-role key, database credential hay `X-User`/`X-Role` làm nguồn định danh. Chỉ có tên/vai trò demo trong `sessionStorage`; sửa giá trị này không phải chứng minh authentication/authorization. Fixture visibility (nhân viên xem yêu cầu mẫu của mình; người xử lý xem bộ dữ liệu mẫu) chỉ là hành vi UX.

## Kiểm thử

```powershell
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:smoke
npm.cmd run check:security
```

Smoke suite khởi chạy production preview tại `127.0.0.1:4173`, kiểm tra hai luồng UI, lỗi/empty/validation, chặn API, keyboard/modal, desktop/tablet/mobile và axe WCAG 2 A/AA + 2.1 AA. Screenshot được tạo trực tiếp từ localhost tại [evidence](../docs/evidence/issue39-portal/README.md). Không dùng ảnh dựng làm bằng chứng. Script security chỉ là kiểm tra mẫu credentials phổ biến, đi kèm review source và staged diff.

Python không có trong môi trường triển khai nên browser suite dùng Playwright TypeScript, có thể chạy bằng dependencies đã khóa của dự án.

## Giới hạn

- Request và lịch sử chỉ ở bộ nhớ trong **một tab**. Reload hoặc khôi phục demo sẽ reset fixtures; đổi route bằng sidebar giữ dữ liệu.
- Phiên demo chỉ lưu lựa chọn vai trò trong tab, không có SSO, access token hay quyền thực.
- Kết quả ghi chưa xác định chỉ là mô phỏng lỗi; mock không xác nhận một commit thật đã xảy ra.
- Chưa kiểm chứng real 401/403/404, cross-tenant denial, concurrency/persistence hoặc HTTP end-to-end.
- Browser coverage hiện là Chromium; axe không thay thế audit accessibility thủ công đầy đủ. Bảng cuộn ngang bên trong ở màn hình nhỏ.
- Chưa upload/read file, chưa gửi thông báo thực, chưa triển khai hosting.

Xem [hướng dẫn demo](DEMO_GUIDE.md). Refs #39; chỉ ghi nhận **mock UI prototype hoàn thành** khi local checks đạt, không đóng Issue #39.
