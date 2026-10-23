# API chưa hoạt động

## Workflow Core HTTP — BLOCKED (#22, #23)

`/api/v1/workflow-instances/**` chỉ có adapter trong source để kiểm thử độc lập.
Spring Boot không đăng ký controller này; các đường dẫn không xuất hiện trong
`/v3/api-docs` và trả 404. Cần tích hợp verified principal/authorization của
Identity #24 và wiring đã review trước khi có thể công bố HTTP API. Không dùng
header `X-User` hoặc `X-Role` làm bằng chứng xác thực.

## Developer Portal — PLANNED (#37)

Giao diện React/Next.js riêng để duyệt OpenAPI và capability là giai đoạn sau;
Swagger UI local/demo là giao diện tài liệu hiện tại.
