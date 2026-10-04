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

## Phụ thuộc Issue #3 và giới hạn xác minh

Issue #3 chịu trách nhiệm Docker/PostgreSQL cho phát triển: database, tài
khoản, credentials và host/port phù hợp. Issue #1 không tạo container,
schema nghiệp vụ hoặc cơ chế bỏ qua kết nối database.

Khi môi trường Issue #3 chưa khả dụng, có thể xác minh compilation, test MVC
và đóng gói JAR; chưa xác minh tích hợp PostgreSQL, toàn bộ startup với JPA,
hoặc HTTP health trên ứng dụng thực tế kết nối PostgreSQL.

Sau khi Issue #3 hoàn thành, cung cấp đủ biến `DB_*`, chạy `bootRun`, kiểm
tra startup, `/api/v1/health` và `/actuator/health` với PostgreSQL thực tế.
