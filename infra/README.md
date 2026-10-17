# Infrastructure

Cấu hình hạ tầng tách khỏi DX-OS Core; không mặc định triển khai
provider hoặc thay đổi shared Supabase.

## OLP 2026 — Draft deployment scaffold (#40)

- [`compose.staging.yml`](compose.staging.yml): **mặc định chỉ khởi động PostgreSQL 17.11** trên loopback và volume riêng. Service Backend dùng profile `app`, không tự khởi động.
- [`staging.env.example`](staging.env.example): mẫu biến môi trường không chứa bí mật. Copy thành `infra/.env.staging` (gitignored).
- [Hướng dẫn triển khai và các bước bị chặn](../docs/deployment/aws-staging.md): yêu cầu khởi tạo migration được phê duyệt, runtime roles, Identity/Gateway ACL, Storage PR #43 và kiểm thử.
- [`compose.workflow-postgres.yml`](compose.workflow-postgres.yml): giữ nguyên bộ PostgreSQL dành riêng cho Workflow tests hiện có.

**Không chạy `docker compose --profile app up` trên EC2 AWS `t3.micro` 1 GiB RAM hiện tại**, không mở Postgres/Workflow HTTP public, không tự động migrate database chung.
