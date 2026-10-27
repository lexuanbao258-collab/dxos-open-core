# APP-10 / APP-11 / APP-13 — bàn giao Application, 26/10/2026

Phạm vi: DX-OS Internal Service Portal của DX-OS Open-Core; IT Helpdesk MVP v0.1. Facility Maintenance tiếp tục MOCK. Không redesign, không thêm CLOSED, không bật LIVE. PASS dưới đây là kết quả kiểm thử Application độc lập với fake service/transport, không phải phê duyệt Backend, Identity hoặc acceptance LIVE.

## 1. Baseline và bảo toàn worktree

- Branch trước/sau: `feature/issue39-university-ui`.
- HEAD trước/sau: `00b10fa43d01cc3dcca725b2ed66a43bdaa92268`.
- Index trước/sau: trống, không có staged changes.
- Ban đầu: 25 tracked modified files, 42 untracked files (13 mục `??` khi Git gộp thư mục), gồm code APP-10/11/13 đã có. Tất cả là baseline, không ghi nhận thành code mới của lượt này.
- Sau lượt này: giữ 25 tracked modified files; 43 untracked files, tăng đúng báo cáo này. Bốn file code/test được bổ sung đều đã untracked từ trước.
- Snapshot SHA-256 trước khi sửa gồm 285 tracked/untracked files. Đối chiếu sau: không mất file; chỉ bốn file liệt kê ở mục 6 đổi bytes. 281 file có sẵn còn lại giữ nguyên, bao gồm toàn bộ tracked modified, hai ảnh evidence đã modified, Backend/Core/Identity/Gateway/Database và các tài liệu cũ.
- Bản hash/status/diff ban đầu và bản sao bốn file trước sửa: `%TEMP%/dxos-app-10-11-13-before/`.
- Không commit, push, merge, rebase, stash, reset, clean; không xóa file chưa commit.

## 2. Xác minh Backend contract

Đã đọc metadata PR #63 qua GitHub connector: draft/open, HEAD chính xác `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`. Commit này chưa có trong object database local; không dùng source Backend ở checkout cũ để mapping, không checkout/fetch đè worktree.

Đã đọc hai source tại exact SHA:

- [HelpdeskController.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskController.java), blob `20facd7df094080311714dffb16dd8403f5cf0f3`.
- [HelpdeskService.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/backend/src/main/java/com/tricore/dxos/infrastructure/workflow/HelpdeskService.java), blob `51ce0a3318f41f6a33a4e8ea929b2acc696a59c8`.

`RequestView` có Workflow state/version authoritative, `assignmentVersion` riêng. Names/eligible list là Detail enrichment; list và mutation không cung cấp enrichment này. Assign là Application command ở TRIAGED; `/start` gọi transition `start_work` ở Backend. History chỉ có `transitionId`, `sourceState`, `targetState`, `version`, `occurredAt`.

## 3. APP-10 — PASS cho mapper; Portal consumption BLOCKED

Tái sử dụng `http/types.ts`, `http/codecs.ts`, `helpdeskPresentation.ts` và `HelpdeskRequestService.ts` đã có. Bộ tiêu chí được kiểm chứng là yêu cầu của nhiệm vụ này; không tìm thấy rubric APP-10 riêng có ID trong tài liệu local, không tự nhận signoff nội bộ mới.

| Acceptance criterion             | Kết quả / bằng chứng                                                                                           |
| -------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Tách wire DTO và presentation    | PASS; `HelpdeskRequest` khác `HelpdeskPortalRequestPresentation`, cả hai không giả làm legacy `ServiceRequest` |
| `requestType=IT_HELPDESK`        | PASS; decoder từ chối loại khác, create allowlist ép đúng loại                                                 |
| Giữ authoritative state          | PASS; giữ nguyên cả CONFIRMED và state chưa biết; không chuyển sang MOCK enum                                  |
| Hai version độc lập              | PASS; Workflow 17/19 khác assignment 0/3; số không an toàn bị từ chối thay vì làm tròn                         |
| Không tạo tên/quyền/identity giả | PASS; names absent/null/empty được giữ; current assignee từ ID/name Backend, eligibility không cấp quyền       |
| Optional/redacted fields         | PASS; phân biệt absent/null/[]/populated; completeness của eligible list là unknown                            |
| List                             | PASS; items + nextCursor, không tự tạo total hay đọc hết collection                                            |
| History                          | PASS; đúng năm trường, không tạo actor/note/event ID/title; giữ thứ tự và time wire                            |

Tests: `src/services/mvp/helpdeskPresentation.test.ts`, `src/services/mvp/http/HelpdeskApiClient.test.ts`.

## 4. APP-11 — Component PASS / Portal integration BLOCKED

Giữ `AssignmentForm.tsx` độc lập cùng `assignmentBinding.ts`; kiểm thử qua fake Application service và qua `HelpdeskRequestService` + injected fake transport. Không thêm mount vào Portal khi model chưa tương thích.

