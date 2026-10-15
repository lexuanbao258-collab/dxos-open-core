# DX-OS Open-Core backend

Nền backend cho Internal Service Portal của TRICORE, theo kiến trúc Modular
Monolith. Base package: `com.tricore.dxos`. Core cung cấp cơ chế; application
sở hữu ngữ nghĩa nghiệp vụ. Issue #1 tạo nền dự án và health API;
Issue #5 bổ sung tạo, danh sách và chi tiết Request; Issue #7 bổ sung workflow IT Support.

## Yêu cầu

- JDK **21**, với `JAVA_HOME` trỏ đến thư mục JDK.
- PowerShell trên Windows.
- Internet ở lần build đầu để Gradle Wrapper tải Gradle và dependency.
- PostgreSQL khả dụng khi chạy toàn bộ ứng dụng; môi trường Docker/PostgreSQL
  do Issue #3 cung cấp.

Dự án sử dụng Spring Boot 3.5.16, Gradle Groovy và Gradle Wrapper 8.14.3.
Không cần cài Gradle riêng. Các lệnh bên dưới chạy từ thư mục `backend/`.

## Build và test trên Windows

Ví dụ thiết lập JDK (thay đường dẫn theo máy):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\gradlew.bat --version
.\gradlew.bat test
.\gradlew.bat build
```

Build tạo executable JAR tại `build/libs/dxos-backend-0.0.1-SNAPSHOT.jar`.
Test health dùng `@WebMvcTest`, kiểm tra HTTP 200, Content-Type JSON và chính
xác body `{"status":"UP"}`. Build và test này không cần PostgreSQL hay biến
môi trường database. Không có database giả hoặc dependency H2.

Chạy riêng test health:

```powershell
.\gradlew.bat test --tests com.tricore.dxos.common.health.HealthControllerTest
```

## PostgreSQL và biến môi trường

Cần cung cấp đủ năm biến sau trong process chạy ứng dụng:

| Biến | Ý nghĩa |
| --- | --- |
| `DB_HOST` | Host PostgreSQL có thể truy cập từ backend |
| `DB_PORT` | Cổng PostgreSQL |
| `DB_NAME` | Database đã được tạo |
| `DB_USER` | Tài khoản PostgreSQL |
| `DB_PASSWORD` | Mật khẩu của tài khoản |

Datasource URL: `jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}`.
Không có credentials mặc định. Hibernate `ddl-auto: none` không tạo hoặc sửa
schema; `open-in-view: false` giới hạn persistence ngoài HTTP rendering.

Ví dụ PowerShell nhập cấu hình mà không ghi mật khẩu vào source hoặc command
history:

Adapter Workflow #25 còn cần `DXOS_WORKFLOW_SCHEMA`, datasource có `currentSchema`
đúng schema riêng và `SPRING_FLYWAY_ENABLED=false` ở runtime. Schema/migrations phải
có trước khi boot; xem **Chạy backend local từ clone mới** hoặc **Kết nối Supabase
PostgreSQL mặc định cho Issue #25** bên dưới. Login runtime riêng là yêu cầu cho
triển khai lâu dài, không phải prerequisite của bước kết nối Supabase ban đầu.

```powershell
$env:DB_HOST = Read-Host 'DB_HOST (theo môi trường Issue #3)'
$env:DB_PORT = Read-Host 'DB_PORT'
$env:DB_NAME = Read-Host 'DB_NAME'
$env:DB_USER = Read-Host 'DB_USER'
$env:DB_PASSWORD = Read-Host 'DB_PASSWORD' -MaskInput
.\gradlew.bat bootRun
```

`-MaskInput` cần PowerShell 7.1 trở lên. Với Windows PowerShell 5.1, nhập
mật khẩu bằng cách sau thay cho dòng `DB_PASSWORD` bên trên:

```powershell
$dbCredential = Get-Credential -UserName $env:DB_USER -Message 'PostgreSQL credentials'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
.\gradlew.bat bootRun
```

Cũng có thể chạy JAR đã build, với cùng các biến môi trường:

```powershell
java -jar .\build\libs\dxos-backend-0.0.1-SNAPSHOT.jar
```

Spring Boot không tự đọc file `.env`. Nếu dùng file này qua công cụ local,
không commit nó. `.gitignore` loại trừ `.env`, build output, Gradle cache,
IDE metadata và file tạm. Sau khi dừng ứng dụng, có thể xóa mật khẩu khỏi
process PowerShell:

```powershell
Remove-Item Env:DB_PASSWORD
```

## Health API

Sau khi ứng dụng khởi động, từ cửa sổ PowerShell khác:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/health'
```

`GET /api/v1/health` trả HTTP 200 với JSON `{"status":"UP"}`. Controller
không truy vấn database, không có logic nghiệp vụ hoặc phụ thuộc module
Request/Workflow. Đây là kiểm tra HTTP của ứng dụng, không chứng minh
PostgreSQL khả dụng. Toàn bộ ứng dụng vẫn cần cấu hình và kết nối PostgreSQL
để JPA khởi động.

Actuator chỉ expose `/actuator/health` qua HTTP, không hiển thị details hoặc
components và không expose endpoint qua JMX. Health tổng hợp của Actuator
có thể kiểm tra database và trả trạng thái khác health API tùy tình trạng
PostgreSQL. Không expose `env`, `beans`, `metrics` hoặc endpoint quản trị khác.

## Request API

Module `request` dùng luồng controller → service → repository → PostgreSQL.
REST nhận/trả DTO riêng; service quản lý transaction và backend luôn gán `NEW`
khi tạo. `requestType` là chuỗi do application cung cấp; chưa có danh mục loại yêu cầu.

