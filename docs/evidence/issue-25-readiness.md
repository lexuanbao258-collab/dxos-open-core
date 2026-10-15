# Issue #25 — Hồ sơ tổng hợp trước PR

Trạng thái mới nhất ngày 2026-10-15: owner xác nhận V3/V4 và cloud smoke
`cloud-20261015-01` COMMITTED_PASS, precheck/postcheck/fresh-JVM persistence PASS,
2 instances / 4 history, exit 0. Đây là evidence owner cung cấp; agent không chạy
lại cloud. Xem mục “Nghiệm thu cloud và chuẩn bị PR” cuối tài liệu để lấy trạng thái
PR và danh sách staging mới nhất. Các kế hoạch, số test và phân loại cũ bên dưới
là lịch sử, không phải lệnh cần chạy tiếp. Không stage/commit/push/PR/merge trong phiên này.

## Worktree và WIP

- Worktree: C:/Users/Admin/AppData/Local/codex-with-chatgpt/worktrees/dxos-data-postgres-adapter.
- Branch: feature/data-postgres-adapter. Sau fetch origin: 1 commit trước origin/main, 0 commit phía main cần hợp nhất. Commit 837f5d8 của V3 đã có trước phiên này.
- Code, SQL, manifest và evidence của cả hai track đều ở worktree này; không cần copy hay cherry-pick WIP.
- Worktree gốc có WIP chỉ thêm dòng trắng ở WorkflowInstance.java, thuộc branch khác; giữ nguyên, không đưa vào Issue #25.
- Core WorkflowPersistencePort và V1/V2 không đổi so với origin/main. Không sửa Request, Identity, Storage hoặc đăng ký Workflow HTTP routes.

## Kết quả đã chạy

Lượt rà soát trước theo Issue cập nhật: JDK 21 chạy offline bốn unit suites
ReadOnlyPreflight/LiveGuard/RuntimePrivilegeAudit/SecurityDefinerReview:
78 PASS, 0 FAIL, 0 SKIP. Đây là unit/fault-model checks, không phải Direct TLS
handshake hay cloud verification thực tế. Lượt rà soát đó không chạy lại PostgreSQL
integration hoặc full build; bảng bên dưới là evidence của lượt local trước.
Checksum đối chiếu xác nhận adapter, V3/V4, manifest, mọi integration tests và WIP
worktree gốc giữ nguyên; không có file bị xóa hoặc thay đổi ngoài phạm vi dự kiến.

JDK 21.0.11, PostgreSQL 17.11 thật trên loopback; migrator và runtime là hai login riêng, runtime NOINHERIT. CA đúng chỉ có DNS localhost; CA sai và hostname 127.0.0.1 dùng cho các ca từ chối TLS.

| Lệnh/kiểm tra | PASS | FAIL | SKIP | Phạm vi bằng chứng |
| --- | ---: | ---: | ---: | --- |
| workflowIntegrationTest, lọc inheritedRuntimeTriggerPrivilegeStopsBeforeWrites | 5 | 0 | 0 | Quyền TRIGGER kế thừa thật và guard từ chối trước write |
| workflowIntegrationTest | 136 | 0 | 0 | Persistence 55; isolation 46; event-trigger guard 27; trigger review 8 |
| JDK 21 test build | 641 | 0 | 0 | Bao gồm 3 runtime-audit TLS, 7 SECURITY DEFINER TLS và Stage 1A rollback fixture |
| scripts/workflow-verify-local.ps1 | 641 | 0 | 0 | Chạy từ fixture mới, kiểm tra đủ 6 integration suites và cleanup; exit 0 |
| Runner migrate/smoke/read | — | 0 | 0 | MIGRATION_PASS, SMOKE_PASS, RESTART_READ_PASS từ các JVM riêng |
| PostgreSQL restart rồi read | — | 0 | 0 | Cùng 2 instances/4 history rows còn nguyên |
| Hai lần bootJar với runtime riêng, Flyway disabled | — | 0 | 0 | Cả hai health UP; đọc lại dữ liệu sau restart Spring PASS |

Báo cáo cũ có 5 FAIL/13 SKIP là trước sửa fixture: PostgreSQL 17 lấy mặc định membership INHERIT từ role NOINHERIT, nên quyền TRIGGER không trở thành quyền hiệu lực. WIP hiện tại đã dùng WITH INHERIT TRUE, SET FALSE; assert quyền hiệu lực bằng 1 và cả 5 ca guard đã chạy PASS. Không có lỗi adapter mới cần sửa để giải quyết nhóm này.

Các ca lost COMMIT dùng proxy JDBC chèn lỗi trước/sau COMMIT trên transaction PostgreSQL thật. Read độc lập xác minh cả trạng thái đã commit và chưa commit; không retry. Đây là fault model ở driver, không phải thử mất mạng Supabase thật. Rollback lỗi được phân loại COMMIT_OUTCOME_UNKNOWN; chỉ rollback được xác nhận mới cho phép OPERATION_FAILURE.

Test restart context đọc qua production WorkflowRuntime/WorkflowPersistencePort. Smoke read dùng JVM mới; bootJar/restart xác minh startup thực tế. WorkflowController vẫn là adapter có constructor injection, chưa expose route production vì principal/authorization có gate riêng.

## Checksum bản review

| Artifact | SHA-256 |
| --- | --- |
| V3, UTF-8 chuẩn hóa CRLF thành LF | 15d615162c09b0c843680cf0c3b9fe48b16cd637877eb91215d8698cd7bc4832 |
| V4, UTF-8 chuẩn hóa CRLF thành LF | 40ee5a6c3a357d7ebca6d198c9981760868fc872121e8caece6ae6e86634a3ef |
| Event-trigger manifest, raw bytes | D5DE8C9DEB62590653F2FB19D184C8AD9298D3A5386C98CF2B4B50EEFC4A3237 |
| Stage 1B-1 audit SQL, raw bytes | 654C5BDEB90A855A01CD7810B761DE07E2FBA846DE2F4F3037978561DA6480FA |

V3/V4 khớp manifest 6 triggers/8 functions/helpers. Integration xác minh V1/V2 upgrade, V3/V4 history/checksum, 5 Workflow tables, 2 functions, 9 triggers và seed 2 definitions/7 states/5 transitions. Audit SQL hiện tại có pg_settings/context/source/SET privileges, sampling, debug, auto_explain, pgAudit và role/database override names; launcher pin đúng hash mới. Không dùng hash SQL cũ.

Flyway history đọc thực tế sau local smoke/restart: V3 checksum -844876185, V4 checksum 1349884712, cả hai success=true. Row counts sau restart: definitions/states/transitions=2/7/5, instances/history=2/4.

Audit cuối qualify pg_catalog và launcher pin search_path=pg_catalog,pg_temp; SQL chạy read-only trên local 17.11 exit 0. PowerShell parse, git diff --check và secret-pattern scan trên 57 file dự kiến commit PASS. Scan bằng hai passwords sinh trong bộ nhớ không tìm thấy chúng trong source hoặc Spring startup logs.

## Đối chiếu Definition of Done

