# DX-OS Open-Core backend

Nền backend cho Internal Service Portal của TRICORE, theo kiến trúc Modular
Monolith. Base package: `com.tricore.dxos`. Core cung cấp cơ chế; application
sở hữu ngữ nghĩa nghiệp vụ. Issue #1 chỉ tạo nền dự án và health API.

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

## Phụ thuộc Issue #3 và giới hạn xác minh

Issue #3 chịu trách nhiệm Docker/PostgreSQL cho phát triển: database, tài
khoản, credentials và host/port phù hợp. Issue #1 không tạo container,
schema nghiệp vụ hoặc cơ chế bỏ qua kết nối database.

Khi môi trường Issue #3 chưa khả dụng, có thể xác minh compilation, test MVC
và đóng gói JAR; chưa xác minh tích hợp PostgreSQL, toàn bộ startup với JPA,
hoặc HTTP health trên ứng dụng thực tế kết nối PostgreSQL.

Sau khi Issue #3 hoàn thành, cung cấp đủ biến `DB_*`, chạy `bootRun`, kiểm
tra startup, `/api/v1/health` và `/actuator/health` với PostgreSQL thực tế.