| Endpoint | Kết quả |
| --- | --- |
| `POST /api/v1/requests` | `201 Created`, response DTO và header `Location` |
| `GET /api/v1/requests` | `200 OK`, mảng response DTO; `[]` nếu chưa có dữ liệu |
| `GET /api/v1/requests/{id}` | `200 OK`, response DTO; `404` nếu UUID không tồn tại |

Body tạo Request:

```json
{"title":"Repair printer","description":"Printer is offline","requestType":"IT_SUPPORT"}
```

Cả ba trường bắt buộc và không được chỉ chứa khoảng trắng. `title` tối đa 200
ký tự; `requestType` tối đa 100 ký tự. Body có trường khác (kể cả `status`) bị
từ chối. Response gồm `id` (UUID), `title`, `description`, `requestType`,
`status`, `assigneeId`, `resolution`, `version`, `createdAt`, `updatedAt` (thời gian UTC).
Request mới có `assigneeId`/`resolution` null và version 0. List chưa phân trang và
không cam kết thứ tự.

Lỗi JSON có `status`, `code`, `message`, `errors` (map tên trường → thông báo).
Validation trả `400 VALIDATION_FAILED`; JSON/body sai trả `400 INVALID_BODY`;
UUID sai trả `400 INVALID_ID`; không tìm thấy trả `404 REQUEST_NOT_FOUND`.

Flyway tự chạy các migration trong `db/migration` trước JPA khi khởi động,
dùng cùng datasource `DB_*`. V1 tạo bảng `requests` với UUID primary key,
`timestamptz`, các trường bắt buộc và CHECK cho sáu trạng thái lifecycle.
V1 đã merge và không được sửa. V2 thêm `assignee_id` (100 ký tự), `resolution`
(4000 ký tự), `version` (BIGINT, mặc định 0 cho dữ liệu M1), và bảng history.
Hibernate vẫn dùng `ddl-auto: none`. Môi trường Issue #3 cần cung cấp database
và tài khoản có quyền tạo bảng/schema history để migration chạy lần đầu.

Test domain, service (mock repository) và MVC (mock service) chạy bằng
`.\gradlew.bat test`, không cần PostgreSQL hoặc H2. Chúng chưa chứng minh
migration/JPA hoạt động trên PostgreSQL thật. Khi có môi trường Issue #3,
cần xác minh startup, migration và cả ba endpoint, bao gồm dữ liệu còn tồn tại
sau khi khởi động lại ứng dụng.

## IT Support workflow API

Chỉ có lifecycle `NEW → ASSIGNED → IN_PROGRESS → RESOLVED → CONFIRMED → CLOSED`.
`CLOSED` là trạng thái cuối. Các method domain kiểm tra trạng thái trước khi đổi
dữ liệu; action không hợp lệ không đổi Request hoặc timestamp.

Tất cả endpoint dưới đây có prefix `/api/v1/requests/{id}`:

| Endpoint | Body | Transition/action |
| --- | --- | --- |
| `POST /assign` | `{"assigneeId":"it-user-001"}` | `NEW → ASSIGNED`, `ASSIGN` |
| `POST /start` | Không cần | `ASSIGNED → IN_PROGRESS`, `START` |
| `POST /resolve` | `{"resolution":"Restarted print service"}` | `IN_PROGRESS → RESOLVED`, `RESOLVE` |
| `POST /confirm` | Không cần | `RESOLVED → CONFIRMED`, `CONFIRM` |
| `POST /close` | Không cần | `CONFIRMED → CLOSED`, `CLOSE` |
| `GET /history` | Không cần | Mảng history DTO theo `changedAt ASC, id ASC` |

Action thành công trả `200 OK` với RequestResponse. `assigneeId` bắt buộc,
nonblank và tối đa 100 ký tự; `resolution` bắt buộc, nonblank và tối đa 4000.
Body assignment/resolve từ chối trường ngoài contract. API tạo Request từ chối
`status`, `assigneeId`, `resolution`, `version` do client gửi.

Mỗi action chạy trong một Spring transaction: load → kiểm tra domain → cập nhật
Request/`updatedAt` → flush Request → insert đúng một history entry → commit.
Request và history cùng rollback khi có lỗi. History lưu `request_id`,
`from_status`, `to_status`, `action`, `changed_at` (bằng `updatedAt` mới), có FK
đến Request, CHECK cặp transition/action hợp lệ và index `(request_id, changed_at, id)`.
History DTO chỉ gồm `fromStatus`, `toStatus`, `action`, `changedAt`.
Request tồn tại chưa có action trả `[]`; Request không tồn tại trả `404`.

`@Version` kiểm tra optimistic locking khi update; DTO trả version sau flush.
Transition không hợp lệ trả `409 INVALID_REQUEST_TRANSITION` kèm trạng thái/action.
Conflict đồng thời trả `409 REQUEST_CONFLICT`; client cần reload trước khi thử lại.
Validation/JSON/UUID sai vẫn trả `400`, không tìm thấy trả `404` theo ApiError.

Test bao phủ lifecycle, mọi action sai, history, DTO/validation/errors,
`@Version` mapping và Spring transaction advice. Transaction manager/repository
được mock trong các test này; chúng chưa chứng minh rollback hay concurrency
trên PostgreSQL thật. Không cần database local hoặc H2 để chạy test.

Khi process có đủ `DB_*`, cần chạy ứng dụng, kiểm tra Flyway V2 thành công,
tạo Request rồi lần lượt assign/start/resolve/confirm/close, và lấy history
(đúng năm entry). Thử action sai phải trả `409`, detail/history không đổi.
Khởi động lại rồi GET cùng UUID để xác minh persistence. Kiểm chứng đồng thời
hai transaction đọc cùng version và rollback khi history insert lỗi vẫn cần
thực hiện với PostgreSQL thật. Không ghi credentials vào source hoặc log.

## Workflow PostgreSQL adapter (Issue #25)

