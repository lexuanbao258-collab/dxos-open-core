# APP-14 — Portal MVP Presentation Integration

Ngày kiểm tra: 26/10/2026. Phạm vi: DX-OS Internal Service Portal, IT Helpdesk MVP v0.1, fake transport. **APP-14 PASS trong phạm vi kiểm thử độc lập; LIVE_BLOCKED giữ nguyên.** Không thay thế approval nghiệp vụ/security của Bảo/Yến.

## Presentation binding

Tái sử dụng APP-10 `HelpdeskPortalRequestPresentation`/list/history mapper, APP-11 `AssignmentForm` + `helpdeskAssignmentFormProps`, APP-13 `HelpdeskRequestService`/`HelpdeskApiClient`/wire codecs. Không viết lại adapter, DTO hoặc Workflow state machine trong UI.

`MvpHelpdeskRequestPort` là structural port gồm chín methods của Application service đã có, trả đúng presentation model APP-10. Nó là binding cụ thể cho contract PR #63 HEAD `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`; không ép port normalized `MvpRequestPort` candidate cũ hoặc `ServiceRequest` MOCK thành contract này. Giữ nguyên implementation và tests của các phần cũ.

`PortalProvider` nhận optional `helpdeskService`; `App` chuyển injection này vào provider. Không có default MVP service, transport, host hoặc environment flag. `main.tsx` vẫn render `<App />`; `portalService.ts` vẫn chọn `MockRequestService`. Route MVP khi chưa inject hiển thị chưa có service kiểm thử và không gọi mạng. Link MVP trong sidebar chỉ có khi được inject.

| Màn hình                     | Model và binding                                                                                                          | Kết quả |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------- |
| `/mvp/helpdesk`              | List `items`, authoritative state, optional display names; query after + limit 20; nextCursor, trang đầu, explicit reload | PASS    |
| `/mvp/helpdesk/new`          | Create chỉ có title/description; adapter gửi IT_HELPDESK; thành công quay về authoritative List                           | PASS    |
| `/mvp/helpdesk/:id`          | Detail + History được đọc qua cùng injected port; nội dung, resolution, hai version, requester/assignee ID/name           | PASS    |
| Action panel                 | Triage/Start/Resolve/Confirm chỉ từ exact server allowedActions; labels chỉ phục vụ hiển thị                              | PASS    |
| Assignment cạnh action panel | Reuse form và binding APP-11, chỉ mount với `assign`; authorized Detail eligibility và assignmentVersion                  | PASS    |
| History trong Detail         | Chỉ transitionId, sourceState, targetState, version, occurredAt, theo thứ tự service; không actor/note/event ID           | PASS    |

Reuse shell/sidebar, PageHeading, Loading, Empty, ErrorState, panel/detail-layout/form-layout, metadata, action-panel, table/timeline classes của Portal. Không sửa CSS, không redesign, không tạo timestamps/priority/location/department/timeline giả cho model MVP. State lạ vẫn giữ nguyên. Tên thiếu/ẩn hiển thị thông báo thiếu tên, không thay bằng persona hoặc suy tên từ ID; ID được trình bày riêng theo đúng field Backend cung cấp.

Facility Maintenance và các màn MOCK hiện tại tiếp tục dùng service/model cũ. Không bổ sung CLOSED; fake journey dừng ở CONFIRMED.

## Assignment, concurrency và lỗi

- Assign gửi `assigneeId` + `expectedAssignmentVersion`; Workflow transitions gửi `expectedVersion`; resolve thêm nonblank resolution. Không trộn hai version.
- Eligibility absent/null/empty, ID không hợp lệ hoặc assignmentVersion thiếu không được submit. Workflow version thiếu cũng khóa transitions, không thay bằng assignmentVersion.
- Không suy quyền từ role/state. Chốt TRIAGED trong binding APP-11 chỉ là fail-closed bổ sung sau khi đã có exact `assign`.
- Không optimistic-update. Workflow writes confirmed mới reload Detail/history; assign dùng binding sẵn có, confirmed write rồi reload Detail, tiếp tục refresh presentation/history.
- Khóa pending ở provider chặn mutation đồng thời trên cùng request và giữ khóa qua điều hướng. Khóa tại component còn chặn cửa sổ giữa response rất nhanh và render/điều hướng. Bỏ qua click thứ hai của double-click trên Workflow actions để tránh chạm vào action mới vừa thay vị trí.
- Loading/empty/read error có presentation riêng. Lỗi read không để lộ Detail cũ; obsolete response/401 từ service đã thay thế bị bỏ qua.
- 401 của service kết thúc phiên Portal qua `expireSession`; HTTP client vẫn gọi callback và đóng protected operations như APP-13. Không thêm OIDC/token framework.
- Mutation 403/404 ẩn Detail cũ, dùng safe error; không xuất diagnostic/resource từ error body. Explicit reload thành công mới sử dụng dữ liệu và actions mới được phép đọc.
- 409 khóa ghi, yêu cầu explicit Detail/history reload thành công. Không tự reload/retry mutation; reload không replay command.
- Network/unknown write khóa ghi, không retry. Unknown stop giữ qua explicit read reload, điều hướng và đổi demo role trong cùng provider. Không tự kết luận đã đối soát thành công từ một lần đọc.
- Stops nằm trong bộ nhớ provider, không phải durable reconciliation hoặc auth/session contract. Hard refresh tạo phiên fake mới; hành vi reconciliation xuyên phiên/LIVE còn cần Bảo/Yến phê duyệt.

