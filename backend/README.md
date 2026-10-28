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
có trước khi boot; xem phần **Chạy backend local từ clone mới** bên dưới.
Dùng runtime DB role không phải superuser/owner, với quyền tối thiểu cần thiết.

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

Cách kiểm tra đầy đủ từ source, không cần dịch vụ database bên ngoài hoặc credentials nhóm:

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
Chỉ chạy các suite này trên database disposable riêng. Không có database thì task test skip
integration, còn workflowIntegrationTest fail vì thiếu biến môi trường.

### Chạy backend local từ clone mới

Các lệnh dưới đây chỉ dùng database loopback do Compose tạo. Không dùng tài khoản
hoặc endpoint ngoài loopback. Mỗi volume mới chỉ provision role/schema một lần; khi dùng
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

### Active local PostgreSQL and Storage setup

The OLP MVP uses **PostgreSQL 17 on an isolated local or private network** through the standard JDBC driver. Use the instructions above (including `scripts/workflow-verify-local.ps1` for disposable integration checks) and keep the migration/runtime roles separate. The database is **not** an external hosted-service dependency.

The target object provider is **Local FileSystem Storage** behind `ObjectStoragePort`, using a private persistent volume with OS permissions, quotas, backup/recovery and application-level authorization. [Draft PR #43](https://github.com/lexuanbao258-collab/dxos-open-core/pull/43) contains the implementation candidate; this file does not claim it is already on `main`.

Do not run historical remote migration/approval launchers against any database. Existing Flyway V1–V4 bytes/checksums and `COMMIT_OUTCOME_UNKNOWN` handling must remain unchanged. Application authorization belongs to verified Keycloak identity + trusted Backend/Gateway checks, with local PostgreSQL runtime privileges limited to persistence.

**Integration status:** nine-route Helpdesk, Local FileSystem and Portal/BFF real-identity E2E require exact-SHA tests/review before LIVE; no production deployment or remote database mutation is authorized.

---

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
Đây là bằng chứng local trên phiên bản được ghi nhận, không phải xác nhận end-to-end hiện tại.
