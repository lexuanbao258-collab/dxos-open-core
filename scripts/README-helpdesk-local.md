# DX-OS MVP: PostgreSQL local

Chỉ dành cho demo local, chưa bật LIVE. Script tạo **container + named volume mới**,
không sửa/xóa database hay container có sẵn. Mỗi `RunId` chỉ setup một lần;
restart dùng container cũ, không chạy lại setup. Nếu setup lỗi, giữ tài nguyên để kiểm tra;
chọn RunId mới để thử lại sau khi xác định lỗi.

## Prerequisites và credentials

Windows PowerShell 5.1 hoặc PowerShell 7, Docker Desktop Linux Engine local
(`desktop-linux` qua named pipe), JDK 21; cổng PostgreSQL 55440 và HTTP 8080 trống.
Từ thư mục gốc repo, tải image nếu chưa có:

```powershell
docker --context desktop-linux pull postgres:17.11-alpine
$run = 'demo_20261027' # đổi cho mỗi lượt demo mới
$db = 'helpdesk_disposable_' + $run
$container = 'dxos-helpdesk-' + $run
$volume = $container + '-data'
```

`HELPDESK_SETUP_PASSWORD` và `HELPDESK_RUNTIME_PASSWORD`: hai secret khác nhau,
ít nhất 24 ký tự, không có control characters. Script hỏi ẩn nếu chưa đặt trong
process environment. Không gõ password trực tiếp vào command/history, ghi `.env`,
Git, transcript hoặc log. Có thể lấy từ secret manager vào process environment.
Để dùng cùng runtime secret khi khởi động Backend, nhập qua prompt ẩn:

```powershell
$credential = [PSCredential]::new('runtime', (Read-Host 'Runtime password' -AsSecureString))
$env:HELPDESK_RUNTIME_PASSWORD = $credential.GetNetworkCredential().Password
$credential = $null
```

Setup login `helpdesk_setup` chỉ bootstrap database riêng. Schema/tables/functions
thuộc role `helpdesk_owner_<RunId>` NOLOGIN. Backend chỉ dùng
`helpdesk_runtime_<RunId>`: NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION,
NOBYPASSRLS, không role membership, không sở hữu schema/table, không CREATE/TEMP.
PUBLIC không được quyền trên database/schema/tables/functions của demo.
Docker administrators vẫn có thể đọc environment của container; không chia sẻ
`docker inspect` đầy đủ. Script không xuất password hoặc SQL chứa password.

## Preflight và setup

```powershell
.\scripts\helpdesk-local-setup.ps1 -RunId $run
.\scripts\helpdesk-local-setup.ps1 -RunId $run -Execute -ConfirmDemoDatabase $db
```

`-Port` mặc định 55440, host cố định 127.0.0.1; `-Tenant` mặc định `demo`.
Preflight chỉ đọc, không hỏi secret hay tạo tài nguyên. Setup từ chối tên container,
volume hoặc cổng đã tồn tại. Không nhận database/endpoint cloud hay tùy chọn xóa.

SQL dùng từ `backend/src/main/resources/`: Workflow V3, `db/helpdesk/policy-schema.sql`
(bản physical schema V5 độc lập test fixture, chỉ private invoker integrity triggers),
`schema.sql`, `definition.sql`, `runtime-grants.sql`. Không chạy Flyway V1/V2/V4,
`application.sql` cũ hoặc Stage 6 guarded SQL entrypoints. Toàn bộ DDL, definition,
5 states, 4 transitions, tenant, pinned binding `mvp_request -> it-helpdesk@1` và
grants được commit cùng transaction; sealing V3 giữ nguyên. Setup tự kiểm tra
runtime TCP login, inventory, exact DML/column grants và seed sau commit.

Để seed 3 membership/actor, truyền **issuer nguyên văn và `sub` thật** lấy từ
Keycloak/directory đã tin cậy. Phải cung cấp đủ cả ba tài khoản khác nhau:

```powershell
$identity = @{
    KeycloakIssuer = $env:OIDC_ISSUER_URI
    EmployeeSubject = $env:HELPDESK_EMPLOYEE_SUB
    CoordinatorSubject = $env:HELPDESK_COORDINATOR_SUB
    WorkerSubject = $env:HELPDESK_WORKER_SUB
}
.\scripts\helpdesk-local-setup.ps1 -RunId $run -Execute -ConfirmDemoDatabase $db @identity
```