Nghiệm thu cloud ngày 2026-10-15 theo owner: V3/V4 COMMITTED_PASS,
checksum/inventory/seed PASS; smoke `cloud-20261015-01` COMMITTED_PASS,
precheck/postcheck/fresh-JVM persistence PASS, 2 instances / 4 history, exit 0.
Giữ dữ liệu thử; không chạy lại bootstrap, Flyway hoặc smoke. Các hướng dẫn cloud
phía dưới là hồ sơ đã thực thi, không tự cấp quyền cho lượt ghi mới. `postgres`
chỉ là ngoại lệ development một lần; deployment lâu dài cần runtime ít quyền.

Adapter JDBC ở `infrastructure.workflow` cung cấp một bean `WorkflowPersistencePort`
và một `WorkflowRuntime` dùng `Clock.systemUTC()`. Core và Workflow HTTP không thay
đổi; controller Workflow vẫn không được đăng ký production. Adapter không thực
hiện resource ACL, không giải quyết revocation/TOCTOU của Issue #23.

V3 tạo bảng riêng cho definitions, instances và history; V1/V2 giữ nguyên. V4
seed hai definition độc lập: `document-review@1` và `incident-resolution@1`.
Import definition bằng `PostgresWorkflowPersistence.provisionDefinition` với
datasource của provisioner: cùng key/cùng nội dung là idempotent, khác nội dung
bị từ chối. Toàn bộ states/transitions phải được insert trong cùng transaction
với definition mới; database chặn bổ sung, UPDATE, DELETE và TRUNCATE sau commit.
Thay đổi nội dung cần version mới. Table owner/superuser vẫn có thể vô hiệu hóa
trigger, vì vậy runtime login phải khác owner và không có quyền DDL/provisioning.

Thời gian lưu lossless bằng epoch seconds BIGINT và nanos INTEGER, nhận toàn bộ
miền `Instant.MIN..MAX` của Java, kể cả nanos không chia hết cho 1000. Không cần
clock làm tròn. Database phải có `fsync=on`, `full_page_writes=on`; mỗi write đặt
`SET LOCAL synchronous_commit=on`, khóa instance và chỉ trả success sau COMMIT
được xác nhận. Hạ tầng lưu trữ của PostgreSQL phải đáp ứng durability.

Lỗi trước COMMIT có rollback được xác nhận là `OPERATION_FAILURE`. Mất phản hồi
COMMIT hoặc không xác nhận được rollback là `COMMIT_OUTCOME_UNKNOWN`; adapter
không retry. Một rollback sau lỗi COMMIT không chứng minh write trước đó chưa
commit. Đọc authoritative instance/history để reconcile, nhưng hai lần đọc
riêng không dùng chung snapshot, dữ liệu cũ hoặc chưa có khi transaction đang
chưa kết thúc không chứng minh rollback hay xác định writer thắng.

### Cấu hình schema bắt buộc

`DXOS_WORKFLOW_SCHEMA` (property `dxos.workflow.schema`) phải là identifier lowercase
`[a-z][a-z0-9_]{0,62}`, không phải public/extensions/information_schema/pg_*.
Không có schema mặc định. Schema phải được provision riêng.
Mỗi connection phải có currentSchema/search_path bắt đầu bằng schema này, chỉ theo
sau bởi pg_catalog/pg_temp; JDBC URL không được ghi đè sang schema khác.
SQL adapter và toàn bộ Workflow objects V3/V4 dùng schema-qualified identifiers.
Flyway phải đi qua `WorkflowMigrations.configure` hoặc Spring customizer:
defaultSchema/schemas cùng một schema, createSchemas=false, transaction bắt buộc,
không clean/repair/baseline. Flyway native defaultSchema placeholder được chèn sau
khi Java đã xác thực identifier; không chạy SQL bằng cơ chế thay placeholder tùy ý.

### PostgreSQL local có thể tái lập

Cách kiểm tra đầy đủ từ source, không cần Supabase, WARP hoặc credentials nhóm:

```powershell
# Từ backend/, với Docker đang chạy và JDK 21 trên PATH:
powershell.exe -NoProfile -File ..\scripts\workflow-verify-local.ps1
if ($LASTEXITCODE -ne 0) { throw 'Local verification failed' }
```

Launcher dùng PostgreSQL 17.11 trên cổng loopback ngẫu nhiên, sinh CA đúng/sai
bằng OpenSSL của Git for Windows và bật toàn bộ TLS/event-trigger/audit fixtures.
Credentials chỉ ở environment/stdin; private keys nằm ngoài Git trong thư mục
current user/SYSTEM. Nó chạy `test build`, kiểm tra XML không có FAIL/SKIP và đủ
chín integration suites, rồi dừng/xóa container của nó và dọn certificates.
Đây là database disposable; dùng Compose bên dưới khi cần giữ dữ liệu local.

Cần Docker và JDK 21. Compose `infra/compose.workflow-postgres.yml` chạy PostgreSQL
17.11 trên loopback 55425, giữ named volume riêng cho major 17; không dùng lại volume 16.
Password chỉ cấp qua môi trường bảo mật,
không ghi vào source/command line. Giữ password cũ khi dùng lại volume.

Các biến integration: WORKFLOW_TEST_JDBC_URL, WORKFLOW_TEST_DB_USER,
WORKFLOW_TEST_DB_PASSWORD và tùy chọn WORKFLOW_TEST_MIGRATION_USER,
WORKFLOW_TEST_MIGRATION_PASSWORD. Test URL phải là loopback với port/database,
không có query; fixtures từ chối URL cloud. Runtime riêng cần login, không superuser,
CREATEDB, CREATEROLE hoặc schema ownership; migrator local tạo/xóa schema test.
Isolation fixture tạo API roles giả trong database disposable, sentinel trong public,
kiểm tra ACL bên ngoài, rồi xóa đúng objects do fixture tạo.

Từ backend, với môi trường local đã cấu hình:

