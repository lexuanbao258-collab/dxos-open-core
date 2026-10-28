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
