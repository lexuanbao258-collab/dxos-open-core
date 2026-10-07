# Architecture

Nơi lưu các quyết định và mô tả kiến trúc TRICORE. Baseline hiện tại là
Spring Boot modular monolith; module logic không đồng nghĩa microservice.

Core có bốn capability bắt buộc: Identity / SSO, API Gateway, Data Management
và Workflow. Skeleton chưa triển khai các capability này.

Hướng phụ thuộc mục tiêu:

- External/Application → Gateway → Identity.
- Gateway → Workflow → WorkflowPersistencePort → Data / persistence adapter.
- Workflow không phụ thuộc runtime trực tiếp vào Identity.
- Gateway không đảm nhiệm persistence; Data không phụ thuộc ngược lên consumer Workflow.
- Contract Core độc lập provider; nghiệp vụ Request thuộc application prototype.

Topology triển khai Gateway chưa được chốt; task này không tạo service riêng.