- Binding chỉ trả form props nếu authorized Detail có exact action `assign`. Eligible list không tự cấp quyền.
- Khóa submit nếu list absent/null/empty, ID trống hoặc dài hơn 1024, assignmentVersion thiếu/không hợp lệ, hoặc state khác TRIAGED. State chỉ là chốt fail-closed bổ sung, không cấp quyền.
- Chỉ gửi `{assigneeId, expectedAssignmentVersion}`; không dùng Workflow version, display name hoặc role.
- Chặn double-submit. Sau confirmed write mới GET Detail, chỉ cập nhật từ kết quả authoritative; không optimistic-update assignee/state.
- 409 hướng dẫn reload; unknown/network write khóa gửi, không replay. Reload Detail thất bại sau ghi cũng khóa form. Prop rerender không tự mở khóa sau lỗi.
- Fake transport integration kiểm chứng 401/403/404/409/503, callback khi 401 và chặn request tiếp theo; không có fetch/XHR LIVE.

Blocker tích hợp cụ thể: `PortalProvider` APP-09 nhận `RequestService`; service này trả `ServiceRequest` với `status` MOCK, priority/location/department, requester/assignee display strings, createdAt/updatedAt và timeline bắt buộc. `RequestDetail.tsx` đang dùng trực tiếp các trường đó và `actions(request, role)`. Contract PR #63 không cung cấp đầy đủ các trường này; `ActionId` của MOCK cũng không chứa `assign`/`triage` và interface không có assign command. Ép adapter vào seam hiện tại sẽ cần giả dữ liệu, mất semantics hoặc thay model xuyên Portal. Theo phương án dừng an toàn đã được người dùng cho phép, giữ component độc lập và ghi BLOCKED, không thay layout/kiến trúc trong lượt này.

Tests: `src/components/AssignmentForm.test.tsx`, `src/components/AssignmentForm.integration.test.tsx`; APP-09 seam/default regression trong `src/context.service.test.tsx`.

## 5. APP-13 — HTTP adapter PASS, transport LIVE BLOCKED

Tái sử dụng typed `HelpdeskApiClient`, injected transport, codecs và Application service hiện có. Không ship concrete fetch transport/host/token/default instance. Không dùng Request prototype API hoặc `/actions`. Không lưu token vào browser storage.

Base `R = /api/v1/mvp/requests`. Tất cả route có table-driven test `<name>: exact method/path/body and one injected attempt` trong `src/services/mvp/http/HelpdeskApiClient.test.ts`.

| Route               | Body / query                                          | Test tương ứng                                     | Kết quả |
| ------------------- | ----------------------------------------------------- | -------------------------------------------------- | ------- |
| POST R              | title, description, requestType=IT_HELPDESK; HTTP 201 | create; allowlist identity fields                  | PASS    |
| GET R               | after khi có; limit 1..100; items/nextCursor          | list; pagination limits/cursor; malformed envelope | PASS    |
| GET R/{id}          | Không body                                            | detail; RequestView validation                     | PASS    |
| POST R/{id}/triage  | expectedVersion                                       | triage                                             | PASS    |
| POST R/{id}/assign  | assigneeId, expectedAssignmentVersion                 | assign; component integration                      | PASS    |
| POST R/{id}/start   | expectedVersion                                       | start; exact HTTP action name                      | PASS    |
| POST R/{id}/resolve | expectedVersion, nonblank resolution                  | resolve; local validation                          | PASS    |
| POST R/{id}/confirm | expectedVersion                                       | confirm; CONFIRMED response                        | PASS    |
| GET R/{id}/history  | Không body; array gồm năm field Backend               | history; mapping/malformed history                 | PASS    |

Cả chín route có test 401/403/404/409, kể cả error body mâu thuẫn: HTTP status quyết định session/quyền/not-found/conflict. 401 gọi callback đúng một lần rồi đóng client instance. 403 và 404 dùng thông báo an toàn; 409 yêu cầu tải lại. Cả sáu mutation có test lost reply/unknown commit/5xx/timeout/malformed success/unexpected success: đúng một attempt, không retry hoặc reconcile tự động. Decode failure sau successful write được coi là unknown outcome.

## 6. Files tạo/sửa trong lượt này

| File                                                          | Thay đổi thực tế so với local ban đầu                                                            |
| ------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `frontend/src/services/mvp/http/HelpdeskApiClient.ts`         | Ưu tiên known 403/404/409 status trước inconsistent Backend error code; giữ 401 boundary hiện có |
| `frontend/src/services/mvp/http/HelpdeskApiClient.test.ts`    | Thêm 36 status/route cases và 12 malformed list/history cases                                    |
| `frontend/src/services/mvp/assignmentBinding.ts`              | Khóa binding khi eligible assignee ID vượt max 1024 của Assign DTO                               |
| `frontend/src/components/AssignmentForm.integration.test.tsx` | Thêm 1 oversized eligibility case và 5 HTTP error integration cases                              |
| `docs/application/issue39-app10-app11-app13-verification.md`  | Báo cáo mới này                                                                                  |