Nguồn: [GitHub Issue #25](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25).

| Yêu cầu | Local | Còn thiếu |
| --- | --- | --- |
| Actual PostgreSQL integration, local route và shared Supabase verified run | PASS local; owner xác nhận cloud migrations/smoke PASS | Không chạy cloud lại trong phiên PR |
| Immutable versions, duplicate/replay, two writers, version-0 activation, forged proposals, history atomicity/rollback | PASS local DB thật; bounded cloud smoke owner báo PASS | Review PR |
| Instant precision/range và round-trip | PASS lossless epoch seconds+nanos, toàn miền Java Instant; không làm tròn | Ánh review/đồng thuận chính sách trong PR |
| Lost COMMIT response, uncertainty và reconciliation limits | PASS fault model với DB thật; README ghi giới hạn | Không tuyên bố đã fault-test mạng cloud |
| Restart và Flyway upgrade V1/V2 | PASS local context/JVM/PostgreSQL/Spring restart; cloud fresh-JVM read owner báo PASS | Không tuyên bố đã restart Spring service trên cloud |
| Production adapter inject được, Core port giữ nguyên | PASS một production port/runtime bean; constructor controller giữ nguyên | Không đăng ký route trước identity/authorization |
| Full JDK 21 test/build/migrations, PR review/merge commit | Các lượt local được ghi ở mục nghiệm thu PR cuối tài liệu | Owner approval, reviewer approval và merge commit thực tế |

## Supabase hiện tại và luồng mặc định

Quyết định trong [Issue #25 cập nhật 2026-10-14](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25)
thay kế hoạch Stage 1A/1B làm prerequisite. Direct Connection dùng tài khoản
`postgres` hiện có, port 5432/database postgres, IPv6 và TLS verify-full với CA
tin cậy. Hai roles `dxos_wf25_migrator` và `dxos_wf25_runtime` đã tồn tại ở
NOLOGIN; giữ nguyên, không chạy lại provisioning, cấp credentials hay LOGIN.

1. Cấu hình DB_* và PGSSLROOTCERT qua secret environment; chạy
   `scripts/workflow-read-only-preflight.ps1`. Launcher hỗ trợ Direct Connection
   và giữ session pooler cho các audit cũ. Không Spring/Flyway/cloud writes.
2. Schema dxos_wf25_smoke_01 đã bootstrap thành công theo owner report; không chạy lại.
   V3/V4 và bounded cloud smoke/restart-read đều đã được owner xác nhận PASS.
   Không chạy lại bootstrap, Flyway hoặc smoke; kế hoạch dưới đây là lịch sử.
3. Sau approval riêng mới kiểm chứng cloud. Không clean/repair/baseline, cleanup
   dữ liệu smoke hoặc tự retry COMMIT unknown. Nếu V3 đã commit nhưng V4 lỗi,
   dừng và đối chiếu history/catalog trước recovery được duyệt.

Tài khoản postgres là ngoại lệ development cho kiểm chứng một lần, không phải
mô hình runtime production. Least-privilege runtime identity phải có trước
triển khai lâu dài/shared production; SCRAM provisioning, Stage 1B-1 và per-role
ACL hardening là optional future work, không chặn kết nối ban đầu.

**Development integration đã chuẩn bị:** task/entrypoint riêng
`workflowDevelopmentSmoke` / `WorkflowDevelopmentSmokeRunner` yêu cầu argument
`--development-integration`, opt-in chính xác, schema cố định đã kiểm tra, Direct
endpoint đúng project, tài khoản postgres và verify-full/CA. Chỉ migrate/smoke có
write approval riêng; runner tạo từ check/read không thể gọi hai thao tác ghi.
Constructor/main production không đọc các cờ development và vẫn bắt buộc runtime
khác owner/least-privilege. Không đăng ký development bean hoặc task trong luồng
bootRun/build. Mỗi connection development kiểm tra identity/TLS/private schema;
guard, bundle/history/checksum, durability và default ACL checks giữ nguyên.
Cloud development smoke đã nghiệm thu theo owner report; review/merge còn chờ approval.

## Kiểm chứng development integration trên disposable PostgreSQL

- Chạy `scripts/workflow-verify-local.ps1` bằng JDK 21, Docker local và PostgreSQL
  17.11 disposable với CA/hostname thật. Fixture chỉ bind loopback, credentials
  ngẫu nhiên trong memory/stdin và được cleanup sau mỗi lượt.
- Lượt đầu: 679 tests, 678 PASS / 1 FAIL / 0 SKIP. Assertion mới đòi
  IllegalArgumentException khi bundle drift; guard hiện có đúng là trả SQLException
  đã làm sạch. Sửa assertion theo contract hiện có, không nới guard.
- Lượt cuối: **680 PASS / 0 FAIL / 0 SKIP**, đủ **7 integration suites**;
  JDK 21 `test build` BUILD SUCCESSFUL, exit 0, bootJar/plain jar được build.
- Development unit suite 24 tests và integration suite 12 tests: opt-in/argument/
  schema/write approval, endpoint/project/credentials boundary; check trước migration
  không ghi; CLI migrate/smoke/read bằng JVM riêng; đúng 2 instances/4 history rows,
  replay từ chối, race activation/transition và Instant nanos; snapshot roles,
  memberships/default ACL và sentinel ngoài schema giữ nguyên.
- Actual JDBC TLS CA sai/hostname sai đều chặn trước migration. PUBLIC schema access,
  owner sai, table lạ, checksum/bundle drift chặn trước schema/smoke writes.
  Production CLI với cùng postgres owner vẫn từ chối mặc dù có development flags.
- Không dùng credentials/cloud endpoints trong kiểm chứng; không thực thi Supabase
  preflight, migrations, smoke, role/privilege changes hoặc Git writes trong lượt này.

Ba file mới cần giữ cùng code development (ngoài danh mục 57 file đã phân loại):

- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeRunner.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeRunnerTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowDevelopmentSmokeIntegrationTest.java

## Kết quả cloud do owner cung cấp và nhãn kết nối

Owner báo JDBC_PREFLIGHT=PASS, LIVE_GUARD=PASS, schema_target=ABSENT,
bundle_sha256=PASS, manifest_triggers=6, manifest_functions=8 và
WRITE_READINESS=BLOCKED. Đây là evidence owner cung cấp; agent chưa kết nối cloud
trong lượt lập kế hoạch vì phiên shell không có DB_* hoặc PGSSLROOTCERT.

Preflight chỉ in client_direct_tls sau khi kết nối bằng host dạng
db.<project-ref>.supabase.co và port 5432 qua verify-full/CA. Live Guard cũ in cứng
client_pooler_tls/pooler_backend_ssl cho cả Direct và pooler; nó không đổi host,
port hoặc route, mà dùng nguyên DB_HOST/DB_PORT. Nhãn cũ không chứng minh dùng
pooler. Đã sửa nhãn theo host và thêm connection_mode/endpoint_port, không xuất
host riêng của project hoặc credentials. Exact host/project và cùng endpoint cho
hai lần chạy cần owner xác nhận; không suy ra chúng từ nhãn guard cũ.

Kiểm chứng sửa nhãn: JDK 21 offline WorkflowLiveGuardTest 17 và
WorkflowReadOnlyPreflightTest 29: 46 PASS / 0 FAIL / 0 SKIP. Chỉ unit tests,
không phải agent chạy lại cloud; không chạy lại full integration/build trong lượt này.

WRITE_READINESS=BLOCKED là output cố định của catalog guard: PASS chỉ xác minh
catalog/bundle/read-only, không cấp phép DDL hoặc chứng minh runtime production.
Không yêu cầu Stage 1A/1B để xử lý trạng thái này.

## Kế hoạch bootstrap schema — owner đã phê duyệt

**Stage Schema Bootstrap đã hoàn tất; tuyệt đối không chạy lại launcher hoặc CREATE SCHEMA.**
Owner cung cấp kết quả:
SCHEMA_BOOTSTRAP=COMMITTED_PASS, POSTCOMMIT_AUDIT=PASS, SCHEMA_OWNER=postgres,
SCHEMA_EMPTY=YES, FLYWAY_HISTORY=ABSENT, OTHER_CLOUD_WRITES=NONE, BOOTSTRAP_EXIT=0.
Đây là evidence do owner cung cấp, không phải agent tự chạy lại cloud.
SQL/precheck dưới đây là lịch sử; điều kiện migration là schema PRESENT/rỗng.

SCHEMA_BOOTSTRAP_PLAN_READY=YES. Owner đã phê duyệt duy nhất CREATE schema rỗng,
conditional COMMIT khi toàn bộ precheck/postcheck đạt, và fresh read-only audit.
Không xin lại approval cho thao tác này. Flyway/seed/smoke ghi/role changes chưa được duyệt.
Chỉ tạo schema dxos_wf25_smoke_01 owner postgres. Kết nối duy nhất được dùng cho
bootstrap/migration sau phê duyệt: Direct db.<đúng-project-ref>.supabase.co:5432,
database postgres, session_user=current_user=postgres, sslmode=verify-full và CA
tin cậy từ environment. Không dùng URL có credentials/query overrides, session/
transaction pooler hoặc flags disposable local. Không boot Spring.

### Precheck (chỉ đọc, phải PASS lại ngay trước write)

- Owner xác nhận đúng Direct host/project và port; JDBC client xác minh CA/hostname,
  pg_stat_ssl.ssl=true; version/settings và guard 6 triggers/8 functions/bundle pin
  còn khớp. API exposed schemas trong Dashboard không có schema test.
- Chạy truy vấn sau trong transaction READ ONLY; tất cả booleans phải true. Query
  kiểm tra default ACL cũng là điều kiện hiện có của smoke runner; nếu false thì
  dừng/review, không tự đổi default privileges hoặc tạo custom roles.
- Snapshot roles/memberships/default ACL và catalog ngoài target để đối chiếu sau
  DDL; dữ liệu/function bodies/credentials không đưa vào repo/báo cáo.

```sql
SELECT current_user = 'postgres' AND session_user = 'postgres' AS identity_ok,
       pg_catalog.current_database() = 'postgres' AS database_ok,
       pg_catalog.current_setting('transaction_read_only') = 'on' AS readonly_ok,
       (SELECT ssl FROM pg_catalog.pg_stat_ssl
        WHERE pid = pg_catalog.pg_backend_pid()) AS ssl_ok,
       pg_catalog.has_database_privilege(current_user, 'postgres', 'CREATE') AS create_schema_ok,
       pg_catalog.to_regnamespace('dxos_wf25_smoke_01') IS NULL AS schema_absent,
       NOT EXISTS (SELECT FROM pg_catalog.pg_default_acl d
                   JOIN pg_catalog.pg_roles r ON r.oid = d.defaclrole
                   WHERE r.rolname = 'postgres' AND d.defaclnamespace = 0) AS default_acl_ok,
       (SELECT count(*) = 2 AND pg_catalog.bool_and(NOT rolcanlogin)
        FROM pg_catalog.pg_roles
        WHERE rolname IN ('dxos_wf25_migrator', 'dxos_wf25_runtime')) AS custom_roles_unchanged_nologin;
```

### SQL cần phê duyệt và transaction boundary

Đây là các SQL đã được owner phê duyệt cho bootstrap, chưa chạy cloud từ Codex.
executor mở connection mới verify-full, xác minh lại identity/target và bắt đầu
transaction READ WRITE tường minh. Không tái sử dụng preflight read-only connection.
Không dùng IF NOT EXISTS: nếu schema đã xuất hiện giữa precheck và CREATE, dừng.

```sql
BEGIN READ WRITE;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
CREATE SCHEMA dxos_wf25_smoke_01 AUTHORIZATION postgres;
```

Đây là DDL duy nhất: một schema và catalog ownership/dependencies tương ứng.
Không tables, functions, Flyway history, seeds, roles, credentials, grants hoặc
default privileges. Không thay objects hiện có trong public/auth/storage/extensions.
Reviewed pgrst_ddl_watch có thể queue NOTIFY pgrst/reload schema sau COMMIT; owner
phải chấp nhận tác động cache này. Reload không tự cấp quyền API.

### Postcheck trước COMMIT (cùng transaction)

Phải trả đúng một row và mọi boolean true. Nếu query lỗi/khác expected thì
ROLLBACK ngay; không sửa ACL để ép PASS và không gửi COMMIT như một batch vô điều kiện.

```sql
SELECT r.rolname = 'postgres' AS owner_ok,
       NOT EXISTS (SELECT FROM pg_catalog.aclexplode(
           COALESCE(n.nspacl, pg_catalog.acldefault('n', n.nspowner))) a
           WHERE a.grantee <> n.nspowner) AS private_acl_ok,
       NOT EXISTS (SELECT FROM pg_catalog.pg_roles api
           WHERE api.rolname IN ('anon', 'authenticated', 'service_role')
           AND (pg_catalog.has_schema_privilege(api.oid, n.oid, 'USAGE')
             OR pg_catalog.has_schema_privilege(api.oid, n.oid, 'CREATE'))) AS api_access_absent,
       NOT EXISTS (SELECT FROM pg_catalog.pg_class WHERE relnamespace = n.oid) AS relations_absent,
       NOT EXISTS (SELECT FROM pg_catalog.pg_proc WHERE pronamespace = n.oid) AS functions_absent,
       NOT EXISTS (SELECT FROM pg_catalog.pg_type WHERE typnamespace = n.oid) AS types_absent
FROM pg_catalog.pg_namespace n
JOIN pg_catalog.pg_roles r ON r.oid = n.nspowner
WHERE n.nspname = 'dxos_wf25_smoke_01';
```

Sau khi postchecks và snapshot ngoài schema không drift ngoài tác động đã duyệt:

```sql
COMMIT;
```

Xác nhận durable commit bằng connection READ ONLY mới: schema PRESENT, owner
postgres, private ACL, không API access, inventory/history rỗng, hai custom roles
vẫn NOLOGIN và snapshot ngoài schema không đổi. Development check phải trả EMPTY;
chỉ mode check, không bật WRITE_APPROVAL, migrate hoặc smoke. Flyway V3/V4 chưa được
phê duyệt bởi bootstrap approval; sau bước này mới chuẩn bị approval migration.

### Khi thất bại

- Lỗi trước COMMIT: ROLLBACK, close; xác nhận schema ABSENT qua connection chỉ đọc mới.
- Timeout/disconnect hoặc mất phản hồi COMMIT: không giả định rollback, không retry;
  đọc lại owner/ACL/inventory và đối chiếu snapshot để phân biệt absent/committed/drift.
- Nếu đã commit nhưng postcheck mới lỗi: dừng, giữ schema, xin recovery approval riêng;
  không tự DROP/REVOKE/GRANT, clean/repair/baseline hoặc CASCADE.
- Rủi ro còn lại: admin có quyền rộng, privileged concurrent DDL/TOCTOU, trigger
  side effects chưa quan sát khi chạy DDL cloud; API exposure phải xác nhận ở Dashboard.

Launcher một mục đích: scripts/workflow-schema-bootstrap.ps1 (explicit invocation,
prompt password ẩn hoặc DB_PASSWORD từ secret environment), Java task
workflowSchemaBootstrap. Endpoint khóa cứng đúng project owner đã chỉ định;
prechecks fail thì dừng, không sửa cấu hình. Cùng connection giữ precheck read-only,
rollback read transaction rồi BEGIN READ WRITE theo approval. Snapshot so sánh
namespace ngoài target, roles/memberships/default ACL/database ACL, structural
relations/attributes/types/routines/constraints/triggers và dependencies; chỉ loại
row schema/ownership dependency của target được phép tạo. Hash được tính phía DB,
không xuất bodies, credentials hoặc raw catalog ra báo cáo.

LISTEN pgrst chỉ đăng ký nhận notification cho session (không persistent write),
để ghi nhận NOTIFY reload schema từ đúng backend PID. Không gửi NOTIFY chủ động;
subscription tự mất khi close connection. Mất COMMIT response luôn COMMIT_UNKNOWN,
chỉ reconcile bằng connection mới, không retry CREATE hoặc DROP. Postcommit audit
FAIL khiến exit 1, kể cả khi COMMIT đã được ACK và status transaction là COMMITTED_PASS.

Owner đã thực thi launcher thành công theo report bên trên. Agent không chạy
bootstrap; không gửi password vào chat và không thực thi lại launcher.

Kiểm chứng launcher/bootstrap trên PostgreSQL 17.11/TLS disposable:
- Full JDK 21 test/build cuối: 713 PASS / 0 FAIL / 0 SKIP, 8 integration suites,
  BUILD SUCCESSFUL. Bootstrap integration 13 PASS gồm exact one-schema DDL,
  new-connection audit, actual CA/hostname failure, existing schema/custom-role
  LOGIN/manifest mismatch dừng, postcheck ACL/objects/catalog failure rollback,
  own-session pgrst notification, lost COMMIT trước/sau apply không retry/drop,
  và fresh audit failure không trả exit 0.
- Lượt full trước có 2 failures audit fixture vì fixture bootstrap để lại roles;
  đã sửa cleanup chỉ dọn roles fixture tạo. Không sửa tests/logic audit cũ.
- Sau full run, bổ sung fallback launcher: child Java không trả report sau handoff
  thì COMMIT_UNKNOWN, không claim NOT_EXECUTED. Chạy riêng 20 bootstrap unit/launcher
  tests PASS / 0 FAIL / 0 SKIP, dùng fake Gradle cho lỗi handoff; không DB/cloud.
- Evidence local này tách biệt với COMMITTED_PASS trên Supabase do owner báo sau đó.

## Thực thi Flyway V3/V4 — owner đã phê duyệt

Ngày hoàn thiện kế hoạch: 2026-10-15.

FLYWAY_PLAN_READY=YES. MIGRATION_BUNDLE_VERIFIED=YES (đối chiếu local).
OWNER_APPROVAL=GRANTED_FOR_PINNED_V3_V4_ONLY. Không xin lại approval cho phạm vi này.
Chưa chạy Flyway hoặc cloud write mới từ Codex vì DB_PASSWORD/PGSSLROOTCERT không có.
NEXT_APPROVAL_REQUIRED=YES dành cho cloud smoke/recovery, không phải hỏi lại V3/V4.
Bootstrap đã hoàn thành, không CREATE SCHEMA lại. Ownership/rỗng/history absent
hiện dựa trên postcommit audit owner cung cấp; phải xác minh mới trước migration.

### Bundle, kết nối và cấu hình đã kiểm tra

| Script | SHA-256 UTF-8, CRLF → LF | Flyway CRC32 |
| --- | --- | ---: |
| V3__create_workflow_persistence.sql | 15d615162c09b0c843680cf0c3b9fe48b16cd637877eb91215d8698cd7bc4832 | -844876185 |
| V4__seed_workflow_definitions.sql | 40ee5a6c3a357d7ebca6d198c9981760868fc872121e8caece6ae6e86634a3ef | 1349884712 |

Source và backend/build/workflow-smoke-bundle đều khớp manifest; bundle chỉ có
hai file trên, không symlink/sidecar/config/callback phụ. Manifest raw SHA-256
D5DE8C9DEB62590653F2FB19D184C8AD9298D3A5386C98CF2B4B50EEFC4A3237:
schema dxos_wf25_smoke_01, PostgreSQL 17.11, 6 event triggers/8 functions/2 helpers.
CRC32 tính lại theo source ChecksumCalculator/SqlMigrationResolver của dependency
Flyway 11.7.2 đã cache, khác mục đích với SHA-256 pin.

Endpoint duy nhất: db.zsfctpnitkdztlfhzqgw.supabase.co:5432, database postgres,
session_user=current_user=postgres. Không pooler/fallback/credential-bearing URL.
JDK 21; CA từ PGSSLROOTCERT, password từ DB_PASSWORD trong secret environment;
không CLI password/log exception/ghi secret vào file. JDBC sslmode=verify-full,
sslrootcert=CA tin cậy, pg_stat_ssl.ssl=true, connectTimeout=10/socketTimeout=20.
Không đổi CA hoặc hạ SSL để vượt lỗi.

Chỉ entrypoint development workflowDevelopmentSmoke với mode migrate, explicit
argument --development-integration, opt-in issue-25-postgres-development-only,
approved schema dxos_wf25_smoke_01, project ref zsfctpnitkdztlfhzqgw.
Launcher chỉ đặt explicit approval marker cho lần chạy đã duyệt. Không boot Spring, gọi
production smoke bằng admin, hoặc đặt disposable flags. Production guards giữ nguyên.

WorkflowMigrations.configure + WorkflowSmokeRunner.flyway khóa defaultSchema/schemas
chỉ target, createSchemas=false, table=flyway_schema_history, target=4,
executeInTransaction=true, cleanDisabled=true, baselineOnMigrate=false,
outOfOrder=false, UTF-8/native defaultSchema placeholder, search_path chỉ
target,pg_catalog,pg_temp. Một filesystem location chứa đúng V3/V4; không V1/V2,
Java migrations/custom resolver/callback/init SQL. Guard kiểm tra configuration,
bundle và catalog khi mở connection, trước validate/migrate/từng migration.
Flyway mặc định group=false: V3/V4 có transaction riêng, không atomic cả bundle.
History table có thể đã commit riêng khi migration lỗi. Không dùng transaction
bootstrap để bọc runner hoặc giả định postcheck toàn bundle trước COMMIT.
Xem [Flyway transaction handling](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html).

### SQL và expected objects cần phê duyệt

SQL nghiệp vụ chính xác là toàn bộ hai file pin:
[V3](../../backend/src/main/resources/db/migration/V3__create_workflow_persistence.sql)
và [V4](../../backend/src/main/resources/db/migration/V4__seed_workflow_definitions.sql),
native defaultSchema được thay bằng dxos_wf25_smoke_01. Không chạy bằng psql cloud
hoặc sửa/re-pin SQL để vượt precheck.

DDL history của Flyway 11.7.2 đã đối chiếu source dependency, không baseline:

~~~sql
CREATE TABLE "dxos_wf25_smoke_01"."flyway_schema_history" (
    "installed_rank" INT NOT NULL, "version" VARCHAR(50),
    "description" VARCHAR(200) NOT NULL, "type" VARCHAR(20) NOT NULL,
    "script" VARCHAR(1000) NOT NULL, "checksum" INTEGER,
    "installed_by" VARCHAR(100) NOT NULL,
    "installed_on" TIMESTAMP NOT NULL DEFAULT now(),
    "execution_time" INTEGER NOT NULL, "success" BOOLEAN NOT NULL
);
ALTER TABLE "dxos_wf25_smoke_01"."flyway_schema_history"
    ADD CONSTRAINT "flyway_schema_history_pk" PRIMARY KEY ("installed_rank");
CREATE INDEX "flyway_schema_history_s_idx"
    ON "dxos_wf25_smoke_01"."flyway_schema_history" ("success");
~~~

Flyway còn dùng metadata reads, session/transaction control, locks và INSERT hai
history rows; timestamp/execution_time xác định lúc chạy. Không tạo schema mới.

| Nhóm | Inventory trong dxos_wf25_smoke_01 |
| --- | --- |
| Tables (6) | workflow_definitions, workflow_definition_states, workflow_definition_transitions, workflow_instances, workflow_transition_history, flyway_schema_history |
| Sequences | 0; BIGINT, txid_current() và installed_rank không tạo sequence |
| Indexes (8, B-tree) | workflow_definitions_pkey (definition_id,definition_version); workflow_definition_states_pkey (definition_id,definition_version,state_id); workflow_definition_transitions_pkey (definition_id,definition_version,transition_id); workflow_instances_pkey (instance_id); workflow_transition_history_pkey (instance_id,resulting_runtime_version); workflow_instances_resource_idx (resource_type,resource_id); flyway_schema_history_pk (installed_rank); flyway_schema_history_s_idx (success). Sáu PK indexes unique, hai còn lại non-unique |
| Functions (2) | reject_workflow_definition_change(), require_workflow_definition_provisioning(); RETURNS trigger, plpgsql, SECURITY INVOKER, search_path=pg_catalog,pg_temp |
| Explicit triggers (9) | workflow_definitions_immutable, workflow_definition_states_immutable, workflow_definition_transitions_immutable: BEFORE UPDATE OR DELETE, FOR EACH ROW, reject function; workflow_definitions_no_truncate, workflow_definition_states_no_truncate, workflow_definition_transitions_no_truncate: BEFORE TRUNCATE, FOR EACH STATEMENT, reject function; workflow_definitions_provisioning, workflow_definition_states_sealed, workflow_definition_transitions_sealed: BEFORE INSERT, FOR EACH ROW, provisioning function |
| Constraints | 34 pg_constraint rows: 6 PK + 6 FK + 22 CHECK; 40 NOT NULL attributes (32 Workflow + 8 history), không UNIQUE riêng |
| Nội bộ PostgreSQL | 24 RI triggers cho 6 FK (RI_ConstraintTrigger_* chứa OID động), 6 composite row types + 6 array types; TOAST relations/indexes và pg_catalog dependency/ownership rows do PostgreSQL tự tạo |

Tên constraint và nội dung theo PostgreSQL 17.11 local:

| Table | PK | FK | CHECK |
| --- | --- | --- | --- |
| workflow_definitions | workflow_definitions_pkey | workflow_initial_state_membership: (definition_id,definition_version,initial_state) → states; DEFERRABLE INITIALLY DEFERRED, mặc định NO ACTION | workflow_definitions_definition_id_check (trim nonempty); workflow_definitions_definition_version_check (>=0); workflow_definitions_initial_state_check (trim nonempty) |
| workflow_definition_states | workflow_definition_states_pkey | workflow_definition_states_definition_id_definition_versio_fkey: (definition_id,definition_version) → definitions, DELETE RESTRICT | workflow_definition_states_state_id_check (trim nonempty) |
| workflow_definition_transitions | workflow_definition_transitions_pkey | workflow_definition_transitio_definition_id_definition_ver_fkey: source_state → states; workflow_definition_transiti_definition_id_definition_ver_fkey1: target_state → states; cùng definition_id/version, DELETE RESTRICT | workflow_definition_transitions_transition_id_check (trim nonempty) |
| workflow_instances | workflow_instances_pkey | workflow_instances_definition_id_definition_version_curren_fkey: (definition_id,definition_version,current_state) → states, DELETE RESTRICT | workflow_instances_instance_id_check, workflow_instances_resource_type_check, workflow_instances_resource_id_check (trim nonempty); workflow_instances_runtime_version_check (>=0); workflow_instances_lifecycle_check (NOT_STARTED/ACTIVE/COMPLETED); workflow_instances_created_at_seconds_check, workflow_instances_updated_at_seconds_check (-31557014167219200..31556889864403199); workflow_instances_created_at_nanos_check, workflow_instances_updated_at_nanos_check (0..999999999); workflow_instances_check (updated Instant >= created Instant) |
| workflow_transition_history | workflow_transition_history_pkey | workflow_transition_history_instance_id_fkey: instance_id → instances, DELETE RESTRICT | workflow_transition_history_resulting_runtime_version_check (>0); workflow_transition_history_transition_id_check, workflow_transition_history_source_state_check, workflow_transition_history_target_state_check, workflow_transition_history_actor_id_check (trim nonempty); workflow_transition_history_occurred_at_seconds_check (range như trên); workflow_transition_history_occurred_at_nanos_check (0..999999999) |
| flyway_schema_history | flyway_schema_history_pk | Không | Không |

V3 REVOKE ALL cho PUBLIC trên 5 Workflow tables/2 functions và DO block REVOKE ALL
cho anon/authenticated/service_role nếu tồn tại, chỉ trên objects mới của target.
Đây là object ACL changes cần approval migration, không ALTER ROLE/membership,
GRANT hoặc đổi quyền hai custom roles/schema ACL/default privileges/shared objects.

Seed V4 chính xác, definition_version đều 1:

| Definition / initial_state | States | Transition ID: source → target |
| --- | --- | --- |
| document-review / DRAFT | DRAFT, IN_REVIEW, APPROVED, REJECTED | submit: DRAFT → IN_REVIEW; approve: IN_REVIEW → APPROVED; reject: IN_REVIEW → REJECTED |
| incident-resolution / OPEN | OPEN, IN_PROGRESS, RESOLVED | start: OPEN → IN_PROGRESS; resolve: IN_PROGRESS → RESOLVED |

Hai provisioning_transaction được trigger gán cùng transaction ID của V4, không
pin trước giá trị. Tổng 2/7/5 rows; workflow_instances/transition history đều 0.
Flyway history đúng versions 3/4, scripts/checksums ở bảng pin, type SQL,
installed_by postgres, success=true, installed_rank 1/2; không V1/V2/baseline/schema marker.

### Precheck mới, chỉ đọc; bất kỳ FAIL thì dừng

1. Exact endpoint/project/port/database/user/CA/JDK 21; không pooler/bypass/URL
   override. Preflight/live guard cùng Direct PASS, client VERIFIED_VERIFY_FULL,
   backend SSL=true, PostgreSQL 17.11 (170011), primary, fsync/full_page_writes/
   synchronous_commit=on. Backend SSL riêng không chứng minh CA/hostname.
2. Connection mới BEGIN READ ONLY, REPEATABLE READ: schema PRESENT đúng một lần,
   owner postgres, USAGE/CREATE target và plpgsql USAGE; default_transaction_read_only=off
   nhưng transaction_read_only=on. Schema ACL chỉ owner, PUBLIC/API/custom roles
   không USAGE/CREATE ngoài trạng thái đã duyệt. Dashboard API exposed schemas
   không chứa target; không tự ALTER/GRANT/REVOKE để ép PASS.
3. Rỗng hoàn toàn: pg_class/pg_proc/pg_type và pg_operator/pg_collation/pg_conversion/
   pg_opclass/pg_opfamily/pg_ts_* của target zero; history ABSENT. Không custom
   default ACL của postgres global/target. Hai custom roles vẫn NOLOGIN,
   flags/ACL/membership không đổi so với bootstrap.
4. Guard fresh khớp 6/8/2 inventory, version/SHA-256; source/bundle khớp pins,
   không sidecar. Development check trả DEVELOPMENT_PREFLIGHT_EMPTY và chỉ hai
   PENDING scripts; không đặt WORKFLOW_DEV_WRITE_APPROVAL trong check.
5. Lưu schema OID/owner/ACL, fingerprint ngoài target, roles/memberships/default ACL/
   database ACL, existing public/auth/storage/extensions objects và event graph;
   không lưu secrets/function bodies vào report. Dừng concurrent privileged DDL,
   drift cần owner review. ROLLBACK/close read transaction; không DDL trong precheck.

### Thực thi đề xuất sau approval và postcheck

Approval chỉ gồm V3/V4 pin, history metadata writes/seed, V3 object ACL revocations,
internal catalog/TOAST/RI objects và reviewed pgrst reload notification nếu có.
Không gồm bootstrap/smoke/provisioning/recovery. Sau approval mới đặt write marker,
gọi migrate đúng một lần và clear marker trong finally; không gọi smoke cùng batch.
Giữ bundle bất biến trong cửa sổ chạy. Runner/connection lỗi thì dừng.

Sau kết thúc, connection mới verify-full, BEGIN READ ONLY để audit:

- Schema OID/owner/ACL như bootstrap, manifest/bundle vẫn khớp.
- Exact names/definitions như inventory, không chỉ counts: 6 tables/8 indexes/
  2 functions/9 explicit triggers/34 PK-FK-CHECK/0 sequences/24 internal RI triggers;
  owner postgres, validated constraints/deferrability, NOT NULL/defaults, trigger
  enabled/orientation/events, function invoker/search_path.
- History đúng 2 success rows/checksums, seed exact 2/7/5 tuples, cùng V4 txid,
  0 instances/transition history. Không thử write để kiểm chứng triggers.
- Table/column/function ACL không PUBLIC/API grants hoặc inherited effective access;
  history private, schema không API exposure; custom roles/flags/memberships/default
  ACL/database ACL không đổi, NOLOGIN.
- Existing public/auth/storage/extensions objects không đổi. Allowlist chỉ catalog/
  dependency/TOAST internals gắn với objects mới, Flyway session locks và reviewed
  NOTIFY pgrst; không tùy ý thay pg_catalog. Structural snapshot không chứng minh
  toàn bộ dữ liệu ngoài target bất biến; không migration DML tham chiếu dữ liệu đó.
- ROLLBACK/close audit, clear write marker. MIGRATION_PASS của runner chưa thay cho
  full fresh postcheck owner; PASS chỉ sau audit này.

### Lỗi, COMMIT_OUTCOME_UNKNOWN và rủi ro

| Kết quả audit fresh | Xử lý bắt buộc |
| --- | --- |
| History absent/empty, không Workflow objects | Chưa xác nhận migration; history table có thể tồn tại riêng. Dừng, giữ schema; không bootstrap/retry tự động |
| Chỉ V3 success, inventory V3 đủ, seed 0 | V3 đã commit; transaction V4 lỗi rollback seed. Dừng, recovery approval riêng trước rerun |
| V3/V4 success, inventory/checksums/seeds chính xác | Ghi AUDIT_CONFIRMED_APPLIED. Nếu ACK mất vẫn giữ COMMIT_OUTCOME_UNKNOWN ban đầu; không retry |
| History/catalog/seed lệch, failed rows hoặc audit không kết nối | FAIL hoặc unknown chưa resolve; giữ nguyên, owner review |

Flyway rollback migration đang lỗi khi DB xác nhận transaction; không rollback
V3 đã commit hoặc tự DROP history/objects. Timeout/disconnect/mất ACK/generic failure
sau handoff có thể COMMIT_OUTCOME_UNKNOWN; exit khác 0 không chứng minh chưa ghi.
Chỉ reconnect read-only; không retry/clean/repair/baseline/undo/DROP/REVOKE bổ sung
hoặc sửa pins. Recovery SQL phải owner duyệt riêng; postcheck fail sau commit
không còn rollback cả bundle.

RISK_ASSESSMENT=SCOPED_ADMIN_DEVELOPMENT_WITH_PARTIAL_COMMIT_RISK.
Qualification/guards không sandbox postgres hoặc loại bỏ concurrent privileged DDL/
event-trigger side effects. Reviewed trigger có thể phát NOTIFY pgrst/reload schema;
không chủ động NOTIFY/đổi API config. Không migration SQL chỉ định DDL/DML vào
public/auth/storage/schema application khác; pg_catalog/pg_toast internals là tác động
PostgreSQL bắt buộc. Đây chưa là runtime identity production least privilege.

Kiểm chứng lượt lập kế hoạch: source/bundle SHA-256, manifest raw hash/inventory,
Flyway CRC32 và configuration review PASS. V3/V4 + DDL history chạy trên PostgreSQL
17.11 disposable local không network/port/cloud credentials: inventory/constraints/
seed 2/7/5 và 0 instances/history PASS, fixture đã dọn. Query inventory đầu lỗi cast
kiểu "char", đã sửa query local và chạy PASS. Đây là SQL/catalog verification,
không Flyway cloud/TLS cloud/full test-build mới. Full 713 PASS/0 FAIL/0 SKIP và
build PASS ở phần bootstrap là evidence trước đó.

Nguồn: [CREATE SCHEMA](https://www.postgresql.org/docs/17/sql-createschema.html),
[schema default privileges](https://www.postgresql.org/docs/17/ddl-priv.html),
[pgJDBC verify-full](https://jdbc.postgresql.org/documentation/ssl/).

## Optional hardening / evidence lịch sử

Stage 1A đã được người dùng báo COMMITTED_AUDIT_PASS; không chạy lại. Các credential
logging/provider proposals, Stage 1B-1/1B-2, runtime ACL và SECURITY DEFINER reviews
được giữ để truy vết hoặc làm hardening sau này. Ticket credential/provider không
còn là bước tiếp theo bắt buộc của Issue #25. Không gửi ticket, đổi logging,
roles, credentials hoặc privileges trong lượt này.

Event-trigger guard/manifest vẫn là dependency thật của WorkflowSchema và
WorkflowMigrations; không được loại cùng với tài liệu provisioning. Audit helpers,
SQL và launcher fixtures cần giữ khi giữ nguyên toàn bộ integration tests.

## Readiness hiện tại

- LOCAL_CODE_TEST_READINESS=YES: JDK 21 full tests/build, PR snapshot 769 PASS và WIP 772 PASS / 0 FAIL / 0 SKIP
  trên PostgreSQL 17.11 disposable; không phải bằng chứng cloud.
- CUSTOM_ROLE_PROVISIONING=DEFERRED_OPTIONAL; roles hiện có giữ NOLOGIN.
- CLOUD_CONNECTION_VERIFICATION=OWNER_REPORTED_PASS_DIRECT; exact project/endpoint
  cho cả hai lần chạy chưa được agent đối chiếu.
- OPERATOR_CLOUD_SMOKE_SUPPORT=OWNER_REPORTED_COMMITTED_PASS; run cloud-20261015-01, 2/4 rows, fresh-JVM read PASS.
- CLOUD_WRITE_APPROVAL=COMPLETED_SCOPE_ONLY; không cho phép rerun hoặc recovery mới.
- SCHEMA_BOOTSTRAP_EXECUTION=OWNER_REPORTED_COMMITTED_PASS; POSTCOMMIT_AUDIT=PASS.
- FLYWAY_EXECUTION=OWNER_REPORTED_COMMITTED_PASS; checksum/inventory/seed/postcommit PASS.
- PR_READINESS: xem báo cáo chuẩn bị PR mới nhất cuối tài liệu; không còn blocker thiếu cloud evidence.

## Checklist trước commit/PR

- [x] WIP tập trung một worktree, main không có commit mới cần hợp nhất; WIP worktree khác giữ nguyên.
- [x] Isolation 5 ca, TLS/CA/hostname và toàn suite chạy thật; không còn FAIL/SKIP local.
- [x] V3/V4/manifest/audit hash khớp; V1/V2/Core port không đổi.
- [x] Source route local, fixture tự sinh credentials, smoke/restart và build được xác minh.
- [x] Cloud Direct Connection và scoped migration/smoke/fresh-JVM read có owner acceptance.
- [ ] Ánh/Yến review precision, adapter boundary và security/deployment evidence.
- [ ] User cho phép commit/push/tạo PR. Không đánh dấu DoD merge trước khi review/merge commit thực tế.

Phân loại phạm vi PR nằm dưới đây; không stage bất kỳ file nào trong phiên này.


## Phân loại file cho PR cũ (57 file; thay bằng allowlist cuối tài liệu)

Đây là đề xuất phạm vi PR; không xóa/unstage file. Toàn bộ WIP giữ trên đĩa.
Giữ JDBC adapter, V3/V4, mọi integration tests, TLS verify-full và secret config.

### Cần giữ (30)

Adapter/production wiring, schema/migrations, tests, local/build config và runbook hiện tại.

- backend/build.gradle
- backend/README.md
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistence.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerGuard.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowMigrations.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowPersistenceConfiguration.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowReadOnlyPreflight.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchema.java
- backend/src/main/resources/db/migration/V3__create_workflow_persistence.sql
- backend/src/main/resources/db/migration/V4__seed_workflow_definitions.sql
- backend/src/main/resources/workflow-event-trigger-manifest.json
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/PostgresWorkflowPersistenceIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerGuardIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerReviewIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowIsolationIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowLiveGuardTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowReadOnlyPreflightTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAuditIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAuditLauncherTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAuditTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSchemaTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReviewIntegrationTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReviewLauncherTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReviewTest.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowSmokeRunnerEndpointTest.java
- docs/evidence/issue-25-readiness.md
- infra/compose.workflow-postgres.yml
- scripts/README.md
- scripts/workflow-read-only-preflight.ps1
- scripts/workflow-verify-local.ps1

### Optional trong luồng chạy, vẫn nên giữ trong PR (12)

Không là prerequisite của Direct Connection. Giữ helpers/SQL/launchers vì guard,
integration và launcher tests đang dùng chúng. Event-trigger review SQL được
integration đọc; audit helpers/SQL và hai launcher được TLS/launcher tests dùng;
SmokeRunner được isolation tests dùng. Không loại các dependency này riêng lẻ.

- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowEventTriggerReview.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowLiveGuard.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowRuntimePrivilegeAudit.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSecurityDefinerReview.java
- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowSmokeRunner.java
- docs/evidence/issue-25-event-trigger-catalog-review.sql
- docs/evidence/issue-25-runtime-privilege-review.sql
- docs/evidence/issue-25-security-definer-review.sql
- scripts/workflow-event-trigger-review.ps1
- scripts/workflow-live-guard.ps1
- scripts/workflow-runtime-privilege-audit.ps1
- scripts/workflow-security-definer-review.ps1

### Có thể loại khỏi PR ban đầu (15), giữ làm WIP/evidence optional

Gói Stage 1A đã hoàn tất, không chạy lại. Stage 1B/custom-role credentials/schema
ACL được hoãn theo Issue mới. Proposal SQL tổng hợp chứa CREATE ROLE/LOGIN/GRANT
cũ, không dùng làm runbook mới. Research/Track B/PR draft là hồ sơ lịch sử,
đã có readiness và backend README hiện tại.

- backend/src/main/java/com/tricore/dxos/infrastructure/workflow/WorkflowStage1AProvisioner.java
- backend/src/test/java/com/tricore/dxos/infrastructure/workflow/WorkflowStage1AProvisionerTest.java
- docs/evidence/issue-25-pr-draft.md
- docs/evidence/issue-25-stage-1a-nologin.sql
- docs/evidence/issue-25-stage-1a-postcommit-audit.sql
- docs/evidence/issue-25-stage-1b-1-login-proposal.md
- docs/evidence/issue-25-stage-1b-1-log-safe-proposal.md
- docs/evidence/issue-25-stage-1b-1-readonly-audit.sql
- docs/evidence/issue-25-stage-1b-2-schema-proposal.md
- docs/evidence/issue-25-supabase-approval-proposal.sql
- docs/evidence/issue-25-supabase-write-readiness-research.md
- docs/evidence/issue-25-track-b-acceptance.md
- scripts/workflow-provisioning-local-test.ps1
- scripts/workflow-stage-1a.ps1
- scripts/workflow-stage-1b-1-readonly-audit.ps1

Nếu loại gói Stage 1A sau này, phải loại Java provisioner, unit test, launcher,
hai SQL Stage 1A, local provisioning launcher và task workflowStage1A trong
backend/build.gradle cùng lúc; không chạm integration tests. Trong lượt này
không loại bất kỳ file/task/test nào, không sửa SQL pins hoặc migration.

## Launcher thực thi V3/V4 theo approval ngày 2026-10-15

Đã thêm WorkflowApprovedMigration + task workflowApprovedMigration và
scripts/workflow-approved-migrations.ps1. Không gọi bootstrap hoặc smoke.
Tái dùng beginReadOnly/session/audit của bootstrap qua package visibility;
không thay state machine/SQL bootstrap. Production smoke, adapter, V3/V4,
manifest và tests cũ giữ nguyên.

Launcher khóa host/port/database/user, opt-in explicit, WARP Connected/IPv6 DNS,
JDK21 và CA; prompt password ẩn hoặc environment. Không chứa credentials trong
URL/args/report. Java pin cả raw manifest hash và bundle V3/V4; chỉ hai PENDING
scripts/CRC đã review. Flyway group=false, connectRetries/lockRetryCount=0,
createSchemas=false; cấm baseline/repair/clean giữ nguyên.
Precheck yêu cầu schema PRESENT/rỗng/owner postgres/ACL private, history ABSENT,
NOLOGIN roles, pg17.11/SSL/durability; mỗi writer connection có read-only recheck
và outside-catalog fingerprint trước khi bàn giao Flyway.

Postcheck dùng connection mới READ ONLY và snapshot so sánh existing outside catalog,
roles/default ACL/membership/database ACL; chỉ loại new target/dependency/TOAST objects
được phép khỏi delta. Inventory fingerprint chuẩn hóa không chứa OID động, gồm
relations/columns/defaults/NOT NULL/constraints/index definitions/functions/ACL,
9 explicit + 24 internal RI triggers, 12 types và không special objects/rules lạ.
Golden hashes từ V3/V4 pin trên PostgreSQL 17.11; seed exact tuples 2/7/5,
0 instances/transition history. Column ACL non-owner cũng bị chặn.
Schema OID/owner/ACL phải giữ nguyên. Không export raw bodies/roles/secrets.

Không nhận ACK hoặc lỗi transport/timeout/rollback không rõ: COMMIT_UNKNOWN,
giữ trạng thái này dù fresh audit PASS; không retry. V3/V4 status, history và seed
được đọc riêng vì mất ACK có thể để seed/history chưa đồng bộ. Known SQL failure
chỉ báo FAILED_ROLLED_BACK sau read-only audit xác nhận không Workflow objects/seed;
history metadata có thể tồn tại riêng. PARTIAL_COMMIT luôn dừng, không DROP/repair.
Nếu audit không chứng minh outside state, report UNVERIFIED thay vì claim NONE.
Đây là fingerprint metadata, không là sandbox admin hoặc bằng chứng toàn bộ outside
data; reviewed event-trigger pgrst reload vẫn là side effect dự kiến.

38 tests mới (22 unit/launcher + 16 PostgreSQL TLS integration) PASS/0 FAIL/0 SKIP:
happy path, wrong endpoint/flags/approval/TLS, nonempty/ACL/role/bundle precheck,
V3 commit/V4 SQL failure, lost V4 COMMIT trước/sau apply, outside/column ACL/
inventory/seed drift, independent audit failure, report filtering/child mất report.
Lượt thử đầu sửa lỗi parser Windows PowerShell; fault injection sau đó được giới hạn
đúng connection thực thi seed thay vì ảnh hưởng connection metadata. Không sửa SQL
hoặc pin migrations để làm tests PASS. Full JDK21 regression/build cuối: 752 PASS / 0 FAIL / 0 SKIP, đủ 9 integration
suites, BUILD SUCCESSFUL (3m30s), PostgreSQL 17.11 TLS disposable. Sau full đầu
752 PASS, cố định JSON row ordering bằng COLLATE pg_catalog."C" để không phụ thuộc
collation local/cloud và chạy lại full: vẫn 752 PASS/0 FAIL/0 SKIP. Không đổi
migration SQL hoặc các SHA/CRC đã review. Fixture/certificates đã dọn.
PowerShell parser và launcher regression PASS. WARP Connected/Direct IPv6 DNS có
trong môi trường máy, nhưng DB_PASSWORD/PGSSLROOTCERT không có ở Codex process/
user/machine; không mở Supabase JDBC connection và không claim cloud TLS PASS.
FLYWAY_EXECUTION=NOT_EXECUTED; POSTCOMMIT_AUDIT=NOT_RUN; OTHER_CLOUD_WRITES=NONE.
Owner chạy đúng một lệnh launcher trong backend/README.md rồi trả report,
không gửi password/chat. Không hỏi lại approval V3/V4; dừng trước cloud smoke.
WIP kiểm tra bằng SHA-256: chỉ docs/config/local verifier và ba helper visibility
của bootstrap đổi trong files cũ; thêm một Java entrypoint, hai tests và launcher.
Không file cũ bị xóa; V3/V4/manifest/adapter/tests cũ và 15 optional-exclude files
giữ nguyên; HEAD không đổi, không stage/commit/push/PR/merge.

## Cloud smoke sau migrations — owner đã nghiệm thu (2026-10-15)

Owner đã báo COMMITTED_PASS/precheck/postcheck/fresh-JVM PASS, 2/4 rows, exit 0.
Kế hoạch và các lệnh dưới đây được giữ làm lịch sử/recovery reference; không chạy
lại smoke, bootstrap hoặc Flyway. Evidence nghiệm thu chính xác ở cuối tài liệu.

Trạng thái mới nhất thay thế các report NOT_EXECUTED lịch sử phía trên.
Owner xác nhận (không phải lượt cloud do Codex tự chạy):

```text
FLYWAY_EXECUTION=COMMITTED_PASS
DIRECT_CONNECTION=PASS
TLS_VERIFIED=YES
V3_STATUS=SUCCESS_PIN_MATCH
V4_STATUS=SUCCESS_PIN_MATCH
FLYWAY_HISTORY=V3_V4_SUCCESS_CHECKSUM_MATCH
INVENTORY_CHECK=PASS
SEED_CHECK=PASS
POSTCOMMIT_AUDIT=PASS
MIGRATIONS_EXIT=0
```

Bootstrap và cloud migrations đã hoàn tất. Không chạy lại hai launcher đó,
CREATE SCHEMA, Flyway migrate/info/repair/clean/baseline hoặc bootRun.
Development check/smoke/read/audit nay đọc trực tiếp history bằng SELECT;
mode migrate lịch sử vẫn tồn tại nhưng launcher smoke không gọi hoặc cấp approval
cho nó. Không đổi migration SQL, pins, custom roles, ACL hoặc production wiring.

### Phạm vi ghi chính xác

Chỉ Direct `db.zsfctpnitkdztlfhzqgw.supabase.co:5432/postgres`, login `postgres`,
schema `dxos_wf25_smoke_01`, run-id **cloud-20261015-01**.
Precheck yêu cầu hai bảng runtime hoàn toàn rỗng; nếu đã có dữ liệu thì dừng,
không đổi run-id để chạy vòng qua việc đối chiếu. Dữ liệu tổng hợp được giữ lại.

| Instance INSERT | Definition/version | Resource type/id | Chuỗi snapshot |
| --- | --- | --- | --- |
| `issue25-cloud-20261015-01-review` | `document-review` / 1 | `issue25-smoke` / chính instance id | DRAFT/NOT_STARTED/v0 → DRAFT/ACTIVE/v0 → IN_REVIEW/ACTIVE/v1 → APPROVED/COMPLETED/v2 |
| `issue25-cloud-20261015-01-incident` | `incident-resolution` / 1 | `issue25-smoke` / chính instance id | OPEN/NOT_STARTED/v0 → OPEN/ACTIVE/v0 → IN_PROGRESS/ACTIVE/v1 → RESOLVED/COMPLETED/v2 |

Mọi created_at/updated_at và history occurred_at dùng Instant cố định
`2026-10-13T01:02:03.123456789Z` (seconds **1791853323**, nanos **123456789**),
không phải thời gian owner chạy. Actor mọi transition là
`issue25-smoke-cloud-20261015-01`.

| History INSERT (instance suffix) | Transition | Source → target | resulting_runtime_version |
| --- | --- | --- | --- |
| review | submit | DRAFT → IN_REVIEW | 1 |
| review | approve | IN_REVIEW → APPROVED | 2 |
| incident | start | OPEN → IN_PROGRESS | 1 |
| incident | resolve | IN_PROGRESS → RESOLVED | 2 |

Thành công: **2 INSERT workflow_instances + 6 UPDATE trên hai hàng đó +
4 INSERT workflow_transition_history**, tổng **8 write transactions commit**.
UPDATE chỉ current_state/runtime_version/lifecycle/updated_at_seconds/updated_at_nanos.
Activation không tăng version và không thêm history. Race activation review và
race submit mỗi race có đúng 1 winner, 1 VERSION_CONFLICT rollback trước UPDATE;
không có history trùng. Bốn transition commit UPDATE+history atomically từng cặp.
Không có một transaction bao toàn bộ smoke: lỗi có thể giữ 0–2 instances,
0–4 history và snapshot trung gian. Precheck/postcheck/read không ghi bảng.

Không INSERT/UPDATE definitions/states/transitions hoặc Flyway history: vẫn
2 definitions / 7 states / 5 transitions / 2 migration history rows.
Không DELETE/TRUNCATE/DDL/GRANT/ALTER ROLE/NOTIFY. Chín explicit triggers V3
nằm trên definition tables, không được kích hoạt bởi runtime DML; FK/internal
constraint triggers và indexes của hai bảng runtime vẫn thực thi. Event triggers
DDL không chạy do smoke không phát DDL; không có pgrst reload NOTIFY chủ động.
WAL/transaction IDs, row/index storage, locks, statistics, server audit/logging,
provider backup/replication là tác động vận hành dự kiến. Hai race dùng thêm
connection đồng thời; transaction sync commit, timeout có thể làm kết quả không rõ.

### Precheck, thực thi và postcheck dự kiến

1. Owner phê duyệt riêng run-id/delta này rồi chạy launcher trong terminal riêng.
   Không đặt password trên CLI/chat/file/transcript. Launcher kiểm tra switch,
   endpoint cố định, cấm mọi disposable flag kể cả false, CA file, JDK21,
   WARP Connected/IPv6 DNS trước prompt `Read-Host -AsSecureString`.
   Mật khẩu chỉ chuyển sang process environment cho JDBC/Gradle child;
   `finally` dispose SecureString và khôi phục môi trường. Không ghi secret vào
   URL, argument, report hoặc credential file. Child output chỉ xuất allowlist.
2. JVM precheck `workflowDevelopmentSmoke --development-integration audit`:
   bundle V3/V4/raw manifest pin; mỗi connection verify-full/CA/hostname,
   backend SSL, session_user=current_user=postgres, đúng database/schema/search_path;
   READ ONLY/REPEATABLE READ, PostgreSQL 17.11, primary, fsync/full_page_writes/
   synchronous_commit; private schema owner/ACL/API deny; custom roles vẫn NOLOGIN,
   không default ACL lạ; event guard; V3/V4 đúng script/CRC/rank/success;
   9 inventory hashes golden, seed exact tuples 2/7/5, global+run counts 0/0.
   Lưu fingerprint catalog/roles/membership/default ACL/database ACL/schema OID/
   inventory/Flyway rows trong bộ nhớ. Rollback transaction chỉ đọc.
3. Chỉ sau precheck PASS: đặt development opt-in, schema và write approval đúng
   giá trị; gọi **một lần** task `workflowDevelopmentSmoke`, mode smoke và run-id
   trên. Không Spring application/HTTP listener, không thay production bean.
   Xóa write approval ngay khi child kết thúc, kể cả lỗi.
4. JVM/kết nối **mới**, mode read không write approval: kiểm tra cả snapshots,
   definition/version/resource, COMPLETED/v2, states cuối, nanos và cả 4 history
   records/actor/order. Một JVM khác audit READ ONLY lại: global/run instances=2,
   history=4; inventory/seed/checksums và fingerprint trước/sau phải khớp.
   Chỉ báo COMMITTED_PASS khi smoke ACK, fresh-JVM read và postcheck đều PASS.
   Sau smoke lỗi vẫn thử read/audit chỉ đọc; không lệnh ghi bù hoặc cleanup.

Lệnh **chỉ owner chạy sau approval**, từ root của worktree Issue #25:

```powershell
powershell.exe -NoProfile -File .\scripts\workflow-approved-smoke.ps1 -ExecuteApprovedSmoke -CaCertificate 'C:\dxos-secrets\prod-ca-2021.crt'
```

Launcher dùng `--offline --no-daemon --no-configuration-cache --console=plain --quiet`;
thiếu dependency/cache thì dừng, không tự tải hoặc hạ TLS. CA là file owner đã review;
file tồn tại không tự chứng minh trust, verify-full thực tế vẫn phải PASS mới ghi.
Approval trước của migrations không phải approval smoke.

### COMMIT_OUTCOME_UNKNOWN và đối chiếu

Bất kỳ lỗi/mất child report/exit khác 0 sau handoff đều được launcher xử lý
conservative là **COMMIT_OUTCOME_UNKNOWN**, kể cả lỗi postcheck sau smoke ACK.
Không suy ra rollback từ exit code. Nếu audit sau đó PASS, vẫn giữ UNKNOWN của
lượt lỗi; `AUDIT_OBSERVED` chỉ có nghĩa đọc được trạng thái, không nghĩa smoke PASS.
Không retry run hiện tại, không đổi run-id để ghi thêm, không xóa dữ liệu hoặc
repair/baseline/migrate. Dừng, giữ report/run-id, xin approval recovery riêng.

Đối chiếu chỉ đọc bằng JVM mới (không cấp write approval):

```powershell
powershell.exe -NoProfile -File .\scripts\workflow-approved-smoke.ps1 -AuditOnly -CaCertificate 'C:\dxos-secrets\prod-ca-2021.crt'
```

AuditOnly không xác nhận thành công của lượt ghi trước và không thay fingerprint
baseline bị mất. Nếu cần xác định bước cuối đã durable, đọc hai instance ids chính
xác trong bảng trên cùng history ORDER BY instance_id,resulting_runtime_version
trong transaction READ ONLY mới; đối chiếu state/lifecycle/version, resource,
seconds/nanos và actor với bảng kế hoạch. Mỗi transition phải có snapshot và history
cùng version. 2/4 cộng read PASS chỉ xác nhận final data đã có; 0/0 không cho phép
tự retry (writer/connection cũ có thể chưa quiesce). Snapshot/history không khớp,
thiếu một phần, catalog drift, connection/audit fail: giữ nguyên dữ liệu và escalate.

### Giới hạn và trạng thái phê duyệt

`postgres` có đặc quyền rộng; guard không phải sandbox quyền admin. Fingerprint
chỉ kiểm tra metadata/ACL, không chứng minh toàn bộ dữ liệu schema ngoài không đổi.
Cần tránh concurrent privileged DDL/DML trong cửa sổ test; drift phải dừng và review.
Credentials tồn tại dạng plaintext tạm trong process/child memory/environment;
không bật debug Gradle/JDBC hoặc chia sẻ crash dump, transcript hay raw output.
Production least-privilege login, full Spring restart và mất mạng Supabase thật
không được xác minh bằng smoke này. Không tự thay custom roles hoặc schema khác.

Kiểm chứng chuẩn bị: PowerShell parser và `git diff --check` PASS; 93 tests PASS,
0 FAIL / 0 SKIP qua 5 suites liên quan (2 PostgreSQL 17.11 TLS integration suites).
Bao gồm smoke/read JVM mới, audit catalog stable sau 2/4 runtime rows, từ chối
inventory/seed drift; launcher opt-in/config, missing/duplicate report, ACK mất
dù final data đã có, restart-read failure, postcheck drift, existing data,
secret-output filtering và AuditOnly. Test database/certificates là fixture local
disposable trên loopback, không dùng Supabase. V3/V4/manifest pins giữ nguyên.

```text
CLOUD_SMOKE_PLAN_READY=YES
EXPECTED_DATA_CHANGES=dxos_wf25_smoke_01: +2 instances, 6 instance UPDATEs, +4 history; retain rows
PRECHECK=OWNER_REPORTED_PASS
POSTCHECK=OWNER_REPORTED_PASS
RISKS=ADMIN_PRIVILEGES;PARTIAL_COMMITS;UNKNOWN_ACK;CONCURRENT_DRIFT;METADATA_ONLY_OUTSIDE_AUDIT
OWNER_APPROVAL_REQUIRED=YES
CLOUD_SMOKE_EXECUTION=OWNER_REPORTED_COMMITTED_PASS
```

## Nghiệm thu cloud và chuẩn bị PR — 2026-10-15

Mục này là trạng thái hiện hành, thay mọi nhãn pending/BLOCKED và phân loại file
lịch sử phía trên. Agent chỉ đọc GitHub/Git, sửa tài liệu/report và chạy fixture local;
không kết nối Supabase, không rerun cloud smoke/Flyway/bootstrap, không cloud writes,
không xóa dữ liệu cloud, không stage/commit/push/tạo PR/merge.

### Nghiệm thu owner cung cấp

```text
CLOUD_SMOKE_EXECUTION=COMMITTED_PASS
SMOKE_RUN_ID=cloud-20261015-01
PRECHECK=PASS
POSTCHECK=PASS
FRESH_JVM_PERSISTENCE=PASS
DEVELOPMENT_INSTANCES=2
DEVELOPMENT_HISTORY=4
CLOUD_SMOKE_EXIT=0
```

V3/V4 trước đó COMMITTED_PASS; SUCCESS_PIN_MATCH/checksum, inventory, seed và
postcommit audit PASS, MIGRATIONS_EXIT=0. Direct Connection/verify-full đã được
owner xác nhận trong báo cáo migrations trước đó. Đây là owner-reported evidence,
không phải cloud verification mới do agent thực hiện. Hai instances và bốn history
được giữ nguyên. Fresh-JVM persistence chứng minh đọc lại sau process smoke;
không tuyên bố đã restart một Spring service đang chạy trên cloud.

### Review branch và phạm vi

- Fetch origin chỉ cập nhật refs. Base PR thực tế `origin/main=cfb9d1d`,
  branch HEAD `837f5d8`: 0 commit phía main cần merge, 1 commit V3 đã tồn tại trước phiên.
  Local ref main cũ còn hiển thị hai diff Storage từ PR #30; chúng đã có trên
  origin/main và không nằm trong PR Issue #25. Không sửa hoặc hợp nhất các branch đó.
- Review tracked diff lẫn toàn bộ untracked candidates. Scope bao gồm JDBC
  transaction/locking/error precedence, immutable definitions, lossless Instant,
  schema/search_path, Flyway isolation/pins, event guard, operator development gate,
  Spring bean/configuration, test fixtures và local PostgreSQL runbook.
- Core port/domain, V1/V2, Request, Identity/Storage, application.yml và production
  HTTP exposure không đổi so với origin/main. Một production adapter/runtime bean;
  runtime dùng Clock.systemUTC, schema cấu hình bắt buộc. Runtime đã migrate phải
  cấu hình SPRING_FLYWAY_ENABLED=false; datasource cloud chỉ qua secret config và
  verify-full/CA, postgres không phải mô hình production least privilege.
- Tải dự kiến: một operator chạy bounded integration và nhiều JDBC connections
  ghi cùng instance. Row locks/unique history key kiểm soát cạnh tranh; không tuyên bố
  đã load-test hoặc latency-benchmark production từ các suite này.
- Một lỗi report được sửa trước PR: bootstrap helper/launcher không còn in
  OTHER_CLOUD_WRITES=NONE khi thiếu child report hoặc postcommit audit chưa xác minh.
  Chúng giữ COMMIT_UNKNOWN/exit fail và báo UNVERIFIED_POSTCOMMIT_AUDIT_NOT_CONFIRMED.
  Assertion launcher và real PostgreSQL lost-ACK/audit-failure đã được bổ sung;
  không đổi transaction logic, SQL, pins hoặc thao tác cloud.

### File đưa vào PR và file giữ ngoài

Tổng 73 candidate/preparation files: **55 file trong PR**, **18 file ngoài PR**.
Danh sách exact path nằm ở hai file chuẩn bị local:

- `docs/evidence/issue-25-pr-stage-paths.txt`: 54 paths để git add, không glob và
  không gồm backend/build.gradle. Có adapter/config/guard, V3/V4/manifest, mọi
  integration suites, unit/launcher tests hiện hành, current evidence, local
  Compose/verifier và các audit/launcher còn dependency của tests.
- `docs/evidence/issue-25-pr-build.patch`: nội dung backend/build.gradle cần stage
  thành file thứ 55. Patch được tạo trên HEAD, giữ toàn bộ task/test config hiện hành
  trừ registration workflowStage1A cũ. Không thay build.gradle WIP trên đĩa.
- `docs/evidence/issue-25-pr-exclude-paths.txt`: 18 paths giữ trên đĩa, gồm gói
  Stage1A Java/test/two SQL, Stage1B proposals/audit, tổng hợp provisioning SQL,
  research/Track B cũ, ba launcher provisioning cũ, PR draft và ba artifact chuẩn bị.
  Gói Stage1A có 3 unit tests; chúng không thuộc snapshot PR. Không loại integration
  suite hoặc guard/helper/SQL mà test hiện hành cần. PR draft là nội dung để owner
  dùng tạo PR sau approval, không phải source file cần commit.

Không chạy git add lúc chuẩn bị. Sau owner review/approval, từ đúng worktree
feature/data-postgres-adapter với index chưa có thay đổi khác, các lệnh dự kiến là:

```powershell
git add --pathspec-from-file=docs/evidence/issue-25-pr-stage-paths.txt
git apply --cached docs/evidence/issue-25-pr-build.patch
git diff --cached --check
git diff --cached --stat
git diff --cached
```

Không dùng git add toàn bộ build.gradle: nó sẽ đưa task Stage1A trỏ tới class nằm
ngoài PR. `git apply --cached --check` đã được chạy ở chế độ kiểm tra, không apply.
Nếu HEAD/index/build thay đổi sau báo cáo này, phải tạo lại patch và kiểm chứng;
không force apply. Full WIP vẫn giữ task/class/scripts cũ nguyên trên đĩa.

### Security review

Scan 73 candidates (tracked diff origin/main + untracked/preparation artifacts),
không in giá trị nhạy cảm: private key/keystore/certificate material, GitHub/cloud
token/JWT, credential URLs và JDBC user/password parameters. Hai strong matches
đều là localhost URL giả trong WorkflowSchemaTest kiểm tra từ chối credential URL;
sáu literal matches khác là fixture config/token giả trong negative tests.
Không có credential thật, token thật, CA/private-key file, `.env` thật, private
function review artifact hoặc dữ liệu nghiệp vụ production được đưa vào PR.
Manifest chỉ chứa reviewed metadata/digests; hostname/project ref là target metadata,
không phải authentication secret. Password chỉ qua environment/prompt ẩn; exception
và child reports được sanitize. Đây là review diff, không phải kiểm toán cloud IAM.

### Kiểm chứng và giới hạn nghiệm thu PR

Kết quả cuối sau sửa report UNKNOWN, JDK 21.0.11, PostgreSQL 17.11 TLS loopback:

| Phạm vi | Tests | FAIL | SKIP | Integration suites | Build |
| --- | ---: | ---: | ---: | ---: | --- |
| Full WIP, bao gồm gói Stage 1A trên đĩa | 772 | 0 | 0 | 9 | PASS, test build, 4m30s |
| Snapshot PR đúng 55 file | 769 | 0 | 0 | 9 | PASS, test build, 4m31s |

Narrow regression WorkflowSchemaBootstrapTest cũng PASS trước hai full runs cuối.
Chênh lệch 3 tests là WorkflowStage1AProvisionerTest được loại cùng gói Stage 1A,
không phải skip hoặc failure. Không bỏ integration suite để có kết quả PASS.
bootJar và plain jar đều build thành công. PowerShell parse, git diff --check,
scope/security/UTF-8 checks và staging patch --check PASS; index không có thay đổi.

Snapshot PR riêng được dựng từ archive origin/main cộng 54 allowlisted paths và
build.gradle trong patch; không copy WIP bị loại và không tạo commit/index mới.
Docker/PostgreSQL 17.11 TLS fixtures chỉ bind loopback, credentials/certificates
ngẫu nhiên ngoài Git. Migrations/bootstrap/smoke trong tests chỉ thuộc database
disposable local; không dùng cloud endpoint hoặc dữ liệu owner.

Ánh/Yến review chính sách precision và security trong PR, owner cho phép commit/
push/tạo PR, reviewer approval và merge commit là các bước tiếp theo, không phải
bằng chứng đã hoàn tất trong phiên này. Runtime identity ít quyền phải có trước
deployment lâu dài; không mở rộng ngoại lệ postgres development thành production.

```text
PR_READINESS=READY
FULL_TESTS=JAVA_21_WIP_772_PASS_PR_SNAPSHOT_769_PASS_0_FAIL_0_SKIP_9_INTEGRATION_SUITES
BUILD=PASS_TEST_BUILD_BOOTJAR
FILES_TO_STAGE=55_FILES_54_EXACT_PATHS_PLUS_BUILD_GRADLE_INDEX_PATCH
FILES_TO_EXCLUDE=18_FILES_PRESERVED_ON_DISK
SECURITY_REVIEW=PASS_NO_REAL_SECRETS_FOUND_SYNTHETIC_FIXTURES_CLASSIFIED
BLOCKERS=NONE_FOR_OWNER_PR_REVIEW
OWNER_APPROVAL_REQUIRED=YES
```
