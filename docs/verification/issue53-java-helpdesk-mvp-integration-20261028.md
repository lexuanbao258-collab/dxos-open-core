# Issue #53 — Java-authorized Helpdesk MVP: bằng chứng tích hợp PR #74

**Ngày:** 2026-10-28

**Trạng thái:** `LOCAL_MVP_TEST_PASS / DRAFT / REVIEW_PENDING / LIVE_BLOCKED / DO_NOT_MERGE`

**Nhánh:** `feature/issue53-java-helpdesk-mvp-20261028`

**Implementation HEAD:** `c45c9a3d5162379d6f7a43ab8e362ff3bfcb4b13`
**Clean base:** `main@e06fa429cfd92b032c166eb3ebf32803d13191c2`; PR trước tích hợp: `218fc27be83c268c0f285b1435aad3ed82ee4eae`.

## Nguồn và bảo toàn

Nguồn thực tế là worktree `D:/olp/dxos-helpdesk-phase1-jdbc`, nhánh
`refactor/helpdesk-phase1-jdbc`, HEAD `df8ee63135d16e268718a7eba66a8ebcf8095fff`,
cộng candidate Java/PG17 chưa commit. Đã sao lưu patch, cả 9 file thay đổi
(3 tracked, 6 untracked) và SHA-256 trước khi chuyển. Đối chiếu sau kiểm thử/commit:
HEAD, trạng thái Git và byte của cả 9 file gốc giữ nguyên; không reset/clean/stash nhánh gốc.

Worktree PR riêng: `D:/olp/dxos-helpdesk-phase1-jdbc/backend/build/pr74-worktree`.
Chuyển chọn lọc 24 file triển khai/test/setup/runbook; không merge/cherry-pick toàn bộ PR #63.
Bản sao nguồn và manifest nằm tại `backend/build/pr74-source-snapshot/` của worktree gốc.

Đã nhận cập nhật đồng thời `48ea3ab13daaba84aaf8cdf0e74edba465f8b381`: định hướng
Data stack kết hợp PG17 + MinIO qua `ObjectStoragePort`, PR #34 và gate kiểm thử
storage riêng; adapter MinIO chưa được xác minh, PR filesystem #43 đã đóng.
Theo yêu cầu trực tiếp của lượt này, phần MinIO/S3 vẫn **ngoài phạm vi PR #74**:
không chuyển adapter, dependency, container, volume hay cấu hình storage vào MVP.
Gate full-stack tương lai vẫn cần API Backend có authorization, private buckets,
kiểm thử restart/negative và cơ chế recovery; không có atomic commit PG+MinIO.
Lịch sử commit remote được giữ nguyên; không force-push hoặc thay thế quyết định đó
bằng tuyên bố storage đã được triển khai/kiểm thử trong Backend MVP.

## Phần triển khai

- Đủ 9 route bảo vệ dưới `/api/v1/mvp/requests`: create, list, detail, triage,
  assign, start, resolve, confirm, history. `it-helpdesk@1` có 5 state/4 transition;
  assignment có version riêng và không thêm Workflow history.
- Principal từ JWT đã xác minh signature/JWKS, issuer, audience, lifetime và token type.
  Tenant do Backend cấu hình; exact authority, membership, resource grant và tư cách
  submitter/coordinator/current assignee được kiểm tra trong Java. Requester mất tư cách
  submitter không còn được confirm, kể cả còn grant.
- 401/403/hidden-404; lọc list trước pagination; detail/history/allowedActions cùng policy.
  Không nhận actor/tenant/role do client tự khai.
- Business facts, ACL và Workflow state/history dùng cùng JDBC connection/transaction;
  tenant lock bảo vệ trước policy edits; stale/concurrent losing writes bị chặn.
  Lỗi activation/history rollback toàn bộ. Giữ `COMMIT_OUTCOME_UNKNOWN`, không tự retry.
- PG17 loopback, runtime không owner/superuser; API mặc định tắt. Prototype Request
  controllers tắt khi bật Helpdesk; generic Workflow controller vẫn không được đăng ký.
- Schema Helpdesk riêng ngoài Flyway chỉ dùng relations/constraints và private invoker
  integrity/locking triggers. Không chuyển `Stage6WorkflowPersistence`, guarded SQL
  authorization, `wf_port_*`, `helpdesk_open/request/list` hoặc SECURITY DEFINER.
  Không thêm Supabase/MinIO/S3; byte Flyway V1–V4 và Workflow Core giữ nguyên so với main.

## Kết quả thực thi

JDK **21.0.11**, Gradle **8.14.3**, PostgreSQL **17.11** (`170011`),
Keycloak **26.8.1** local riêng với synthetic users và token do IdP thật ký.

