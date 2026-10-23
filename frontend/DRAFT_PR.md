# feat(portal): Layer 2 DX-OS Service Portal (#39)

DX-OS chưa có consumer Tầng 2 có giao diện. PR này thêm portal tiếng Việt build/run độc lập, cho phép trình diễn tạo yêu cầu, phê duyệt/xử lý và lịch sử của IT Support/Facility bằng mock data. **Chỉ hoàn thành mock UI prototype. Issue #39 giữ OPEN; chưa có HTTP end-to-end thật.**

## UI screens

- Phiên demo/đổi vai trò/hết hạn, dashboard, danh sách yêu cầu và form tạo mới.
- Bàn phê duyệt/xử lý, chi tiết yêu cầu, timeline/lịch sử, Integration Status.
- Search không dấu, filter, pagination, validation, loading, empty/error/success.
- Responsive desktop/tablet/mobile, font cục bộ, Lucide, design tokens, focus và reduced motion.

## Architecture

`frontend/` là SPA React + Vite + TypeScript, stack đã được người dùng xác nhận sau audit. Shared `RequestService` interface → composition root → `MockRequestService`. `HttpApiClient` riêng luôn từ chối trước mạng, không có cờ bật LIVE. Không import Java/Core, không nối PostgreSQL, không có credential hoặc trusted identity từ header.

Chỉ thêm source frontend, tests, docs và screenshot evidence; không sửa backend, Core contracts, Gateway/Identity/Workflow, Flyway V1–V4 hoặc PR #35.

## Mock workflows

IT Support và Facility dùng chung request view model, form, table, metadata, action dialog và timeline. Mock chỉ có phản hồi cố định phục vụ UX; không có WorkflowRuntime/definition execution ở frontend. Role/visibility chỉ mô phỏng, không chứng minh backend ACL.

Không tự retry write/transition. Double-submit bị khóa; kết quả ghi chưa xác định vô hiệu hóa gửi tiếp và hướng dẫn kiểm tra lịch sử.

## Build/test results

Local PASS: TypeScript + Vite build; 19 unit/component tests; 12 Playwright Chromium smoke tests; axe WCAG 2 A/AA + 2.1 AA trên tám màn hình và ba viewport dashboard (0 violations); source/bundle credential-pattern scan (27 files). npm install audit: 0 vulnerabilities tại thời điểm cài. Backend tests SKIP vì không sửa backend; real HTTP/security E2E BLOCKED. Không tuyên bố CI đã chạy.

Xem [bằng chứng và lệnh tái hiện](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/docs/evidence/issue39-portal/README.md).

## Screenshots

![Dashboard mock](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/docs/evidence/issue39-portal/02-dashboard-desktop.png?raw=true)

![Mobile mock](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/docs/evidence/issue39-portal/09-dashboard-mobile.png?raw=true)

[Tám màn hình và ảnh desktop/tablet/mobile](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/docs/evidence/issue39-portal/README.md), chụp từ Chromium chạy localhost bằng Playwright, không phải ảnh mockup.

## BLOCKED API

- Identity/SSO, Gateway và Workflow HTTP: #22/#23/#24 security gate; create/activate/transition/snapshot/history chưa được gọi.
- OpenAPI/DTO mapping: chờ #37 và review contract trước khi có HTTP adapter.
- Attachments: BLOCKED FEATURE, chờ backend Storage API có kiểm quyền #26; không có file input/fake upload.
- Không có database/storage persistence thật từ portal.

## Known limitations

Request/timeline ở bộ nhớ một tab, reload reset fixtures. Demo role ở sessionStorage không phải đăng nhập thật. Chưa có real security/tenant tests hoặc real HTTP E2E; browser coverage là Chromium, axe không thay thế audit accessibility đầy đủ. Chưa triển khai hosting. Hướng dẫn chạy tại [frontend README](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/README.md), kịch bản tại [Demo guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/DEMO_GUIDE.md).

Refs #39. Không close Issue, không merge và không đánh dấu real integration DONE.