```powershell
$env:WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE = 'true'
.\gradlew.bat workflowIntegrationTest --no-daemon
.\gradlew.bat test build --no-daemon
```

Integration gồm regression adapter V1–V4 và isolation suite V3/V4 độc lập.
Không chạy các suite này trên shared Supabase. Không có database thì task test skip
integration, còn workflowIntegrationTest fail vì thiếu biến môi trường.

### Chạy backend local từ clone mới

Các lệnh dưới đây chỉ dùng database loopback do Compose tạo. Không dùng tài khoản
hoặc endpoint Supabase. Mỗi volume mới chỉ provision role/schema một lần; khi dùng
lại volume, giữ mật khẩu cũ và đọc trạng thái trước, không chạy lại `migrate` nếu
schema đã có Flyway history.

```powershell
# Từ backend/:
$localDb = Get-Credential -UserName workflow_test -Message 'Choose the local PostgreSQL owner password'
$env:WORKFLOW_LOCAL_DB_PASSWORD = $localDb.GetNetworkCredential().Password
docker compose -f ..\infra\compose.workflow-postgres.yml up -d --wait
docker compose -f ..\infra\compose.workflow-postgres.yml exec workflow-postgres psql -U workflow_test -d workflow_test
```

Trong psql của volume local mới, nhập mật khẩu runtime ở prompt ẩn:

```text
CREATE ROLE workflow_runtime LOGIN NOINHERIT NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
\password workflow_runtime
CREATE SCHEMA dxos_wf25_smoke_01 AUTHORIZATION workflow_test;
GRANT USAGE ON SCHEMA dxos_wf25_smoke_01 TO workflow_runtime;
\q
```

Chạy V3/V4 riêng bằng owner local, rồi cấp quyền runtime:

```powershell
$runtimeDb = Get-Credential -UserName workflow_runtime -Message 'Enter the local runtime password'
$env:WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE = 'true'
$env:WORKFLOW_SMOKE_LOCAL_DISPOSABLE = 'true'
$env:WORKFLOW_SMOKE_JDBC_URL = 'jdbc:postgresql://127.0.0.1:55425/workflow_test'
$env:WORKFLOW_SMOKE_MIGRATOR_USER = 'workflow_test'
$env:WORKFLOW_SMOKE_MIGRATOR_PASSWORD = $env:WORKFLOW_LOCAL_DB_PASSWORD
$env:WORKFLOW_SMOKE_RUNTIME_USER = 'workflow_runtime'
$env:WORKFLOW_SMOKE_RUNTIME_PASSWORD = $runtimeDb.GetNetworkCredential().Password
$env:WORKFLOW_SMOKE_BUNDLE = Join-Path (Get-Location) 'build\workflow-smoke-bundle'
.\gradlew.bat workflowSmokeBundle workflowSmoke --args=migrate --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Local migration failed' }
docker compose -f ..\infra\compose.workflow-postgres.yml exec workflow-postgres psql -U workflow_test -d workflow_test
```

Trong psql local:

```sql
GRANT SELECT ON dxos_wf25_smoke_01.workflow_definitions,
  dxos_wf25_smoke_01.workflow_definition_states, dxos_wf25_smoke_01.workflow_definition_transitions,
  dxos_wf25_smoke_01.workflow_instances, dxos_wf25_smoke_01.workflow_transition_history TO workflow_runtime;
GRANT INSERT ON dxos_wf25_smoke_01.workflow_instances,
  dxos_wf25_smoke_01.workflow_transition_history TO workflow_runtime;
GRANT UPDATE (current_state, lifecycle, runtime_version, updated_at_seconds, updated_at_nanos)
  ON dxos_wf25_smoke_01.workflow_instances TO workflow_runtime;
```

Sau `\q`, kiểm tra dữ liệu và boot với login runtime:

```powershell
.\gradlew.bat workflowSmoke --args="smoke local-first-run" --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Local smoke failed' }
.\gradlew.bat workflowSmoke --args="read local-first-run" --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'New-process read failed' }
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '55425'
$env:DB_NAME = 'workflow_test'
$env:DB_USER = 'workflow_runtime'
$env:DB_PASSWORD = $runtimeDb.GetNetworkCredential().Password
$env:DXOS_WORKFLOW_SCHEMA = 'dxos_wf25_smoke_01'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:55425/workflow_test?currentSchema=dxos_wf25_smoke_01,pg_catalog,pg_temp'
$env:SPRING_FLYWAY_ENABLED = 'false'
.\gradlew.bat bootRun
```

Health ở `/api/v1/health`. Workflow HTTP controller vẫn chưa đăng ký production
cho đến khi verified principal/authorization được tích hợp; #25 cung cấp bean
adapter/runtime và constructor injection, không mở route trước gate bảo mật đó.
V3/V4 không tạo Request tables; demo Request vẫn cần V1/V2 của quy trình riêng.
Sau khi dừng backend, `docker compose ... down` giữ named volume; tránh `down -v`
khi kiểm tra restart. Đọc lại cùng run-id từ process mới để xác minh persistence.
Không chạy `smoke` lại với run-id cũ: runner từ chối replay trước write.

### Kết nối Supabase PostgreSQL mặc định cho Issue #25