Actor ID được tính đúng như Backend: `oidc:base64url(UTF8(iss)).base64url(UTF8(sub))`,
không padding. Omit toàn bộ identity arguments để chỉ seed tenant/binding, actor và
membership để trống; khi đó chưa thể chạy authenticated demo. Không mặc định tạo
identity giả. Subject khai báo lúc provision **không phải** bằng chứng xác thực.
Keycloak cần issuer/audience/client và client roles đúng contract hiện có:
employee `workflow:create/activate/read/transition` (cần transition để confirm),
coordinator và worker `workflow:read/transition`.
B06 vẫn **NOT VERIFIED** tới khi chạy test với access tokens thật theo runbook B06.

## Start Backend và kiểm tra

Trong PowerShell có JDK 21 và runtime secret (không dùng setup password):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$url = "jdbc:postgresql://127.0.0.1:55440/$db"
$env:SPRING_DATASOURCE_URL = $url + '?currentSchema=helpdesk_demo,pg_catalog,pg_temp'
$env:SPRING_DATASOURCE_USERNAME = 'helpdesk_runtime_' + $run
$env:SPRING_DATASOURCE_PASSWORD = $env:HELPDESK_RUNTIME_PASSWORD
$env:SPRING_FLYWAY_ENABLED = 'false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'none'
$env:DXOS_WORKFLOW_SCHEMA = 'helpdesk_demo'
$env:DXOS_HELPDESK_ENABLED = 'true'
$env:DXOS_HELPDESK_JDBCURL = $url
$env:DXOS_HELPDESK_USERNAME = $env:SPRING_DATASOURCE_USERNAME
$env:DXOS_HELPDESK_PASSWORD = $env:HELPDESK_RUNTIME_PASSWORD
$env:DXOS_HELPDESK_SCHEMA = 'helpdesk_demo'
$env:DXOS_HELPDESK_TENANT = 'demo'
$env:WORKFLOW_EVENT_GUARD_LOCAL_DISPOSABLE = 'true'
$env:SERVER_ADDRESS = '127.0.0.1'
$env:SERVER_PORT = '8080'
# Đặt OIDC_ISSUER_URI đúng Keycloak thật; không dùng mock làm bằng chứng B06.
$env:OIDC_AUDIENCE = 'tricore-api'
$env:OIDC_RESOURCE_CLIENT_ID = 'tricore-api'
Set-Location backend
.\gradlew.bat --no-daemon bootRun
```

Nếu dùng `-Port`/`-Tenant` khác, đổi URL/tenant tương ứng. Trên terminal khác:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/v1/health # UP xác nhận HTTP; không thay cho DB check
# Không có bearer token: route bảo vệ phải trả 401.
curl.exe -s -o NUL -w '%{http_code}' http://127.0.0.1:8080/api/v1/mvp/requests
# psql -W hỏi runtime password ẩn, dùng TCP có xác thực.
docker --context desktop-linux exec -it $container psql -X -W -h 127.0.0.1 -U ('helpdesk_runtime_' + $run) -d $db -c "SELECT current_user,rolsuper FROM pg_roles WHERE rolname=current_user; SELECT count(*) FROM helpdesk_demo.workflow_definition_states;"
```

## Restart và cleanup

Dừng Backend bằng Ctrl+C. `docker --context desktop-linux restart $container`,
chờ `pg_isready`, rồi start Backend với cùng environment. Named volume giữ schema,
tenant, definition và dữ liệu requests. Không chạy setup lại, không `down -v`.

Chỉ cleanup khi không cần dữ liệu của **chính lượt demo này** nữa. Kiểm tra tên,
label và mount trước khi xóa; không chạy với container/volume khác:

```powershell
$item = (docker --context desktop-linux inspect $container | ConvertFrom-Json)[0]
$vol = (docker --context desktop-linux volume inspect $volume | ConvertFrom-Json)[0]
if ($LASTEXITCODE -ne 0 -or $item.Name -cne ('/' + $container) -or
    $item.Config.Labels.'dxos.helpdesk.demo' -cne $run -or
    $vol.Labels.'dxos.helpdesk.demo' -cne $run -or
    @($item.Mounts | Where-Object { $_.Name -ceq $volume -and $_.Destination -ceq '/var/lib/postgresql/data' }).Count -ne 1) { throw 'Ownership mismatch' }
docker --context desktop-linux stop $container
if ($LASTEXITCODE -ne 0) { throw 'Stop failed' }
docker --context desktop-linux rm $item.Id
if ($LASTEXITCODE -ne 0) { throw 'Remove failed; do not delete volume' }
docker --context desktop-linux volume rm $volume
Remove-Item Env:HELPDESK_SETUP_PASSWORD,Env:HELPDESK_RUNTIME_PASSWORD,Env:SPRING_DATASOURCE_PASSWORD,Env:DXOS_HELPDESK_PASSWORD -ErrorAction SilentlyContinue
```