## Fake journey và cách chạy lại

Test-only fake transport trả Backend-shaped DTO, đi qua HTTP client → Application service → APP-10 mapper → feature UI. Fake capabilities/identity/history được định nghĩa trong fixture kiểm thử, không được import vào `main.tsx` hoặc default production entry. UI không tự tính capabilities từ fixture state.

Journey đã chạy: **Create → List → Detail → Triage → Assign → Start → Resolve → Confirm → History**. Sáu POST đúng một lần; assignment tăng assignmentVersion 0 → 1, giữ Workflow version 2; start dùng expectedVersion 2, resolve dùng 3, confirm dùng 4. History có bốn Workflow transitions; assignment không thêm Workflow history.

Component integration: `src/features/helpdesk/HelpdeskPortal.test.tsx` chạy actual `App`/provider/router với injected service và fake transport, không phải host giả thay Portal.

Browser entry riêng: `tests/mvp-preview.html` + `tests/mvp-preview.tsx`. Khởi tạo fake service và mở phiên Demo theo flow có sẵn; không thay root entry production. Chạy:

```powershell
cd frontend
npx.cmd playwright test --config tests/mvp.playwright.config.ts
```

Config chỉ chạy `helpdesk-mvp.spec.ts`, dùng loopback Vite dev server và ghi artifacts vào thư mục tạm. Khi chạy bộ smoke production mặc định, test fake-preview này chủ động SKIP vì entry kiểm thử không được đóng gói vào production. Không có flag bật LIVE. Có thể xem thủ công `/tests/mvp-preview.html` khi chạy Vite dev; chọn Vào phiên demo → IT Helpdesk MVP · kiểm thử.

## Files tạo/sửa trong APP-14

Sửa bốn file có sẵn:

| File                                             | Thay đổi so với local đầu lượt                                                                                                 |
| ------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| `frontend/src/App.tsx`                           | Optional MVP service injection, route feature và conditional sidebar link; 14 dòng thêm, 3 dòng thay/xóa so với snapshot local |
| `frontend/src/context.tsx`                       | Optional concrete MVP port và pending/error stops trong bộ nhớ provider; giữ default RequestService injection                  |
| `frontend/src/components/AssignmentForm.tsx`     | Chỉ cập nhật comment về APP-14 mount; logic/component UI giữ nguyên                                                            |
| `frontend/src/services/mvp/assignmentBinding.ts` | Chỉ cập nhật comment về model MVP riêng; binding logic giữ nguyên                                                              |

Tạo mười file:

- `frontend/src/services/mvp/helpdeskPortalPort.ts`
- `frontend/src/features/helpdesk/HelpdeskPortal.tsx`
- `frontend/src/features/helpdesk/useHelpdeskRead.ts`
- `frontend/src/features/helpdesk/HelpdeskPortal.test.tsx`
- `frontend/src/test/fakeHelpdeskTransport.ts`
- `frontend/tests/mvp-preview.html`
- `frontend/tests/mvp-preview.tsx`
- `frontend/tests/helpdesk-mvp.spec.ts`
- `frontend/tests/mvp.playwright.config.ts`
- `docs/application/issue39-app14-presentation-integration.md`

Không sửa Backend/Core/Gateway/Identity/PostgreSQL, migration, OIDC, composition default, screenshots/evidence hoặc tài liệu lịch sử. Không format toàn frontend; Prettier chỉ files liệt kê trên.

## Kiểm thử cuối

