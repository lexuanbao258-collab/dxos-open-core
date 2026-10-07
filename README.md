# TRICORE DX-OS Open-Core

## Vấn đề sản phẩm

**DX-OS Internal Service Portal** là định hướng sản phẩm của TRICORE: tập trung
các yêu cầu dịch vụ nội bộ công ty hiện được xử lý qua giấy, email, Zalo/Messenger/chat,
điện thoại, bảng tính hoặc trao đổi miệng. Những kênh phân tán khiến việc theo dõi
người phụ trách, tiến độ và lịch sử xử lý khó thống nhất.

Portal hướng tới tập trung việc gửi yêu cầu, phân loại, giao việc, phê duyệt/xử lý
workflow, theo dõi trạng thái, lưu hồ sơ và lịch sử xử lý. Đây là định hướng của
TRICORE, không phải diễn giải yêu cầu chính thức của cuộc thi OLP PMNM 2026.

**DX-OS không phải hệ điều hành như Windows/Linux.** Đây là nền tảng dịch vụ số
chung, cung cấp các capability tái sử dụng cho ứng dụng chuyển đổi số.
Application sở hữu nghiệp vụ; Core cung cấp cơ chế chung.

## Kiến trúc Core — TEAM ARCHITECTURE BASELINE

Repository triển khai **Spring Boot modular monolith** với bốn capability:
Identity / SSO, API Gateway, Data Management và Workflow. Module logic không
đồng nghĩa microservice; topology riêng cho Gateway chưa được chốt.
Diagram dưới đây là hướng phụ thuộc mục tiêu, không khẳng định toàn bộ luồng
đã tích hợp hoặc các thành phần được triển khai thành service riêng.

```text
External / H-P-D-I Application
             |
             v
         API Gateway
          /      \
         v        v
   Identity      Workflow
                   |
                   v
        WorkflowPersistencePort
                   |
                   v
          Data / Persistence
```

| Capability | Trách nhiệm |
| --- | --- |
| Identity / SSO | Authentication, identity resolution, authority information và authentication/security context tin cậy |
| API Gateway | External entry point, routing, xử lý public/protected routes, phối hợp Identity, truyền trusted context và chuẩn hóa lỗi tại boundary |
| Workflow | Definition/instance tái sử dụng, transition bằng transitionId, exact definition version binding, runtime versioning, optimistic concurrency, history và bảo vệ terminal state |
| Data Management | Boundary persistence dữ liệu có cấu trúc, boundary object storage tương lai, provider adapters và triển khai persistence cho port do Core capability sở hữu |

Gateway không sở hữu persistence hay ngữ nghĩa workflow nghiệp vụ. Workflow
không gọi Identity trực tiếp tại runtime. `WorkflowPersistencePort` thuộc Workflow;
Data có thể triển khai adapter sau này nhưng không định nghĩa lại Workflow semantics
hoặc phụ thuộc ngược lên application/consumer. Xem [baseline kiến trúc](docs/architecture/README.md).

Request và workflow IT Support trong `com.tricore.dxos.request` hiện là
application/business prototype, có API và persistence riêng. Generic Workflow
Core đã triển khai riêng; Request chưa được chuyển sang Core mới.

## Trạng thái — VERIFIED NOW / công việc đang mở

Trạng thái GitHub được kiểm tra ngày **2026-10-07**, tại main `2b51a41`.

