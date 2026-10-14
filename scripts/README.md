# Scripts

Nơi lưu script hỗ trợ phát triển và kiểm tra khi cần.
Build và test hiện dùng Gradle Wrapper trong `backend/`.

`workflow-verify-local.ps1` tạo PostgreSQL 17.11 disposable trên loopback, CA đúng/sai
và credentials ngẫu nhiên trong bộ nhớ, chạy full JDK 21 `test build`, rồi dọn fixture.
Cần Docker, JDK 21 trên PATH và OpenSSL (có trong Git for Windows). Script yêu cầu
chín Workflow integration suites chạy và không có FAIL/SKIP; không nhận endpoint cloud.
Xem `backend/README.md` để chạy database local giữ dữ liệu và boot ứng dụng từ source.

Luồng Supabase mặc định của Issue #25: dùng Direct Connection, tài khoản `postgres`
hiện có và `DB_*`/`PGSSLROOTCERT` từ secret environment; chạy
`workflow-read-only-preflight.ps1` để kiểm tra JDBC `verify-full` chỉ đọc trước.
Migrations/smoke cloud cần phê duyệt ghi riêng; không chạy trong bước kết nối.

`workflow-stage-1a.ps1`, `workflow-stage-1b-1-readonly-audit.ps1` và
`workflow-provisioning-local-test.ps1` là công cụ hardening lịch sử/optional, không
phải các bước setup mặc định. Giữ hai custom roles hiện có ở `NOLOGIN`.
Các runtime-privilege/SECURITY DEFINER/event-trigger audit launchers cũng là công cụ
review optional; guard trong adapter/migrations và integration tests vẫn giữ nguyên.
Danh sách file cần giữ hoặc có thể loại khỏi PR nằm trong
[`issue-25-readiness.md`](../docs/evidence/issue-25-readiness.md).

`workflowDevelopmentSmoke` là task riêng trong backend, yêu cầu argument và opt-in
development chính xác, schema cố định, tài khoản postgres, Direct verify-full và
phê duyệt riêng cho lệnh ghi. Runner production không kích hoạt chế độ này qua
environment/profile. Fixture local kiểm tra đường development và regression
production; xem lệnh preflight/cloud approval trong `backend/README.md`.

`workflow-schema-bootstrap.ps1 -ExecuteApprovedBootstrap -CaCertificate <CA file>`
chỉ thực thi CREATE schema rỗng đã được owner phê duyệt, với conditional commit và
fresh read-only audit. Không hỏi lại approval; password qua secure environment hoặc
prompt ẩn. Cấu hình sai/precheck không đạt thì dừng, không tự sửa hoặc cleanup.
Không dùng launcher này cho Flyway hoặc smoke ghi.

Schema Bootstrap đã hoàn tất theo owner report COMMITTED_PASS/POSTCOMMIT_AUDIT=PASS,
BOOTSTRAP_EXIT=0: không chạy lại workflow-schema-bootstrap.ps1 hoặc CREATE SCHEMA.
Owner đã duyệt riêng V3/V4 pin; xem inventory/precheck/postcheck/recovery trong
kế hoạch Issue #25. Không chạy smoke cùng batch khi chỉ migration được duyệt.

workflow-approved-migrations.ps1 -ExecuteApprovedMigrations là launcher một mục đích
cho approval này: Direct endpoint/project cố định, WARP IPv6/JDK21/verify-full,
secure password prompt, fail-closed prechecks và fresh read-only audit.
Không bootstrap/smoke/role changes/recovery/retry. Codex chưa chạy cloud vì thiếu
credentials/CA. Thiếu child report sau handoff luôn COMMIT_UNKNOWN.

Owner đã báo V3/V4 COMMITTED_PASS, checksum/inventory/seed/postcommit audit PASS,
MIGRATIONS_EXIT=0. Không chạy lại bootstrap hoặc launcher migrations.
Cloud smoke đã được owner nghiệm thu COMMITTED_PASS, run-id cloud-20261015-01,
precheck/postcheck/fresh-JVM PASS, 2 instances / 4 history, exit 0.
Không chạy lại smoke và không xóa dữ liệu thử. Xem evidence và kế hoạch lịch sử ở phần
“Cloud smoke sau migrations” trong `docs/evidence/issue-25-readiness.md`.
`workflow-approved-smoke.ps1` chỉ cho phép `-ExecuteApprovedSmoke` sau approval,
hoặc `-AuditOnly` chỉ đọc để đối chiếu khi có lỗi. Run-id được cố định trong launcher;
password luôn qua prompt ẩn, không nhận qua tham số hay ghi file.