| Check                                                                              | Kết quả                                                                                                             |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| `npx.cmd tsc --noEmit`                                                             | PASS                                                                                                                |
| Full unit/component/adapter suite, `npm.cmd test -- --reporter=dot --maxWorkers=2` | PASS: 15 files, 353 tests, 0 FAIL/SKIP; gồm 25 APP-14 cases và toàn bộ 328 tests cũ                                 |
| APP-14 fake Chromium journey                                                       | PASS: 1 test, desktop 1440 và mobile 390, không overflow, WCAG 2A/AA/2.1AA không violation được phát hiện           |
| Default production Chromium regression                                             | PASS: 5 tests — auth blocked, 8 screens + no API/external requests, IT CONFIRMED, Facility MOCK, unknown write stop |
| Production build vào temp                                                          | PASS, tsc chạy riêng PASS; Vite không ghi đè frontend/dist                                                          |
| `LIVE_BLOCKED` + default MOCK regression                                           | PASS, có trong full suite; browser không phát sinh API/LIVE request                                                 |
| Default production bundle audit                                                    | PASS: không có fake transport/fixture identities, vẫn có LIVE_BLOCKED                                               |
| Security heuristic script                                                          | PASS: 62 source/bundle files; không thay security review                                                            |
| Prettier selected files, git diff --check, whitespace checks cho untracked mới/sửa | PASS                                                                                                                |
| Hash/status before/after                                                           | PASS, chi tiết bên dưới                                                                                             |
| Real Backend/PostgreSQL/OIDC, LIVE acceptance, toàn bộ responsive/browser suite    | SKIP theo scope; không suy diễn acceptance LIVE                                                                     |

Trong quá trình kiểm tra, test phát hiện cửa sổ double-create đã được sửa bằng khóa invocation. Browser test ban đầu phát hiện fixture cung cấp start trước assignment và click thứ hai chạm action thay vị trí; đã sửa fixture theo Backend policy và bỏ click thứ hai trên action button. Một lượt full suite chạy đồng thời build/browser có hai timeout 5 giây (NewRequest cũ và journey mới), không có assertion failure; đã chạy lại toàn bộ suite với 2 workers sau khi các tác vụ nặng dừng. Kết quả cuối là PASS như bảng trên, không sửa timeout hoặc test cũ để che lỗi.

Production artifact: `%TEMP%/dxos-app14-final-20261026-173313/`. Default production browser artifacts: `%TEMP%/dxos-app14-production-browser-173313/`; fake browser artifacts trong `%TEMP%/dxos-app14-browser-*/`. Các preview/dev process do lượt kiểm tra khởi tạo đã dừng sau khi tests hoàn tất; runner thoát bình thường. Không ghi ảnh mới vào evidence cũ.

## Git và bảo toàn

- Branch trước/sau: `feature/issue39-university-ui`.
- HEAD trước/sau: `00b10fa43d01cc3dcca725b2ed66a43bdaa92268`.
- Index trước/sau trống, 0 staged files.
- Trước: 25 tracked modified, 43 untracked files. Sau: 25 tracked modified, 53 untracked files (10 file mới APP-14; hai file comment-only đã untracked từ trước).
- Snapshot SHA-256 đầu lượt có 286 files. Sau: không mất file; chỉ bốn file có sẵn nêu trên đổi bytes, 282 file còn lại giữ nguyên. Đặc biệt bảo toàn các thay đổi local có trước trong App/context; không dùng diff so với HEAD để nhận thay đổi cũ thành APP-14.
- Hash/status trước và sau, cùng bản sao bốn file trước sửa: `%TEMP%/dxos-app14-before-20261026-172044/`.
- Không commit, push, merge, rebase, reset, stash, clean hoặc xóa file cũ chưa commit.

## Blockers thuộc Bảo/Yến và điểm dừng

**Không còn blocker để chạy APP-14 với fake service.** Blocker model của lượt APP-11 đã được xử lý trong feature MVP riêng; legacy MOCK vẫn dùng model cũ.

**Bảo:** ratify contract/matrix và quyết định Backend candidate tại exact PR #63 HEAD; duyệt quy trình reconciliation/định danh sau unknown create và điều kiện mở khóa xuyên phiên. Không tự bổ sung API/idempotency hoặc business rules để gỡ các quyết định này.

**Yến:** security/authorization/visibility review exact HEAD, approved auth/session/token/expiry/logout và transport wiring trước bất kỳ LIVE integration nào. Demo role hiện tại chỉ bảo vệ phiên kiểm thử Portal, không đại diện identity/quyền Backend thật.

**Điểm dừng APP-14:** MOCK mặc định, HttpApiClient LIVE_BLOCKED, không concrete LIVE transport hoặc token storage; không bật LIVE, không triển khai Backend hoặc ghi database thật. Không tự làm APP tiếp theo hoặc commit/push.