Theo [Issue #25 cập nhật ngày 2026-10-14](https://github.com/lexuanbao258-collab/dxos-open-core/issues/25),
dùng Direct Connection của project như pgAdmin: `db.<project-ref>.supabase.co`, port
`5432`, database `postgres`, tài khoản `postgres` và mật khẩu database hiện có.
Máy phát triển cần IPv6; PostgreSQL local ở trên vẫn chạy độc lập với Supabase/WARP.
Không chạy Stage 1A/1B, không cấp SCRAM/LOGIN và không đổi hai custom roles đang
`NOLOGIN`. Các hồ sơ đó là optional hardening cho triển khai lâu dài.

Trong phiên PowerShell đã có `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
và `PGSSLROOTCERT` (CA tin cậy lấy từ project), kiểm tra kết nối chỉ đọc từ repo root:

```powershell
powershell.exe -NoProfile -File .\scripts\workflow-read-only-preflight.ps1
if ($LASTEXITCODE -ne 0) { throw 'Read-only connection check failed' }
```

Preflight ép JDBC `sslmode=verify-full`, CA, timeout và transaction READ ONLY, rồi
rollback/close. Không boot Spring, chạy Flyway hay tạo role/schema. Không đưa secrets
vào URL, args, source, command history hoặc báo cáo; chỉ nhập bằng secure prompt hay
secret environment hiện có. Kết quả kiểm tra không phải phê duyệt thao tác ghi.

Cấu hình Spring dùng cùng `DB_*`; URL phải bổ sung schema và TLS trước khi sử dụng:

```powershell
$env:DXOS_WORKFLOW_SCHEMA = 'dxos_wf25_smoke_01'
$caPath = [Uri]::EscapeDataString($env:PGSSLROOTCERT)
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://$($env:DB_HOST):$($env:DB_PORT)/$($env:DB_NAME)?currentSchema=$($env:DXOS_WORKFLOW_SCHEMA),pg_catalog,pg_temp&sslmode=verify-full&sslrootcert=$caPath"
$env:SPRING_FLYWAY_ENABLED = 'false'
```

Đây chỉ là cấu hình chuẩn bị, chưa phải lệnh boot/migrate. Cloud writes cần owner
phê duyệt riêng schema, chỉ bundle V3/V4, seed và bounded smoke/restart/read, kèm
postchecks và recovery; không chạy V1/V2 hoặc clean/repair/baseline trên shared DB.
Không retry khi COMMIT chưa rõ kết quả. Giữ kiểm tra schema isolation, manifest/event
triggers và quyền API/PUBLIC hiện có; không tắt guard để kết nối dễ hơn.

`postgres` chỉ là ngoại lệ kiểm chứng một lần được phê duyệt, không dùng cho backend
chạy lâu dài hoặc frontend. Phải có runtime identity tối thiểu trước triển khai
persistent/shared production. `WorkflowSmokeRunner` vẫn yêu cầu runtime khác owner;
entrypoint production và constructor mặc định không đọc các cờ development.
Cloud migration/smoke/restart DoD còn pending, không thay bằng local PASS.

### Development integration một lần bằng postgres

Task riêng `workflowDevelopmentSmoke` gọi `WorkflowDevelopmentSmokeRunner`, không
được đăng ký Spring bean hay chạy từ bootRun/build hoặc cấu hình production.
Nó tái sử dụng bounded smoke/check/restart scenario, chỉ mở ngoại lệ owner/runtime
cho chế độ development đã opt-in. Runner production giữ kiểm tra non-owner và
least-privilege hiện có. Không tự tạo schema, roles, GRANT, cleanup hoặc retry.

Yêu cầu đồng thời: argument `--development-integration`, opt-in có giá trị chính xác,
schema duy nhất `dxos_wf25_smoke_01`, DB_USER/DB_NAME là `postgres`, Direct endpoint
đúng project, CA và verify-full. Mỗi connection xác minh session_user=current_user
postgres, backend TLS, schema owner postgres, không schema ACL cho PUBLIC/roles
khác hoặc quyền hiệu lực của API roles, không relation do owner khác sở hữu.
Guard/manifest, bundle V3/V4 pin, durability, default ACL, history/checksums và
inventory checks vẫn hoạt động. Chế độ check/read không có quyền gọi migrate/smoke.

Schema đã bootstrap thành công theo report owner; không chạy lại. Sau preflight
fresh, chỉ chuẩn bị/check từ repo root (chưa đặt write approval):

```powershell
# DB_* và PGSSLROOTCERT đã có trong secret environment; không in chúng.
$env:WORKFLOW_DEV_INTEGRATION_OPT_IN = 'issue-25-postgres-development-only'
$env:WORKFLOW_DEV_APPROVED_SCHEMA = 'dxos_wf25_smoke_01'
$env:WORKFLOW_DEV_PROJECT_REF = $env:DB_HOST.Split('.')[1]
Remove-Item Env:WORKFLOW_DEV_WRITE_APPROVAL -ErrorAction SilentlyContinue
Push-Location backend
.\gradlew.bat workflowSmokeBundle --no-daemon
if ($LASTEXITCODE -ne 0) { throw 'Bundle preparation failed' }
$env:WORKFLOW_DEV_BUNDLE = (Resolve-Path 'build/workflow-smoke-bundle').Path
.\gradlew.bat workflowDevelopmentSmoke --args="--development-integration check" --no-daemon
Pop-Location
```

Không đặt `WORKFLOW_DEV_LOCAL_DISPOSABLE`, `WF25_DISPOSABLE_TEST` hoặc
`WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE` trên cloud, kể cả giá trị false. Chế độ local
đòi cả ba cờ fixture cùng marker `dxos.wf25_disposable=on` của login postgres,
loopback và TLS verify-full; cờ local không thể áp dụng cho endpoint Supabase.

**Owner đã phê duyệt chỉ V3/V4 đã pin sau fresh prechecks PASS.**
Dùng launcher một mục đích ở phần dưới; không chạy trực tiếp ví dụ Gradle để bỏ qua
audit/report. Approval không bao gồm smoke, bootstrap hoặc recovery.
Entry point nội bộ của launcher là mode migration riêng:

~~~powershell
# Cần explicit WORKFLOW_DEV_WRITE_APPROVAL khớp schema sau approval, không đặt trước.
# Chạy từ backend; credentials/CA chỉ từ secret environment.
.\gradlew.bat workflowDevelopmentSmoke --args="--development-integration migrate" --offline --no-daemon --no-configuration-cache --quiet
~~~

Clear write approval trong finally, kể cả command lỗi. Dừng sau migrate để audit
qua connection mới READ ONLY. Smoke/read/restart là bước có approval riêng;
không chạy smoke cùng batch migrate hoặc suy ra approval từ schema bootstrap.

Schema phải đã tồn tại/rỗng và owner postgres; thao tác bootstrap schema là approval
riêng, không do runner thực hiện. Migrate chỉ V3/V4: 5 tables + Flyway history,
2 functions/9 explicit triggers (6 row, 3 statement), seed 2 definitions/7 states/5 transitions, 2 success
history rows/checksums. Smoke tạo tối đa 2 instances/4 history rows cho run-id mới,
race activation version 0/transition, kiểm tra Instant nanos và replay; read dùng
process/JVM mới không ghi. Dữ liệu smoke giữ lại. Đây là durability qua JVM mới;
cloud restart Spring bằng production bean còn cần evidence riêng.

Nếu V3 đã commit/V4 lỗi hoặc `WORKFLOW_DEVELOPMENT_FAIL_COMMIT_OUTCOME_UNKNOWN`,
dừng, đọc catalog/history/instances bằng connection mới và xin recovery approval;
không rerun tự động, clean/repair/baseline hay xóa dữ liệu đã commit. Tài khoản admin
vẫn có thể tác động ngoài schema; guard thu hẹp phạm vi tool, không sandbox quyền
admin hoặc loại bỏ privileged concurrent DDL.

### Lệnh preflight Supabase chỉ đọc trước cloud migrations

**Cập nhật sau migrations:** owner đã xác nhận V3/V4 `COMMITTED_PASS`, checksum,
inventory, seed và postcommit audit PASS, `MIGRATIONS_EXIT=0`. Các lệnh migration
trên/dưới là lịch sử; **không chạy lại bootstrap hoặc Flyway**.
Owner cũng xác nhận cloud smoke `cloud-20261015-01` COMMITTED_PASS, precheck,
postcheck và fresh-JVM persistence PASS, 2 instances / 4 history, exit 0.
Không chạy lại cloud smoke; giữ nguyên dữ liệu thử. Kế hoạch, dữ liệu và recovery ở phần “Cloud smoke sau migrations”
trong `../docs/evidence/issue-25-readiness.md`.

Từ repo root, Direct host/CA/password đã được cấp qua môi trường bảo mật:

```powershell
$env:DB_PORT = '5432'
$env:DB_NAME = 'postgres'
$env:DB_USER = 'postgres'
powershell.exe -NoProfile -File .\scripts\workflow-read-only-preflight.ps1
if ($LASTEXITCODE -ne 0) { throw 'Direct read-only preflight failed' }
powershell.exe -NoProfile -File .\scripts\workflow-live-guard.ps1
if ($LASTEXITCODE -ne 0) { throw 'Reviewed catalog guard failed' }
```

Kiểm tra dự kiến: Direct IPv6, client verify-full/CA, backend SSL=true,
transaction_read_only=on, session_user=current_user=postgres, đúng database;
schema absent hoặc owner/ACL/inventory đúng kế hoạch; hai custom roles vẫn NOLOGIN;
không drift ngoài phạm vi, durability/settings và manifest/bundle pin khớp.
Owner phải kiểm tra thêm Dashboard API exposed schemas không chứa schema test.
`LIVE_GUARD=PASS` chỉ xác minh catalog; WRITE_READINESS=BLOCKED không tự cấp approval.
Target hiện đã bootstrap: nếu preflight fresh báo ABSENT, dừng và báo sai lệch,
không chạy lại bootstrap. Dev check phải trả EMPTY trước migration. Không chạy Stage 1A/1B hoặc thay credentials/custom roles.

### Schema Bootstrap đã hoàn tất — không chạy lại

Owner báo SCHEMA_BOOTSTRAP=COMMITTED_PASS, POSTCOMMIT_AUDIT=PASS,
SCHEMA_OWNER=postgres, SCHEMA_EMPTY=YES, FLYWAY_HISTORY=ABSENT,
OTHER_CLOUD_WRITES=NONE, BOOTSTRAP_EXIT=0.
Đây là evidence owner cung cấp; Codex không thực thi lại cloud.
Không chạy workflow-schema-bootstrap.ps1 hoặc CREATE SCHEMA lại.

V3/V4 và cloud smoke đã hoàn tất theo owner report. Phần
[thực thi Flyway V3/V4](../docs/evidence/issue-25-readiness.md#thực-thi-flyway-v3v4--owner-đã-phê-duyệt)
là hồ sơ lịch sử; không phải bước tiếp theo và không chạy lại.
Launcher khóa đúng Direct endpoint và schema; tự dừng nếu precheck FAIL.
Dùng Direct db.zsfctpnitkdztlfhzqgw.supabase.co:5432/postgres, tài khoản postgres,
TLS verify-full và CA/credentials từ secret environment. Precheck fresh phải xác
nhận schema PRESENT/rỗng/owner postgres, guard/hash không drift; không dùng
điều kiện ABSENT từ kế hoạch bootstrap lịch sử.

Approval migration phải gồm V3/V4 pin, Flyway history, V4 seed và V3 object ACL
revocations chỉ trong target; không gồm smoke, role changes hoặc recovery.
Không gọi smoke trong cùng batch migrate. Migrate thất bại hoặc mất ACK:
dừng, clear write approval trong finally, reconcile qua connection READ ONLY mới;
không retry/clean/repair/baseline/DROP. Postcheck đầy đủ trước khi báo cloud PASS.

### Launcher V3/V4 lịch sử — đã hoàn tất, không chạy lại

Lệnh lưu để review việc đã thực thi; không chạy trong lúc chuẩn bị PR:

~~~powershell
& 'C:\Users\Admin\AppData\Local\codex-with-chatgpt\worktrees\dxos-data-postgres-adapter\scripts\workflow-approved-migrations.ps1' -ExecuteApprovedMigrations
~~~

Mặc định dùng PGSSLROOTCERT; nếu chưa có, hỏi đường dẫn CA. Password lấy từ
DB_PASSWORD đã có hoặc prompt ẩn. Launcher yêu cầu JDK 21, WARP Connected và Direct
host có IPv6; không tự bật/sửa WARP, CA hay endpoint sai. Environment cũ được khôi
phục trong finally; Gradle offline, không configuration cache, không log exception.
Chỉ in report allowlist đầy đủ; child mất/thiếu report sau handoff là COMMIT_UNKNOWN.

Java task workflowApprovedMigration chỉ chạy V3/V4 đã pin, createSchemas=false,
không baseline/repair/clean/retry/smoke. Precheck READ ONLY xác minh schema rỗng
owner postgres, history absent, TLS verify-full/backend SSL, PG17.11, NOLOGIN custom
roles, manifest raw hash và migration SHA/CRC. Guard lặp lại khi mở connection.
Sau migrate dùng connection mới READ ONLY: history, exact catalog fingerprint,
seed/0 instances, schema ACL/owner và outside catalog/roles/membership snapshot.
Fingerprint bao gồm columns/defaults/NOT NULL, indexes, constraints, function
definitions/ACL, explicit/internal triggers và types; không chỉ so counts.
Chỉ exit 0 khi COMMITTED_PASS + audit/inventory/seed PASS; lỗi thì dừng, không retry.

Giữ COMMIT_UNKNOWN khi mất ACK dù audit tìm thấy dữ liệu; history và seed được
đối chiếu riêng, không suy ra thành công chỉ từ một bên. V3 đã commit/V4 lỗi:
PARTIAL_COMMIT, giữ nguyên để owner duyệt recovery. Catalog drift hoặc fresh audit
không đọc được không được claim OTHER_CLOUD_WRITES=NONE. Mọi trường hợp đều dừng
sau migration/audit, NEXT_APPROVAL_REQUIRED=YES cho cloud smoke.

### Optional hardening — Runtime Privilege Audit READ-ONLY trên Supabase

Từ root worktree, với JDK 21 và các biến DB_HOST, DB_PORT, DB_NAME, DB_USER,
DB_PASSWORD, PGSSLROOTCERT đã có trong phiên PowerShell:

```powershell
powershell.exe -NoProfile -File .\scripts\workflow-runtime-privilege-audit.ps1
$LASTEXITCODE
```

Launcher hỗ trợ Direct Connection hoặc session pooler, port 5432; ép verify-full/CA,
search_path pg_catalog,pg_temp và transaction_read_only=on. SQL audit được pin SHA-256;
thay đổi SQL cần review rồi cập nhật pin. Không chạy Spring/Flyway/provisioning/smoke.
WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE phải không được đặt, kể cả giá trị false.
Secrets chỉ được kế thừa qua environment; không truyền qua args, in hoặc ghi báo cáo.

AUDIT_REPORT chỉ có role names cố định, statuses, booleans/counts và số quyền true
theo role/privilege; không chứa host/database/object identifiers, function source,
passwords hay connection strings. NOT_PROVISIONED nghĩa là chưa có role, không phải
permission PASS. RUNTIME_PRIVILEGE_AUDIT=PASS chỉ xác nhận catalog audit hoàn tất,
rollback/close thành công; WRITE_READINESS vẫn BLOCKED và cần reviewer xét ACL/TLS.

### Optional hardening — SECURITY DEFINER metadata và private review

Chạy từ root repo trong phiên PowerShell đã giữ `DB_*` và `PGSSLROOTCERT`:

```powershell
powershell.exe -NoProfile -File .\scripts\workflow-security-definer-review.ps1
$LASTEXITCODE
```

Launcher riêng dùng JDBC `verify-full`, `pg_catalog,pg_temp`, transaction READ ONLY
và SQL được pin SHA-256. Metadata giữ schema/signature/owner, EXECUTE/schema ACL,
search_path của function, quyền runtime nullable, membership, dependencies và
definition/configuration fingerprints. Không trả bodies hoặc các GUC khác.
`NOT_PROVISIONED` giữ quyền runtime là `null`, không phải `false`.

Chỉ sau khi metadata hợp lệ đã được xem xét, dùng đường dẫn `SHAREABLE_METADATA`
để lấy bodies/configuration vào một thư mục bảo vệ mới ngoài Git:

```powershell
powershell.exe -NoProfile -File .\scripts\workflow-security-definer-review.ps1 -MetadataFile '<SHAREABLE_METADATA>'
```

Baseline phải ở thư mục chỉ current user/SYSTEM truy cập; catalog drift, SQL pin
sai, rollback/close lỗi hoặc cấu hình TLS sai đều fail closed. Chỉ chia sẻ
`shareable-metadata.json`; không gửi `private-function-review.json` hay credentials
vào chat/Git. Helper tìm thấy khi review body cần capture metadata mới bằng
`-HelperSignature 'schema.helper(argument_types)'`, rồi private capture từ baseline
mới đó. Catalog dependencies không đầy đủ cho dynamic SQL/string-body routines.
Mọi routine bắt đầu `NEEDS_REVIEW`; capture thành công không tự cấp PASS bảo mật,
chấp nhận PUBLIC TEMP, phê duyệt roles hoặc giải quyết TLS backend-hop.

### Optional hardening — runner dùng migrator/runtime riêng

`workflowSmokeBundle` chỉ copy V3/V4. Runner kiểm tra chính xác hai filenames và
SHA-256 được pin trong `workflow-event-trigger-manifest.json` (chuẩn hóa CRLF thành LF), cùng Flyway checksums/history. Không nạp V1/V2.
Schema cố định: dxos_wf25_smoke_01; history đặt trong cùng schema.
Runner không boot Spring, không tự tạo schema/role, không GRANT, không cleanup,
DROP/TRUNCATE/DELETE hoặc retry khi kết quả commit chưa xác định.

Biến độc lập: WORKFLOW_SMOKE_JDBC_URL (không chứa user/password),
WORKFLOW_SMOKE_PROJECT_REF (bắt buộc cho cloud),
WORKFLOW_SMOKE_CA_CERT, WORKFLOW_SMOKE_MIGRATOR_USER/PASSWORD,
WORKFLOW_SMOKE_RUNTIME_USER/PASSWORD, WORKFLOW_SMOKE_BUNDLE (đường dẫn bundle);
WORKFLOW_SMOKE_RUN_ID dùng cho smoke/read khi không truyền run-id qua args.
Cấp secrets bằng secure environment bên ngoài chat. Cloud chỉ chấp nhận Direct
db.<project-ref>.supabase.co:5432/postgres với đúng project ref, CA file tồn tại và TLS verify-full;
hai username là tên role PostgreSQL trực tiếp, không có hậu tố project ref.
URL cloud không có query/fragment; currentSchema và TLS chỉ do runner đặt. Không có CA thì dừng.
WORKFLOW_SMOKE_LOCAL_DISPOSABLE=true chỉ dùng loopback, không thể hạ TLS cloud.

Migrator là owner của schema riêng, có USAGE/CREATE trong schema và USAGE plpgsql.
Runtime khác owner, không membership owner, không quyền CREATE schema, không
superuser/CREATEROLE/CREATEDB/BYPASSRLS. Sau migration, operator cấp runtime:
USAGE schema, SELECT năm Workflow tables, INSERT instances/history, UPDATE chỉ
current_state/lifecycle/runtime_version/updated_at_seconds/updated_at_nanos của
instances. Không cấp DDL, provisioning definitions, DELETE hoặc TRUNCATE.
Không sửa default privileges, database privileges hay role membership.
Dùng postgres làm migrator tạo objects do postgres sở hữu; role mạnh có thể tắt
trigger/sửa history nên chỉ dùng cho bước migration đã duyệt, không dùng runtime.
Default privileges tùy chỉnh hoặc event triggers lệch manifest khiến runner dừng;
không tự sửa hoặc tắt triggers. Manifest khóa đúng sáu triggers, function/config hashes,
owners/ACL và hai helper internal đã review. OID capture chỉ là evidence; helper được
resolve live theo signature. Remote chỉ nhận bundle filesystem V3/V4 và PostgreSQL 17.11.
Profile local cần `WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE=true`, loopback và inventory
event triggers hoàn toàn trống; flag không cho phép bỏ qua trigger lạ hoặc cloud.
Guard đọc hashes/config keys, không tải private bodies/config values; collector riêng
vẫn giữ private capture ngoài Git. Native binary là giả định tin cậy provider, không
được attested bằng hash catalog. Các watcher được duyệt có thể gửi NOTIFY reload schema.
Kiểm tra live chạy trên mỗi connection, trước migration và trước từng migration;
bundle được kiểm tra lại cả tại connection boundary. Chúng thu hẹp TOCTOU, không
loại bỏ privileged concurrent DDL hay concurrent filesystem mutation.

```powershell
.\gradlew.bat workflowSmokeBundle --no-daemon
.\gradlew.bat workflowSmoke --args=check --no-daemon
# Các lệnh dưới đây CHỈ sau phê duyệt riêng và bootstrap role/schema phù hợp:
.\gradlew.bat workflowSmoke --args=migrate --no-daemon
# Operator cấp các quyền runtime tối thiểu đã duyệt, rồi:
.\gradlew.bat workflowSmoke --args="smoke unique-run-id" --no-daemon
.\gradlew.bat workflowSmoke --args="read unique-run-id" --no-daemon
```

check dùng read-only transactions, xác minh ownership/quyền, mode/durability,
không có event triggers/default privileges chưa review, schema trống hoặc đúng
sáu tables, bundle và migration history. migrate chỉ nhận schema đã tồn tại và trống.
smoke tạo hai instances có prefix issue25/run-id riêng, race activation version 0,
race transition, hoàn tất cả hai definitions, kiểm tra hai history/instance và
Instant nanos. Run-id đã dùng bị từ chối. read dùng JVM/connection mới kiểm tra
snapshots/history; không ghi. Sau lỗi/COMMIT_OUTCOME_UNKNOWN dừng và điều tra,
không tự chạy lại. Dữ liệu smoke được giữ lại.

## Phụ thuộc Issue #3 và giới hạn xác minh

Issue #3 chịu trách nhiệm Docker/PostgreSQL cho phát triển: database, tài
khoản, credentials và host/port phù hợp. Issue #1 không tạo container,
schema nghiệp vụ hoặc cơ chế bỏ qua kết nối database.

Khi môi trường Issue #3 chưa khả dụng, có thể xác minh compilation, test MVC
và đóng gói JAR; chưa xác minh tích hợp PostgreSQL, toàn bộ startup với JPA,
hoặc HTTP health trên ứng dụng thực tế kết nối PostgreSQL.

Sau khi Issue #3 hoàn thành, cung cấp đủ biến `DB_*`, chạy `bootRun`, kiểm
tra startup, `/api/v1/health` và `/actuator/health` với PostgreSQL thực tế.

Kiểm chứng launcher V3/V4 (2026-10-15): full JDK21/PostgreSQL17.11 TLS disposable
752 PASS, 0 FAIL/SKIP, 9 integration suites, build PASS; bao gồm 38 tests mới.
Đây chưa là cloud execution; Codex thiếu credentials/CA và chờ owner chạy launcher.