| Kiểm tra | Kết quả |
|---|---|
| Focused HelpdeskConfiguration + HelpdeskHttpPostgres | **PASS: 49, FAIL: 0, SKIP: 0** |
| Full Gradle `test build`, 60 JUnit suites | **PASS: 999, FAIL: 0, SKIP: 0** |
| 9 PostgreSQL Workflow integration suites, gồm persistence 60 test | **PASS**, runtime DB không superuser |
| B06: Keycloak → HTTP filter → Java authorization → PG17, đủ 9 API | **PASS** |
| JWT hết hạn/sai issuer/audience/type/unsigned/forged; tenant/resource/role negatives | **PASS** |
| Stale versions, concurrent assignment/resolution, revoke race, rollback, unknown COMMIT | **PASS** |
| Clean setup bằng script, runtime TCP login/catalog, restart giữ definition/data | **PASS** |
| PowerShell 5.1 parse, staged diff whitespace, Core/V1–V4, hash nguồn gốc | **PASS** |
| Dọn fixture mới, inventory container có trước giữ nguyên | **PASS** |
| Portal → BFF → Backend E2E; review độc lập Yến/Ánh | **SKIP / PENDING** |
| Postman CLI | **SKIP** — chưa cài; kiểm thử HTTP/JUnit/JDBC đã chạy thật |

Lượt full đầu: **979 PASS / 8 FAIL / 6 SKIP**. Hai nhóm launcher PowerShell cũ
kế thừa PSModulePath của PowerShell 7 sang PowerShell 5.1. Tái hiện hẹp: 7/10 FAIL;
cùng 10 test PASS khi dùng Windows module path. Chỉ sửa môi trường harness,
không sửa/bỏ assertion launcher. Sáu test OIDC ban đầu thiếu token; lượt cuối đã
cấp `OIDC_TEST_ACCESS_TOKEN` từ Keycloak fixture và chạy đủ, không SKIP.

Lệnh focused: `gradlew.bat --no-daemon --rerun-tasks test --tests '*HelpdeskHttpPostgresTest' --tests '*HelpdeskConfigurationTest' --console=plain`.
Lệnh full: `scripts/workflow-verify-local.ps1` → `gradlew.bat test build --no-daemon`.
Harness cấp các biến disposable/JWT và dùng
`$env:PSModulePath = [Environment]::GetEnvironmentVariable('PSModulePath','Machine')`
trước khi gọi Gradle từ PowerShell 7.

Bằng chứng local tại worktree gốc:
`backend/build/pr74-evidence-20261028-164449/` (focused/full JUnit XML, logs,
runtime, inventory trước/sau, SHA-256 code) và `backend/build/pr74-clean-setup.log`.
Giữ riêng lượt lỗi ở `backend/build/pr74-evidence-20261028-163258/`.
Không commit secrets, token, realm import hay build artifacts. Code đã test được
đối chiếu SHA-256 tại implementation HEAD; sau test chỉ bỏ dòng trắng EOF của SQL.

## Blocker và phạm vi chưa chấp nhận

PR giữ **Draft** để Yến review security và Ánh review API/DTO tại HEAD hiện hành.
Portal/BFF real-login E2E chưa chạy; Keycloak PASS ở đây là fixture riêng, không phải
hệ thống identity dùng chung. Production D2/D3/D4 SQL security engine vẫn deferred.
Chưa merge, deploy, đổi production exposure hoặc bật LIVE.
## Bổ sung: kiểm thử E2E thủ công qua Postman (2026-10-28)

**Loại bằng chứng:** thao tác HTTP thủ công qua Postman GUI, thực hiện sau các lượt JUnit/Gradle nêu trên. Đây là một **luồng nghiệp vụ Helpdesk thực tế trong môi trường local cô lập**, không phải kiểm thử Portal/BFF trên trình duyệt, không phải triển khai tại doanh nghiệp, và không phải một lượt chạy lại 999 unit/integration tests.