Không tạo lại adapter/form/mapper đã có; không sửa component layout, composition root, auth implementation, fixtures hoặc evidence cũ. Prettier chỉ áp dụng bốn file code/test trên và báo cáo mới.

## 7. Kiểm thử và production artifact

| Check                                                                 | Kết quả                                                                                                                                                  |
| --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Baseline unit/component/adapter tests                                 | PASS: 14 files, 274 tests                                                                                                                                |
| Final `npx.cmd tsc --noEmit`                                          | PASS                                                                                                                                                     |
| Final `npm.cmd test -- --reporter=dot`                                | PASS: 14 files, 328 tests; 0 FAIL/SKIP                                                                                                                   |
| HTTP adapter fake transport + APP-10/11 integration                   | PASS, nằm trong 328 tests; thêm 54 cases so với baseline                                                                                                 |
| `LIVE_BLOCKED` regression                                             | PASS: default MOCK, chặn cả 9 routes; fetch/XHR không được gọi                                                                                           |
| Production build                                                      | PASS: `npx.cmd vite build --outDir <temp> --emptyOutDir false`; tsc chạy riêng và PASS                                                                   |
| Chromium regression trên temp production build                        | PASS: 10 selected tests; IT → CONFIRMED, Facility, 8 screens/no API/external calls, auth blocked, 401/403/404/network, unknown write, hidden/nonexistent |
| Prettier check                                                        | PASS, chỉ files của lượt này                                                                                                                             |
| `npm.cmd run check:security`                                          | PASS: 57 source/bundle files theo heuristic của script; không thay thế security review                                                                   |
| `git diff --check` + kiểm tra whitespace file untracked mới/sửa       | PASS                                                                                                                                                     |
| SHA-256 before/after                                                  | PASS: chỉ 4 code/test files thay đổi, không mất file, evidence cũ nguyên bytes                                                                           |
| Backend LIVE / real OIDC / PostgreSQL / full browser responsive suite | SKIP có chủ đích: ngoài phạm vi; không suy diễn LIVE acceptance                                                                                          |

PowerShell chặn wrapper `npm.ps1`/`npx.ps1` ở lần gọi đầu; đã chạy lại bằng `.cmd`, không sửa execution policy và các check hoàn tất PASS.

Production artifact: `%TEMP%/dxos-app-production-20261026-170847/` (index, self-hosted fonts, JS/CSS). Browser artifacts mới: `%TEMP%/dxos-app-browser-20261026-170847/`. Playwright dùng config tạm trong ignored `frontend/node_modules/.dxos-app-verification-20261026.config.mjs`, preview trên loopback; không đổi cấu hình tracked và không dùng backend. Sau khi cả 10 tests hoàn tất, đã dừng đúng preview process của lượt kiểm tra để giải phóng teardown Windows; runner kết thúc exit 0, `10 passed (4.3m)`. Không ghi đè `frontend/dist` hoặc screenshots/evidence hiện có.

## 8. Blockers và việc Application còn lại

**Bảo — còn BLOCKED/CANDIDATE cho bước tích hợp tiếp theo:** ratify contract/matrix và scope tại exact Backend HEAD; quyết định cách Portal tiêu thụ presentation model không mất state và optional fields, đồng thời giữ Facility MOCK. Xác nhận quy trình read/reconciliation và điều kiện mở lại thao tác sau conflict/unknown outcome, đặc biệt create mất ACK; không tự thêm idempotency/retry contract. PR #63 vẫn draft, không coi adapter PASS là phê duyệt triển khai Backend.

**Yến — LIVE tiếp tục BLOCKED:** security/authorization/visibility review tại exact HEAD, approved session/token/expiry/logout contract và callback wiring vào protected Portal operations. Không có approved auth transport/composition để gọi Backend thật. Không thay OIDC framework, token storage hoặc security gate trong lượt này.

**Application còn lại sau quyết định của owners:** điều chỉnh consumption của `RequestService`/`ServiceRequest` theo model đã duyệt; mount assignment đúng action panel qua provider injection; render missing/hidden fields, server allowedActions, cursor pagination và authorized history trong Portal; hoàn thiện recovery UX đã duyệt; chạy acceptance tích hợp khi LIVE được cho phép. Đây là backlog phụ thuộc, chưa triển khai ngầm trong MOCK hoặc bằng env flag.

**Điểm dừng:** MOCK vẫn là default, `HttpApiClient` vẫn `LIVE_BLOCKED`. IT demo kết thúc CONFIRMED; không thêm CLOSED. Không có commit/push, migration, ghi database thật hoặc bật LIVE.
