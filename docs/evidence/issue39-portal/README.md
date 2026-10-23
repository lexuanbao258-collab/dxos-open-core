# Issue #39 — bằng chứng mock portal

**MOCK / DEVELOPMENT. Chỉ xác nhận giao diện mock; chưa xác nhận HTTP end-to-end, authentication hoặc authorization thực. Giữ Issue #39 OPEN.**

## Môi trường

- Ngày kiểm tra: 2026-10-20, Windows, Node.js 24.19.0, npm 11.17.0.
- Branch: `feature/issue39-portal-ui`; base `2115de3`, trùng `origin/main` sau fetch tại thời điểm audit.
- Frontend build/run độc lập trong `frontend/`; React 19, Vite 8, TypeScript strict.
- Chromium do Playwright cài; browser tests chạy production build tại `http://127.0.0.1:4173`.
- Fixture identities và nội dung là dữ liệu demo. Không chứa token hoặc thông tin xác thực.

## Screenshot thực tế

Các PNG được tạo bởi `frontend/tests/portal.spec.ts` từ ứng dụng chạy trên localhost. Chờ font cục bộ, tắt chuyển động khi capture để không chụp giữa transition. Không dựng ảnh, không dùng ImageGen, không chỉnh sửa screenshot. Các ảnh cùng dữ liệu ban đầu; vai trò nhân viên/ người xử lý được chọn qua UI.

| Màn hình | Ảnh |
| --- | --- |
| Trạng thái phiên / đăng nhập demo | [01-session.png](01-session.png) |
| Dashboard desktop | [02-dashboard-desktop.png](02-dashboard-desktop.png) |
| Danh sách yêu cầu | [03-requests.png](03-requests.png) |
| Tạo yêu cầu mới | [04-new-request.png](04-new-request.png) |
| Bàn phê duyệt và xử lý | [05-approvals.png](05-approvals.png) |
| Chi tiết và timeline | [06-detail-timeline.png](06-detail-timeline.png) |
| Lịch sử hoạt động | [07-history.png](07-history.png) |
| Integration Status | [08-integration.png](08-integration.png) |
| Dashboard desktop 1440 × 1100 | [09-dashboard-desktop.png](09-dashboard-desktop.png) |
| Dashboard tablet 834 × 1112 | [09-dashboard-tablet.png](09-dashboard-tablet.png) |
| Dashboard mobile 390 × 844 | [09-dashboard-mobile.png](09-dashboard-mobile.png) |

Ảnh full-page có thể dài hơn chiều cao viewport. Bảng cuộn ngang nội bộ trên mobile; không tràn chiều ngang tài liệu.

## Tái hiện

Kết quả local ngày 2026-10-20 trên source cuối:

| Kiểm tra | Kết quả |
| --- | --- |
| TypeScript strict + production build | **PASS** |
| Vitest unit/component | **PASS · 19 tests / 3 files** |
| Playwright Chromium smoke | **PASS · 12 tests** |
| axe WCAG 2 A/AA + 2.1 AA | **PASS · 0 violations trên các màn hình/viewport được quét** |
| Source/bundle credential-pattern scan | **PASS · 27 files** |
| npm install audit | **PASS · 0 vulnerabilities tại thời điểm cài** |
| Backend/Core regression | **SKIP · không thay đổi backend** |
| LIVE HTTP E2E / real security | **BLOCKED · chưa mở security gate** |

```powershell
Set-Location D:\olp\issue39-ui\frontend
npm.cmd ci
npm.cmd run build
npm.cmd test
npx.cmd playwright install chromium
npm.cmd run test:smoke
npm.cmd run check:security
```

`dist/`, `node_modules/`, trace và reports tự sinh được ignore; chỉ PNG evidence và tài liệu được commit. Xem [README frontend](../../../frontend/README.md) và [hướng dẫn demo](../../../frontend/DEMO_GUIDE.md).

## Phạm vi kiểm chứng

- Cả IT Support và Facility: tạo → phê duyệt → bắt đầu xử lý → hoàn thành; cùng form/model/service/timeline.
- Validation, double-submit, success announcement, search không dấu, filter, empty, pagination.
- Loading 350 ms; lỗi đọc có tải lại chủ động; lỗi ghi kết quả chưa xác định không retry và không cho gửi tiếp.
- 401 mô phỏng kết thúc phiên; 403 mô phỏng; hidden/nonexistent fixture có thông báo 404 giống nhau.
- `HttpApiClient` luôn chặn trước `fetch`, không có enable flag; browser kiểm tra không gọi API và không tải tài nguyên ngoài.
- Không có file input hoặc fake upload; attachments hiển thị BLOCKED FEATURE.
- axe WCAG 2 A/AA và 2.1 AA trên tám màn hình, dashboard desktop/tablet/mobile; native dialog focus/Escape, từ chối, mobile drawer Escape, reduced motion.
- Security-pattern scan source và production bundle, đi kèm review danh sách file commit.

Các kiểm tra trên chỉ xác minh UI và mock seam. Real 401/403/404, cross-tenant denial, HTTP Workflow, database transaction và storage authorization đều **BLOCKED / NOT TESTED**. Không thay đổi Core, Flyway V1–V4, PR #35 hay Issue #23.