**Môi trường:** Backend Spring Boot/Java 21 tại \`127.0.0.1:8080\`; Keycloak 26.8.1 (realm \`tricore\`, issuer \`http://127.0.0.1:8180/realms/tricore\`); PostgreSQL 17.11 disposable tại \`127.0.0.1:55441\`, database \`helpdesk_disposable_demo_20261028\`, schema \`helpdesk_demo\`. Xác thực Postman dùng Authorization Code + PKCE và tài khoản thử nghiệm riêng theo ba vai trò Employee, Coordinator, Worker. Không sử dụng bearer token, mật khẩu, client secret, hoặc dữ liệu thật của doanh nghiệp làm minh chứng.

**Request ID duy nhất được theo dõi:** \`7be09ac9-d928-4349-97d8-d479d47a50a9\` (dữ liệu demo local).

| Bước | Vai trò | HTTP/API | HTTP nhận được | Kết quả thực tế |
|---|---|---|---|---|
| 1. Create | Employee | \`POST /api/v1/mvp/requests\` | **201 Created** | Request \`IT_HELPDESK\`; \`SUBMITTED\`, \`version=0\`, \`assignmentVersion=0\` |
| 2. Triage | Coordinator | \`POST /api/v1/mvp/requests/{id}/triage\` | **200 OK** | \`TRIAGED\`, \`version=1\` |
| 3. Assign | Coordinator | \`POST /api/v1/mvp/requests/{id}/assign\` | **200 OK** | Gán đúng \`assigneeId\` canonical lấy từ \`eligibleAssignees\`; \`assignmentVersion=1\`; workflow vẫn \`TRIAGED\`, \`version=1\` |
| 4. Start | Worker | \`POST /api/v1/mvp/requests/{id}/start\` | **200 OK** | \`IN_PROGRESS\`, \`version=2\` |
| 5. Resolve | Worker | \`POST /api/v1/mvp/requests/{id}/resolve\` | **200 OK** | \`RESOLVED\`, \`version=3\`, có nội dung xử lý |
| 6. Confirm | Employee | \`POST /api/v1/mvp/requests/{id}/confirm\` | **200 OK** | \`CONFIRMED\`, \`version=4\`, \`assignmentVersion=1\`, \`allowedActions=[]\` |
| 7. History | Employee | \`GET /api/v1/mvp/requests/{id}/history\` | **200 OK** | Trả về **đúng 4** record workflow transition, versions 1–4 |

**History quan sát từ response JSON thực tế:**

| Action | Trước → Sau | Version | \`occurredAt\` (UTC) |
|---|---|---|---|
| \`triage\` | \`SUBMITTED → TRIAGED\` | 1 | \`2026-10-28T14:10:56.188128500Z\` |
| \`start_work\` | \`TRIAGED → IN_PROGRESS\` | 2 | \`2026-10-28T14:23:23.678576100Z\` |
| \`resolve\` | \`IN_PROGRESS → RESOLVED\` | 3 | \`2026-10-28T14:24:42.008512500Z\` |
| \`confirm\` | \`RESOLVED → CONFIRMED\` | 4 | \`2026-10-28T14:29:26.190413500Z\` |

\`assign\` là cập nhật phân công có version riêng, **không** được tính là workflow transition; vì thế 6 thao tác ghi nghiệp vụ tương ứng đúng 4 bản ghi History, không phải thiếu history.

**Nguồn minh chứng và giới hạn:**
- Kết quả trên được đối chiếu từ các ảnh response Postman do người chạy demo cung cấp trong cuộc trao đổi ngày 28–29/10/2026. Đây là **bản ghi kết quả quan sát**, không phải log tự động có chữ ký/chứng thực độc lập.
- **Chưa tải các ảnh gốc lên GitHub trong lần cập nhật này**; không khẳng định có screenshot attachment, ảnh CI hay Postman CLI collection mới trong repo. Cần cung cấp ảnh đã che JWT/token, cookie và thông tin nhạy cảm nếu reviewer yêu cầu bằng chứng thị giác.
- Luồng đã dùng một Request ID đi tới \`CONFIRMED\`. **Không phát lại các lệnh POST trên ID này**; nếu cần tái hiện thì tạo một disposable request mới với token/role thích hợp.
- Kiểm thử thủ công này xác nhận các endpoint **Create, Triage, Assign, Start, Resolve, Confirm, History**; không tuyên bố đã thao tác thủ công độc lập với List/Detail trong cùng lượt. Phạm vi test tự động cho 9 API được ghi riêng ở phần "Kết quả thực thi".
- **Vẫn chưa PASS:** Portal → BFF → Backend bằng browser + SSO/session thật; Facility Request E2E; MinIO runtime; deploy doanh nghiệp/production và independent review. Không đổi cổng \`DRAFT / REVIEW_PENDING / LIVE_BLOCKED / DO_NOT_MERGE\`.

**Kết luận của lượt Postman:** \`LOCAL_BACKEND_HTTP_E2E_PASS\` đối với **một** request \`IT_HELPDESK\` end-to-end, kết thúc ở \`CONFIRMED\` cùng 4 history transitions. Đây là bằng chứng bổ sung cho PR #74, **không phải sign-off full-stack hoặc phê duyệt merge/LIVE**.