**DONE / CURRENT:** repository/package baseline đã merge qua
[PR #11](https://github.com/lexuanbao258-collab/dxos-open-core/pull/11);
[Workflow Core contracts #13](https://github.com/lexuanbao258-collab/dxos-open-core/issues/13)
đã đóng và merge qua [PR #15](https://github.com/lexuanbao258-collab/dxos-open-core/pull/15).
Workflow được kiểm chứng bằng unit/contract tests độc lập provider, gồm hai
lifecycle khác nhau. Lần chạy lại Session 1 đạt **29 Workflow tests**, **109 Request
tests**, **139 tests toàn backend** và build thành công. Lệnh, môi trường và giới
hạn kiểm chứng nằm trong [Workflow POC](docs/poc/workflow-core-poc.md).

**IN PROGRESS:** [Identity Core contracts #12](https://github.com/lexuanbao258-collab/dxos-open-core/issues/12)
và [Data Core boundaries #14](https://github.com/lexuanbao258-collab/dxos-open-core/issues/14)
đã giao, còn OPEN. Trên main, hai package hiện chỉ có skeleton; chưa có PR mở
tại thời điểm kiểm tra. Trạng thái này không xác nhận tiến độ trên máy cá nhân.

**Giới hạn kiểm chứng:** test fake/mock chưa chứng minh transaction PostgreSQL,
startup với database thật, tích hợp Gateway/Identity hoặc toàn bộ portal.
Xem [evidence Session 1](docs/evidence/session-1-homework.md) và
[contract Workflow](docs/contracts/workflow-core.md).

## Công nghệ triển khai hiện tại

Baseline: **Java 21**, **Spring Boot 3.5.16**, **Gradle Wrapper 8.14.3**,
**PostgreSQL** và **Flyway**. Các công nghệ triển khai/POC này không tự động
đóng băng contract Core độc lập provider.

Chưa có quyết định chọn Keycloak, APISIX, MinIO, n8n hoặc Flowable trong
tài liệu/quyết định hiện tại được kiểm tra. Provider POC/selection là việc sau.

## Cấu trúc

```text
backend/              Spring Boot; package root com.tricore.dxos
docs/architecture/    Quyết định kiến trúc và hướng phụ thuộc
docs/contracts/       Contract Core và API
docs/poc/             Phạm vi và kết quả POC
docs/evidence/        Bằng chứng kiểm tra và checkpoint Git
infra/                Cấu hình hạ tầng khi được phê duyệt
scripts/              Script hỗ trợ khi cần
.env.example          Template biến môi trường, không có credentials
```

Dưới package root, `core.{identity,workflow,data}`, `application`,
`infrastructure.{identity,persistence,storage}` và `common` tạo ranh giới logic.
`core.workflow` có contracts/domain mechanisms và tests; `core.identity`,
`core.data`, `application` và các package infrastructure hiện chủ yếu là skeleton.
`common` giữ health/error hiện có. Folder baseline không đồng nghĩa capability
đã được tích hợp đầy đủ.

## Ownership

| Thành viên | Trách nhiệm / Issue | Commit checkpoints |
| --- | --- | --- |
| Phan Hải Yến | Identity / SSO — [#12](https://github.com/lexuanbao258-collab/dxos-open-core/issues/12), OPEN | Principal/context → authority/errors → contract tests/trust assumptions |
| Lê Xuân Bảo | API Gateway + Workflow — [#13](https://github.com/lexuanbao258-collab/dxos-open-core/issues/13), CLOSED | Models `d6d6b8b` → persistence port `56f5837` → tests/docs `971f6dd`; merged PR #15 |
| Phan Ngọc Ánh | Data / Database / Storage — [#14](https://github.com/lexuanbao258-collab/dxos-open-core/issues/14), OPEN | Storage/metadata → errors/tests → adapter ownership/migration constraints |

Mỗi task có Issue, nhánh ngắn hạn, các commit có ý nghĩa và PR về main;
xóa nhánh sau merge. Kế hoạch đầy đủ nằm trong từng Issue. Bài tập Session 1
được theo dõi tại [Issue #16](https://github.com/lexuanbao258-collab/dxos-open-core/issues/16).

## Phát triển local

Dùng **JDK 21** với `JAVA_HOME` trỏ đúng JDK; không cần cài Gradle riêng.
Lần đầu cần Internet để Wrapper tải Gradle/dependencies.
Từ repository root, trên PowerShell:

```powershell
Set-Location backend
java -version
.\gradlew.bat --version
.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*'
.\gradlew.bat test --tests 'com.tricore.dxos.request.*'
.\gradlew.bat test
.\gradlew.bat build
```

Tests hiện dùng unit, mock repository/transaction manager và MVC slices;
không cần PostgreSQL. Chạy toàn bộ ứng dụng cần PostgreSQL và đủ biến `DB_*`
trong process. Spring Boot hiện **không tự động nạp `.env`**; công cụ local
hoặc môi trường bên ngoài phải cung cấp các biến này. Không commit `.env`.
Flyway quản lý migration; Hibernate giữ `ddl-auto: none`.

Khi có PostgreSQL khả dụng, cung cấp đủ biến trong process:

| Biến | Giá trị do môi trường cung cấp |
| --- | --- |
| DB_HOST | Host PostgreSQL |
| DB_PORT | Cổng PostgreSQL |
| DB_NAME | Database đã tạo |
| DB_USER | Tài khoản được cấp |
| DB_PASSWORD | Mật khẩu của tài khoản đó |

Không có credentials mặc định. Ví dụ Windows PowerShell, từ `backend/`:

```powershell
$env:DB_HOST = Read-Host 'DB_HOST'
$env:DB_PORT = Read-Host 'DB_PORT'
$env:DB_NAME = Read-Host 'DB_NAME'
$env:DB_USER = Read-Host 'DB_USER'
$dbCredential = Get-Credential -UserName $env:DB_USER -Message 'PostgreSQL credentials'
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
.\gradlew.bat bootRun
```

Backend dự kiến kết nối PostgreSQL, chạy Flyway trước JPA rồi phục vụ API.
Đây là hướng dẫn startup khi database sẵn sàng, **chưa được chạy xác minh trong
POC Session 1**. Sau startup, từ cửa sổ khác có thể kiểm tra
`Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/health'`.
Health API trả `{"status":"UP"}` theo MVC test, không chứng minh database khả dụng.
Executable JAR nằm tại `backend/build/libs/dxos-backend-0.0.1-SNAPSHOT.jar`.

Xem [backend README](backend/README.md) cho JDK setup, cấu hình local, JAR startup,
Request APIs và giới hạn database validation; [evidence baseline](docs/evidence/README.md)
giữ nguyên các checkpoint lịch sử.

## MVP roadmap — PLANNED / DEFERRED

Vertical slice mục tiêu:
**Login / Identity → API Gateway → Create Internal Request → Workflow Processing
→ Persist Data → H-P-D-I Portal hiển thị trạng thái/kết quả.**

MVP cần thể hiện một luồng tích hợp end-to-end. Hiện các contracts/prototype
mới là nền để nối luồng; chưa có bằng chứng portal tích hợp chạy hoàn chỉnh.

| NEXT | Dependency / kết quả mong đợi |
| --- | --- |
| Gateway trusted-context integration | Chờ Identity contracts được review/merge; entry point và context tin cậy |
| Workflow runtime/application orchestration | Dùng Workflow contracts đã merge; application sở hữu nghiệp vụ |
| Real Workflow persistence adapter | Data thực hiện WorkflowPersistencePort; cần POC PostgreSQL atomicity/concurrency |
| Identity provider POC/integration | Dựa trên Identity contracts và quyết định có bằng chứng; chưa chọn provider |
| Internal Service Portal vertical slice | Nối login, Gateway, Request, Workflow và Data; UI hiển thị status/history/result |

Mục tiêu Session 1 là tài liệu kiến trúc và bằng chứng đã nghiên cứu/chạy thử
một capability. Workflow POC xác minh mô hình/contract generic hiện có, không
xác minh engine bên thứ ba hay thay đổi topology.
