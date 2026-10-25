# Hướng dẫn demo DX-OS — OLP 2026

> **Hai chế độ, hai loại bằng chứng:** (A) Portal React **MOCK/DEVELOPMENT** trong [Draft PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51); (B) Backend IT Helpdesk + real Keycloak/PostgreSQL **E2E CANDIDATE** trong [Draft PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63). Không trình bày (A) như đã xác nhận (B). Các PR này chưa được merge vào `main` tại checkpoint 2026-10-25.

## 1. Mục tiêu buổi demo

Giải thích DX-OS dùng các capability tái sử dụng (Identity, Gateway, Workflow, Data) cho quản lý yêu cầu nội bộ. Scenario thực tế gần nhất để nghiệm thu là **IT Helpdesk**: nhân viên gửi phiếu, người điều phối phân công, nhân viên xử lý, người gửi xác nhận; toàn bộ lịch sử/phiên bản phải nhất quán. Scenario **Facility** là kịch bản UI mock để chứng minh khả năng dùng chung thành phần Portal, **chưa có evidence Backend E2E tương đương**.

Phân biệt:
- **Core:** cơ chế chung, không định nghĩa quy tắc IT/Facility.
- **Application:** quyết định role/eligibility, trạng thái nghiệp vụ, điều kiện chuyển.
- **Portal:** hiển thị và gửi yêu cầu theo hợp đồng HTTP được Backend xác nhận; UI không tự cấp quyền.
- **Bằng chứng:** chỉ tính PASS nếu có exact SHA, test environment và kết quả test không bị skip.

## 2. Demo A: Portal mock có thể chạy độc lập

Lấy branch PR #51 và khởi động như [installation guide](installation.md#3-chạy-thử-portal-của-ánh--chỉ-mockdevelopment). Theo [frontend/DEMO_GUIDE.md tại PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/DEMO_GUIDE.md):

1. Mở `/session`, đăng nhập **phiên demo** bằng fixture nhân viên (không phải SSO).
2. Mở Dashboard, Requests và New Request; thử form trống để thể hiện validation; tạo một phiếu **Hỗ trợ CNTT**.
3. Đổi qua vai trò **người xử lý giả lập**, mở phiếu, duyệt/bắt đầu/hoàn thành; trở về vai trò người gửi để xác nhận.
4. Hiển thị Timeline, tìm kiếm/filter, pagination, empty/loading, network failure, 401/403/404 giả lập và lỗi `COMMIT_OUTCOME_UNKNOWN` theo hướng dẫn của Ánh.
5. Tạo và quan sát scenario **Cơ sở vật chất** bằng cùng thành phần giao diện; không suy ra Backend Workflow đã dùng chung thật.
6. Vào `/integration`, trình bày trạng thái chặn LIVE. **Không** khoe hành vi mock như bằng chứng thực thi ACL, JWT, transaction hoặc provider.

Mọi hình ảnh từ nhánh Portal phải ghi nguồn `docs/evidence/issue39-portal/` và HEAD của PR #51. Không sử dụng fixtures nhân sự làm thông tin người thật.

## 3. Demo B: IT Helpdesk Backend candidate — chỉ sau khi B06 được kiểm chứng

Luồng **candidate** trong [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) gồm **9 nhóm API**, dùng `/api/v1/mvp/requests`:

| Giai đoạn | HTTP | Actor hợp lệ | Kết quả mong đợi |
| --- | --- | --- | --- |
| 1. Create | `POST /` | Requester | `SUBMITTED`, `requesterId` canonical |
| 2. List | `GET /` | Người có quyền xem | `{items,nextCursor}` theo ACL |
| 3. Detail | `GET /{id}` | Người có quyền xem | `state`, phiên bản, `allowedActions` |
| 4. Triage | `POST /{id}/triage` | Coordinator | `TRIAGED` |
| 5. Assign | `POST /{id}/assign` | Coordinator | `assigneeId`, `assignmentVersion` tăng |
| 6. Start | `POST /{id}/start` | Assignee | `IN_PROGRESS` |
| 7. Resolve | `POST /{id}/resolve` | Assignee | `RESOLVED` |
| 8. Confirm | `POST /{id}/confirm` | Requester | `CONFIRMED` |
| 9. History | `GET /{id}/history` | Người có quyền xem | Bốn Workflow transitions, version nhất quán |

**Không** thêm bước `CLOSED` như đã chạy Backend: đây là trạng thái cuối **CONFIRMED** của candidate IT Helpdesk hiện có. Không đưa phiên bản endpoint `/api/v1/requests` cũ của Request prototype thành hợp đồng Helpdesk mới. Không tự tạo `displayName`/actor/timeline notes/attachments từ Frontend.

Phân quyền cần có negative evidence: anonymous/tampered token `401`; denied action `403`; hidden/cross-tenant request `404`; stale assignment/Workflow version `409`; worker bị revoke không còn được thực hiện việc của phiếu; unknown commit **không auto-retry**. Chỉ nói **PASS** nếu test có báo cáo thực tế tại HEAD tương ứng.

**B06** kiểm tra token Keycloak thực, JWT issuer+subject canonical, 9 route HTTP và PostgreSQL disposable. Script candidate và hướng dẫn nằm tại [B06 run guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/docs/evidence/issue53-b06-real-keycloak-helpdesk-e2e.md). Chưa có bằng chứng độc lập B06 PASS tại thời điểm viết.

## 4. Khi nào trình diễn Portal kết nối thật?

Chỉ sau khi nhóm xác nhận bằng chứng trên cùng **integration HEAD**:

- Yến: B05/B06 focused tests và xác minh [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58) SQL/rules/provisioning/runtime privilege; review các negative authorization cases.
- Ánh: Portal HTTP Adapter/APP-11 sử dụng DTO thật, phân biệt `version` và `assignmentVersion`, giữ `eligibleAssignees` chỉ cho coordinator có quyền, không giả danh actor.
- Bảo: đối chiếu kết quả giữa nhánh và bằng chứng, bảo toàn dữ liệu/Workflow history; kiểm tra script cài đặt, không dùng staging hoặc LIVE khi chưa được duyệt.
- Compliance/giấy phép: đánh giá riêng [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38); không tuyên bố đã vượt cổng bản quyền khi chưa có review.

**Trạng thái hiện tại: `DRAFT / LIVE_BLOCKED`.** Không gắn chữ “hoàn thành E2E”, “production-ready” hoặc “released” lên README/video trước khi có evidence và sign-off.

## 5. Mẫu biên bản nghiệm thu / cập nhật sau B06

| Trường | Ghi kết quả có chứng cứ |
| --- | --- |
| PR / integration HEAD SHA | Chưa nghiệm thu |
| Ngày giờ, người vận hành, OS, JDK, PG, Keycloak | Chưa ghi |
| Test command và môi trường disposable | Chưa ghi |
| JUnit B05/B06 PASS / FAIL / SKIP | B06 chưa được xác nhận |
| Golden Path và các negative tests | Chưa nghiệm thu E2E |
| Portal HTTP integration / screenshot thật | Chưa nghiệm thu |
| Privilege audit / provisioning / migration gate | Chưa nghiệm thu |
| Quyết định của reviewer | LIVE_BLOCKED |

Điền bằng chứng thật, link PR comment/CI log đã che secrets, người review và exact SHA vào bảng này sau nghiệm thu; không điền số dự đoán.
