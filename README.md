# TRICORE DX-OS Open-Core

Repository hiện là baseline triển khai **Spring Boot modular monolith**.
Core có bốn capability bắt buộc: **Identity / SSO**, **API Gateway**,
**Data Management** và **Workflow**. Task baseline mới chuẩn bị ranh giới package;
các capability Core chưa được triển khai đầy đủ.

Request và workflow IT Support hiện nằm trong `com.tricore.dxos.request`,
là application prototype/use case, chưa phải Workflow Core tái sử dụng.
Module này được giữ nguyên trong task baseline.

Stack triển khai/POC hiện tại: **Java 21**, **Spring Boot**, **Gradle Wrapper**,
**PostgreSQL** và **Flyway**. Các lựa chọn này không tự động chốt provider cho
contract Core; contract phải độc lập provider. Module logic không đồng nghĩa
microservice; topology triển khai Gateway chưa được chốt.

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
Skeleton mới chỉ chứa `package-info.java`; `common` giữ health/error hiện có.
Xem [hướng phụ thuộc](docs/architecture/README.md).

## Ownership

| Thành viên | Trách nhiệm |
| --- | --- |
| Phan Hải Yến | Identity / SSO |
| Lê Xuân Bảo | API Gateway + Workflow Core |
| Phan Ngọc Ánh | Data Management / Database / Storage |

## Phát triển local

Dùng JDK 21. Từ `backend/`, trên Windows chạy:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

Tests hiện dùng unit, mock repository/transaction manager và MVC slices;
không cần PostgreSQL. Chạy toàn bộ ứng dụng cần PostgreSQL và đủ biến `DB_*`
trong process. Spring Boot hiện **không tự động nạp `.env`**; công cụ local
hoặc môi trường bên ngoài phải cung cấp các biến này. Không commit `.env`.
Flyway quản lý migration; Hibernate giữ `ddl-auto: none`.

Xem [backend README](backend/README.md) để cấu hình local và dùng API hiện có,
và [evidence](docs/evidence/README.md) để xem kết quả xác minh baseline.
