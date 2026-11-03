# DX-OS OPEN-CORE — API GATEWAY CORE V2

**Architecture Design, Decision Review, Implementation Plan & Acceptance Specification**
**Dự án:** TRICORE / Olympic Phần mềm nguồn mở 2026
**Ngày:** 28/10/2026
**Trạng thái tài liệu:** PROPOSED — chờ nhóm phê duyệt; không phải mô tả tính năng đã triển khai xong
**Phạm vi:** GW-ADR-01 đến GW-ADR-06; React Portal, BFF, Backend Gateway HTTP, Authorized Application boundary, Identity/Workflow/Data integration.
**Git baseline:** `main=e06fa429cfd92b032c166eb3ebf32803d13191c2`; PR #63 `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`; PR #70 `d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27`; PR #71 `c73bbc306a698141169304e5da848ea0686700db`.
**Cảnh báo:** Đối chiếu source và PR; chưa kiểm thử độc lập toàn bộ E2E trên cùng release SHA. Không đánh dấu PASS nếu chưa chạy và lưu bằng chứng.

---

## 1. Mục tiêu và phạm vi

DX-OS có bốn năng lực logic: **Identity, Workflow, Data, API Gateway**. API Gateway là ranh giới giao tiếp dùng chung; không đồng nghĩa một microservice hoặc một sản phẩm gateway thương mại. Mục tiêu: cho phép IT Helpdesk và Facility tiêu thụ cùng khả năng xác thực, hợp đồng lỗi, quy tắc công khai route và tầng transport mà không sao chép cơ chế nền tảng. Công nghệ có thể thay ở ranh giới adapter *nếu* tuân thủ semantic contract và acceptance tests.

**Mục tiêu kiểm chứng (G):** G1 xác thực trusted principal; G2 bảo vệ resource ở Application; G3 public API có version, schema và lỗi ổn định; G4 mặc định tắt route không được duyệt; G5 Browser không giữ OAuth bearer token khi dùng BFF; G6 thêm Facility không cần thay mã nguồn shared security/transport; G7 cơ chế retries và uncertain commit an toàn; G8 clean install/E2E có thể tái hiện.

**Không nằm trong MVP:** thiết kế API management platform đa tenant theo kiểu Kong, dynamic arbitrary reverse proxy, service discovery, microservices, workflow designer công khai, tự động tạo endpoint nghiệp vụ từ metadata, hoặc cam kết đổi provider không cần viết adapter/test.

### 1.1. Khái niệm cần hiểu

- **Core capability:** năng lực dùng chung và những invariant thuộc nó; không bắt buộc tương ứng với deployment service.
- **Contract:** quy định về đầu vào, đầu ra, ngữ nghĩa, lỗi, bảo mật và tương thích, không chỉ chữ ký hàm hay URL.
- **Inbound adapter:** lớp chuyển protocol bên ngoài (HTTP) thành application command; không sở hữu business rules.
- **Application/use case:** phối hợp authorization, workflow và business facts để đạt mục đích người dùng.
- **Outbound port/adapter:** interface do phía trong sở hữu và implementation tương tác database/provider.
- **API Gateway boundary:** xử lý request entry, coarse-grained checks, routing, validation, error semantics và observability.
- **BFF (Backend for Frontend):** backend riêng cho trình duyệt, sở hữu server-side session/OAuth client, CSRF, token forwarding và proxy allowlist.
- **Resource authorization:** câu hỏi actor có thể xem/thao tác *resource cụ thể* hay không; không thể suy ra chỉ từ việc JWT hợp lệ.
- **Coarse-grained authority:** quyền mức operation như workflow:transition; chưa xác định quyền trên resource.
- **Feature flag:** bật/tắt registration/behavior; phải fail-closed và không tự cấp quyền.
- **Authorized use case:** lớp thực thi đúng xác thực, policy và transaction trước khi gọi Core.
- **COMMIT_OUTCOME_UNKNOWN:** backend/adapter không thể xác nhận giao dịch đã commit hay chưa; không tự động replay write.
- **Golden path:** một hành trình người dùng xuyên qua mọi thành phần với dữ liệu thật và bằng chứng kiểm thử.

## 2. Source baseline và vấn đề đã xác minh

| Mục | Source | Phát hiện | Hệ quả kiến trúc |
|---|---|---|---|
| Workflow HTTP | `gateway/workflow/controller/WorkflowController.java` trên main | Có `@RequestMapping`, `@ResponseBody`, **không có** `@RestController`/`@Component`; chủ động không đăng ký production; gọi trực tiếp `WorkflowRuntime` | Không được “bật” chỉ bằng annotation; cần Authorized Workflow UseCase và resource-binding authorization |
| Identity handoff | `gateway/workflow/security/WorkflowGatewayPrincipalGate.java` | `CurrentPrincipal.requireVerified()` rồi kiểm operation authority; comment nói rõ không xử lý tenant/resource ACL | Đúng coarse-grained boundary, chưa đủ resource authorization |
| Backend auth | `infrastructure/identity/security/SecurityConfig.java` | Resource Server stateless JWT; health permit; routes còn lại `.authenticated()`; có issuer/audience/subject/type validation | Authentication chưa thay cho per-resource authorization; browser cookie không được dùng để bypass bearer check |
| Helpdesk API | PR #63 `gateway/helpdesk/HelpdeskController.java` | `@ConditionalOnProperty(dxos.helpdesk.enabled=true)`; import `infrastructure.workflow.HelpdeskService` và trả record của service | Gateway phụ thuộc implementation/persistence; không đạt GW-ADR-05 |
| Helpdesk persistence | PR #63 `infrastructure/workflow/HelpdeskService.java` | Application orchestration biết `helpdesk_request`, `wf_port_*` và guarded JDBC transaction | ADR Application-enforced không thể đơn giản xóa Stage 6 SQL mà vẫn bảo đảm atomicity |
| BFF | PR #70 `portal-bff/.../PortalBackendClient.java` | Hardcode `/bff/api/v1/mvp/requests` và `ACTIONS={triage,assign,start,resolve,confirm}`; allowlist, body limit, no redirect, no blind POST retry | MVP tốt; Facility cần riêng route adapter nhưng nên dùng chung transport/security |
| BFF security | PR #70 `portal-bff/.../PortalSecurity.java` | Confidential OAuth2 login+PKCE; HTTP session; CSRF enabled; `/bff/api/**` authenticated; `/bff/**` deny-all fallback | Cần HTTPS ingress, cookie, origin, CSRF và real browser evidence |
| Legacy API | `request/controller/RequestController.java`, `RequestWorkflowController.java` main | Prototype có `@RestController` và JPA state machine riêng | Demo phải ngăn legacy API exposure; tắt route ≠ xóa bảng dữ liệu |
| Portal | PR #71 | HTTP adapter và mocked tests; LIVE còn BLOCKED | Không tính mock E2E là real end-to-end |

**Nguồn:**
- https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/backend/src/main/java/com/tricore/dxos/gateway/workflow/controller/WorkflowController.java
- https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/backend/src/main/java/com/tricore/dxos/gateway/workflow/security/WorkflowGatewayPrincipalGate.java
- https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/backend/src/main/java/com/tricore/dxos/infrastructure/identity/security/SecurityConfig.java
- https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskController.java
- https://github.com/lexuanbao258-collab/dxos-open-core/blob/d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27/portal-bff/src/main/java/com/tricore/dxos/portal/PortalBackendClient.java
- https://github.com/lexuanbao258-collab/dxos-open-core/blob/d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27/portal-bff/src/main/java/com/tricore/dxos/portal/PortalSecurity.java
- https://github.com/lexuanbao258-collab/dxos-open-core/pull/63
- https://github.com/lexuanbao258-collab/dxos-open-core/pull/70
- https://github.com/lexuanbao258-collab/dxos-open-core/pull/71

## 3. Sáu Architecture Decision Records (phản biện và chốt có điều kiện)

### GW-ADR-01 — Deployment Topology: BFF + Backend Gateway Boundary

**Decision: ACCEPT WITH CONDITIONS.** Trình duyệt đi qua HTTPS ingress đến React Portal và BFF; BFF quản lý server-side OAuth2 session, PKCE, CSRF, lưu token server-side và proxy allowlisted business API. Backend tiếp tục là JWT Resource Server tự xác thực request. Backend là modular monolith với HTTP inbound adapters và Application/Core.

**Không chọn A — SPA → Backend trực tiếp:** ít tiến trình hơn nhưng chuyển trách nhiệm token handling/OAuth browser về SPA; cần thiết kế lại threat model. **Không chọn C — Dedicated API Gateway:** tốt cho nhiều upstream, traffic governance và platform operations nhưng tăng service, policy drift và demo cost. BFF không thay resource authorization của Backend.

**Quan hệ trust:** browser↔BFF: cookie/CSRF; BFF↔Backend: bearer JWT qua kết nối được kiểm soát; Backend↔Data: runtime DB credentials. Caddy/Nginx nếu có chỉ TLS/reverse proxy; không tự cung cấp resource authorization. Backend không được public trực tiếp trên mạng không kiểm soát trong bản demo. Tính riêng tư hạ tầng không thay cho JWT security.

**Exit gate:** OIDC callback HTTPS, host-only Secure HttpOnly SameSite cookie, login/logout, CSRF negative, backend trực tiếp từ chối JWT sai, cookie không chứa bearer, 401 invalidates session, 403 preserved, no redirect follow, bounded body, no blind replay. Ghi rõ single-instance session model; scale nhiều BFF instances yêu cầu sticky sessions hoặc shared session storage và là deferred.

### GW-ADR-02 — Responsibility Boundary: Gateway chỉ cross-cutting

**Decision: ACCEPT.** Gateway sở hữu route exposure, authentication enforcement, coarse authority, transport validation, size/timeouts, error mapping, request correlation, logging/redaction và API versioning. Application sở hữu tenant membership, resource visibility, assignee/requester, state-dependent eligibility và transaction coordination. Workflow Core sở hữu definition/transition/version/history invariant; Identity Core sở hữu principal/authority facts; Data ports sở hữu storage behavior.

**Anti-pattern cần tránh:** `if role == TECHNICIAN && assignee == actor` trong BFF, Gateway đọc bảng Helpdesk hoặc Gateway trực tiếp ghi SQL. Coarse authority có thể thực thi tại gateway hoặc application facade; phải bảo đảm không tạo hai truth sources mâu thuẫn. Resource authorization không được bị bỏ qua khi gọi UseCase không qua HTTP.

### GW-ADR-03 — Public APIs: Business API + restricted Generic Workflow API

**Decision: ACCEPT WITH STRICT EXPOSURE GATE.** Business APIs như `/api/v1/mvp/requests` là public business surface của MVP. Generic Workflow API `/api/v1/workflow-instances` mặc định không đăng ký. Chỉ cân nhắc bật cho trusted client sau khi implement authorized resource binding, definition scope, tenant visibility, operation policy, explicit consumer identity và audit. Không tin actor/requester/resource ownership do client tự khai.

**Rủi ro:** Generic API có thể tạo workflow instance trỏ đến resource người khác hoặc bypass Helpdesk transaction khi được mở. Public generic `transition` không tự động tương đương `resolve` vì business fact của Helpdesk cũng phải được cập nhật. Vì vậy platform APIs phải được giới hạn theo use case, không kích hoạt controller cũ nguyên trạng. Phân biệt workflow definition version với `/api/v1` protocol version.

**Alternative:** Chỉ Business API trước (ít rủi ro nhất) rồi restricted API sau; đề xuất delivery MVP theo thứ tự này, nhưng giữ ADR mục tiêu C.

### GW-ADR-04 — Authorization: Gateway coarse-grained + Application resource policy

**Decision: ACCEPT; CONSISTENT WITH APP-ENFORCED MVP.** Spring Security xác minh JWT; `CurrentPrincipal` trả verified canonical subject. Gateway có thể kiểm operation authority; Application dùng dữ liệu authoritative để xác minh tenant, ACL, visibility, assignment, requester và workflow/assignment versions.

**Deny semantics:** 401 cho thiếu/sai authentication; 403 cho thiếu quyền ở resource visible; hidden 404 khi visibility policy yêu cầu che tồn tại; 409 cho version conflict; 400/415/413 cho lỗi transport. Phải chỉ rõ thứ tự kiểm tra để lỗi không tiết lộ sự tồn tại của resource. `allowedActions` là gợi ý UI, KHÔNG phải authorization enforcement.

**Concurrency:** authorization không thể chỉ kiểm `if` trước transaction; cần row locks/conditional writes/version guards áp dụng nhất quán cả khi assign/revoke/transition. Giữ atomic business facts + workflow state/history, không coi `@Transactional` là bằng chứng đủ nếu adapter tự quản JDBC connection. Khi mất COMMIT ACK, phải reconcile authoritative state; không retry POST tự động.

**Xung đột:** PR #63 dựa vào Stage 6 guarded SQL. Chuyển application-enforced cần inventory từng function để tách SQL authorization ra khỏi SQL persistence/integrity; không được bỏ function đồng loạt.

### GW-ADR-05 — HTTP Controller → Authorized UseCase

**Decision: ACCEPT — ARCHITECTURE P0.** Thay `WorkflowController -> WorkflowRuntime` bằng `WorkflowHttpController -> AuthorizedWorkflowUseCase` khi/ nếu kích hoạt. Thay `HelpdeskController -> infrastructure.workflow.HelpdeskService` bằng `HelpdeskController -> HelpdeskUseCase` với DTO mapping tại HTTP boundary. Application service dùng verified CurrentPrincipal/verified execution context; không nhận raw user-controlled actorId làm bằng chứng danh tính.

**Lưu ý:** Interface mới không được làm mất cùng JDBC transaction dùng cho business/workflow; tránh 'refactor sạch' tạo hai commits riêng. Đặt unit-of-work/transaction coordination ở nơi phù hợp và test fault-injection. Đừng tạo public generic use case cho tất cả resource mà không có resource ownership policy.

### GW-ADR-06 — Spring Annotations + Feature Flags/Profile

**Decision: ACCEPT WITH FAIL-CLOSED RULES.** `dxos.helpdesk.enabled=true` hiện điều khiển PR #63. Legacy Request Controller trên main cần mặc định không được đăng ký ở demo. Generic Workflow Controller mặc định OFF và chỉ bật nếu authorized gateway adapter đã đủ policy. Dùng annotation registration và startup validation về mode xung đột.

**Proposed exposure modes** (thiết kế mới):

| Mode | Legacy Request | New Helpdesk | Generic Workflow |
|---|---|---|---|
| default | OFF | OFF | OFF |
| demo | OFF | ON | OFF |
| legacy-test | ON | OFF | OFF |
| platform-test | OFF | Optional | ON, only with secured facade |
| both legacy + MVP | startup FAIL | startup FAIL | N/A |

**Không đánh đồng:** Route không đăng ký (404 do thiếu mapping) với route đăng ký nhưng resource hidden (policy 404). HTTP status 404 đơn lẻ không đủ chứng minh route đã bị disable; test cần kiểm Spring RequestMapping registry.

## 4. Kiến trúc mục tiêu và các luồng

### 4.1. Logical architecture

```text
[Browser React Portal]
      |
      | HTTPS, SameSite/Secure/HttpOnly session + CSRF
      v
[Caddy HTTPS ingress] ---> [Portal BFF: OAuth client, session, per-app route adapters]
                              |
                              | trusted upstream bearer JWT (never X-User)
                              v
                   [Backend: Spring Security Resource Server]
                              |
                       [HTTP Gateway adapters]
                              |
                       [Authorized UseCases] <--- CurrentPrincipal verified
                         /         |         \
                Helpdesk       Workflow      Facility
                      \          |          /
                        [Core capabilities]
                     Identity / Workflow / Data
                              |
                   [Ports + Provider Adapters]
                              |
                      [PostgreSQL / Storage]
```

**Điểm tinh tế:** BFF và Backend Gateway đều là hai transport/security boundaries nhưng không phải hai implementation trùng lặp cho business authorization. Gateway Core là logical capability, không phải tất cả mã BFF đặt trong backend/core.

### 4.2. Login flow (Authorization Code + PKCE)

1. Browser điều hướng tới BFF login endpoint, BFF tạo state/nonce/PKCE và redirect sang Keycloak.
2. Người dùng xác thực tại Keycloak; browser quay về HTTPS callback đã pin origin.
3. BFF kiểm tra OAuth state, PKCE và tokens; tạo server-side session cookie; access token không gửi xuống JS.
4. Portal gọi `/bff/session` để lấy tình trạng session và CSRF token; chỉ lộ metadata được phê duyệt.
5. User logout: session bị invalidate, cookie xóa, xử lý redirect/logout provider theo cấu hình.
6. Khi token hết hạn hoặc backend trả 401, BFF phải fail closed/invalidate theo policy; không được nuốt 403 thành 401.

**Exception tests:** sai redirect URI, expired state, missing CSRF, session fixation, cookie sai origin, logout replay, token refresh failure.

### 4.3. Create Helpdesk flow

1. Portal gửi POST tới BFF với cookie và CSRF.
2. BFF chỉ chấp nhận Helpdesk allowlisted route/method, giới hạn request body, lấy OAuth access token đã xác minh của session.
3. BFF forward bearer tới Backend; không follow redirect và không proxy client-supplied arbitrary host/path.
4. Backend Resource Server xác minh issuer, audience, expiry, token type, canonical subject; không tin forged `X-User`/`X-Role`.
5. HTTP controller validate DTO và gọi `HelpdeskUseCase.create`.
6. Authorized application xác minh eligible requester/tenant/operation, tạo business request, binding Workflow, activate theo rule; cùng transaction bảo đảm atomic state.
7. Backend trả 201 + Location; BFF chuyển đúng status/JSON hợp lệ; UI cập nhật.
8. Timeout/lost response khi write: BFF trả `COMMIT_OUTCOME_UNKNOWN` hoặc lỗi có semantics tương đương; client không tự retry mù.

### 4.4. Transition/resolve flow

1. UI đọc resource và `workflowVersion`, `assignmentVersion` authoritative; không coi `allowedActions` là quyền thật.
2. POST `/requests/{id}/resolve` với expected workflowVersion và resolution.
3. Gateway xác thực/coarse-grained; Application xác minh visibility, assignee, tenant, permission và business state.
4. Transaction thực thi transition và business fact update; workflow version tăng đúng một, history record đúng một.
5. Concurrent resolve: tối đa một winner trên cùng expectedVersion; loser nhận conflict và không có partial history.
6. User confirmation thực thi policy requester; history sau restart vẫn nhất quán.

### 4.5. Facility reuse flow

Facility giới thiệu `FacilityProxyController` và `FacilityHttpController`, `FacilityUseCase`, domain rules riêng. Chỉ dùng chung BFF `BackendHttpClient`, verified principal, error schema, security configuration, HTTP adapter conventions, Workflow Core, Storage Port. Không sửa `PortalAuthorization`, shared BFF transport hoặc Workflow domain chỉ để onboard Facility. Nếu cần thay Core để đáp ứng quy tắc chung mới, phải ghi architectural change riêng; không giả định reuse thành công.

## 5. Contracts cần đóng băng

### 5.1. Contract matrix

| ID | Contract | Owner | Consumer | Verification |
|---|---|---|---|---|
| CT-01 | Public business OpenAPI v1 | Helpdesk Application/API | Portal BFF, UI | schema snapshot, negative HTTP |
| CT-02 | Restricted Workflow API policy | Workflow Application/API | trusted platform clients | not mapped by default, operation/resource tests |
| CT-03 | Verified principal | Identity | Gateway, Application | issuer/audience/sub/headers, actual JWT |
| CT-04 | Error envelope | Gateway/common | BFF, UI | status+code+cache controls |
| CT-05 | Authorization policy | Application | Helpdesk, Facility | tenant, ACL, requester, assignee tests |
| CT-06 | WorkflowPersistencePort semantics | Workflow Core | provider adapters | concurrency/unknown commit contract tests |
| CT-07 | BFF upstream proxy policy | BFF | Portal | route/method/body/redirect/timeout tests |
| CT-08 | Route exposure policy | Bootstrap/Gateway | Runtime/operations | mode startup/mapping tests |
| CT-09 | API compatibility | API team | all clients | versioning and consumer contract tests |

### 5.2. HTTP contract rules

- HTTPS for browser-facing origin; public login/callback and `/bff/session` behavior documented.
- Path is explicit and versioned (`/api/v1/...`); do not silently rename MVP path before adapter compatibility tests.
- Strict DTO validation (lengths, enum/requestType, version numbers; unknown fields policy documented).
- Allowlisted methods, paths and query params; no open proxy, no arbitrary backend host.
- Response status mapping: 200/201, 400, 401, 403, 404, 409, 413, 415, 422 as relevant; 5xx must not leak SQL/stacktrace; `COMMIT_OUTCOME_UNKNOWN` distinguishable.
- `Cache-Control: no-store` for session and sensitive API responses; consistent request-id propagation with sanitized logs.
- Cursor pagination consistent `items,nextCursor` and bounded limits.
- `Authorization`/Cookie/CSRF headers are transport-only; no copying raw tokens into domain objects or logs.
- Define breaking changes and deprecation window; OpenAPI compatibility diff in CI.

### 5.3. Illustrative Java interfaces (PROPOSED, not present in source)

```java
public interface HelpdeskUseCase {
    RequestView create(CreateRequestCommand command);
    RequestView get(UUID id);
    RequestView resolve(UUID id, long expectedWorkflowVersion, String resolution);
}

public interface AuthorizedWorkflowUseCase {
    WorkflowView get(String instanceId);
    WorkflowView transition(String instanceId, String transitionKey, long expectedVersion);
}
```

UseCase should obtain actor from `CurrentPrincipal.requireVerified()` or a trusted provenance-preserving execution context; never trust a request JSON actor. The exact interface signature is contingent on tenant/transaction design and is not an implementation instruction to copy verbatim.

## 6. Module / package design and dependency rules

```text
backend/src/main/java/com/tricore/dxos/
  core/
    identity/                    # provider-neutral identity facts/contracts
    workflow/                    # domain/runtime/persistence port
    data/                        # storage port/contracts
    gateway/contract/            # only if truly framework-neutral shared Gateway contract
  application/
    identity/CurrentPrincipal.java
    authorization/              # operation, resource/tenant policies
    workflow/AuthorizedWorkflowUseCase.java
    helpdesk/HelpdeskUseCase.java
    facility/FacilityUseCase.java
  gateway/
    common/error/                # HTTP error mapping (Spring-specific)
    common/security/             # HTTP identity bridge
    helpdesk/HelpdeskController.java
    workflow/WorkflowHttpController.java (registered only after gate)
    facility/FacilityController.java
  infrastructure/
    identity/security/           # verified JWT implementation
    workflow/postgres/           # workflow persistence adapter
    helpdesk/postgres/           # business persistence adapter
  bootstrap/                     # DI wiring, exposure flags, startup checks

portal-bff/src/main/java/.../portal/
  security/PortalSecurity.java
  session/PortalSessionController.java
  transport/BackendHttpClient.java
  routes/HelpdeskProxyController.java
  routes/FacilityProxyController.java
```

**Dependency direction:** `Gateway HTTP -> Application UseCase -> Core domain/ports`; provider adapters implement inward ports; `Core !-> Gateway/Application/Infrastructure`; application must not import Spring Web, servlet request, HTTP DTO or `infrastructure` concrete classes. Infrastructure-specific composition/wiring may reference implementations, but use-case code must not. `WorkflowRuntime` is not an authorized HTTP use case. Avoid package mass move until tests are green.

**ArchUnit targets:**
- ARCH-GW-01 no classes in `core..` depend on `gateway..` or `infrastructure..`.
- ARCH-GW-02 no classes in `core..` depend on Spring Web/JDBC/Servlet (scoped exemptions only if justified).
- ARCH-GW-03 HTTP controllers must not depend on classes in `infrastructure..`.
- ARCH-GW-04 Helpdesk/Facility application services must not depend on HTTP DTOs.
- ARCH-GW-05 Gateway controllers must not access `WorkflowRuntime` directly for protected use cases.
- ARCH-GW-06 no cross-application implementation dependencies.
- ARCH-GW-07 provider bindings unique per active profile; fail startup on ambiguity.

## 7. Công nghệ, cấu hình và vận hành

| Layer | Current / proposed technology | Responsibility | Why not replace immediately |
|---|---|---|---|
| TLS ingress | Caddy local via mkcert (candidate environment) | HTTPS, host routing | Already provides local TLS, no need Kong solely for diagram |
| Portal UI | React + TypeScript PR #71 | views, forms, client-side status | Replacing UI framework does not fix API authorization |
| BFF | Spring Boot OAuth2 Client PR #70 | session/PKCE/CSRF, allowlisted proxy | Preserves security investment; needs E2E |
| Identity provider | Keycloak | OIDC/SAML identity service (OIDC used) | Do not custom-build authentication |
| Backend HTTP | Spring MVC 3.5.16 + Spring Security JWT | validate HTTP, verify JWT, route | Stable for modular monolith |
| Application | Java 21 | policies/orchestration/transaction | Split contracts before changing framework |
| Workflow | Pure Java domain + persistence port | transition/version/history semantics | Strong provider-independent contract |
| DB | PostgreSQL 17, JDBC/JPA, Flyway | transactional data | Swapping DB needs adapter + semantic tests |
| Testing | JUnit/MockMvc/PostgreSQL integration/browser tests | evidence | Must run together on pinned RC, not combine PR pass counts |

### 7.1. Deployment modes

- **Development MOCK:** Portal mock adapter; must show visible MOCK indicator, never treated as production E2E.
- **Isolated demo LIVE:** loopback/controlled HTTPS ingress; BFF session; Backend verified JWT; isolated disposable PostgreSQL setup; one known schema/runtime account; no public exposure.
- **Production:** not claimed complete; requires secrets management, TLS, monitoring, session scaling, hardening, backups, DB least privilege and threat model review.

**Critical integration blocker:** PR #63's disposable PostgreSQL guard and loopback `server.address` assumptions may not work unmodified behind ordinary Compose networking or a remote BFF. Document the actual run topology, guard exceptions and runtime credentials; do not weaken them merely to make demo work. Use loopback composition, same-host bridge or an explicitly reviewed network model. Any relaxation requires threat-model and tests.

### 7.2. Runtime configuration proposal

```yaml
# Proposed configuration schema (NOT committed values)
dxos:
  api:
    mode: demo
    legacy-request-enabled: false
    helpdesk-enabled: true
    generic-workflow-enabled: false
  portal-bff:
    # managed in separate BFF process
    backend-base-url: ${PORTAL_BACKEND_BASE_URL}
```

This configuration is illustrative: PR #63 currently uses `dxos.helpdesk.enabled`. Do not silently rename without adapting the candidate, tests and docs. Avoid putting secrets in YAML or source. Release needs canonical env matrix, valid startup profile and fail-fast errors for invalid combinations.

### 7.3. Database and OIDC operations

- IdP issuer used by Backend JWT validator must match real tokens; mapping issuer/subject into canonical ID must be stable across session and API.
- Secrets: OAuth client secret only BFF; DB password only Backend; browser gets neither.
- Database migration should be deterministic and versioned; separate schema-provisioner credentials from runtime user.
- Runtime user should not be DB owner/superuser. Application-enforced authorization is not a license to leave privileged DB credentials exposed.
- No automatic write retries after interrupted network/unknown commit. Expose reconciliation instructions to operator.

## 8. Detailed implementation plan — incremental, no code yet

### Phase 0 — Evidence freeze & ADR ratification

**Tasks:** Pin exact SHAs of main/PR63/PR70/PR71; capture known behavior, route inventory, current tests and external contract. Approve GW-ADR-01...06 plus security ADR (`Application-enforced`). Decide current vs target runtime network topology.

**Artifacts:** `docs/architecture/gateway/ADR-GW-001..006.md`, baseline manifest, ownership matrix, threat model, route matrix.

**Gate:** ADR approved; no contradictory ownership or exposed generic route. Design docs distinguish current/recommended/deferred.

### Phase 1 — Contracts before implementation

**Tasks:** Define Helpdesk OpenAPI, standard HTTP error semantics, `HelpdeskUseCase`, `AuthorizedWorkflowUseCase`, principal provenance, read visibility and resource binding. Inventory SQL functions/permissions and transaction behavior from PR63 before changing Application enforcement.

**Gate:** Unit acceptance specs reviewed; explicit mapping of 401/403/hidden404/409/unknown commit; DB transaction design signed off.

### Phase 2 — Controller/UseCase boundary

**Tasks:** Make Helpdesk Controller depend on application-facing interface; separate HTTP DTO and view mapping; introduce authorized workflow facade without enabling unregistered generic API. Refactor SQL collaboration only when transactional consistency tests exist.

**Gate:** ArchUnit rules pass; no controller→infrastructure; no direct protected controller→WorkflowRuntime; fail injection preserves consistency.

### Phase 3 — Route isolation

**Tasks:** Gate legacy controllers default OFF; Helpdesk flag compatible with existing PR63; generic workflow remains OFF; configure startup mutual-exclusion checks and endpoint registry tests.

**Gate:** For each mode, startup and route map exactly match exposure matrix; invalid mode fails startup; forged headers cannot enable bypass.

### Phase 4 — Modular BFF transport

**Tasks:** Preserve PR70 security and negative tests. Split Helpdesk route specification from shared upstream HTTP client; restrict per-app methods/routes. Keep BFF default-deny fallback. Add Facility adapter later, not generic open proxy.

**Gate:** Existing 24 focused tests reported in PR70 are rerun independently at integration SHA; limits, paths, no redirects, 401 invalidation/403 preservation, CSRF and unknown outcome pass.

### Phase 5 — Integration branch

**Tasks:** Assemble Backend, BFF, React Portal, Keycloak config, TLS ingress, migrations and runtime DB privileges on a fresh integration branch pinned to manifest. Resolve PR63 Stage6/application-enforced mismatch with explicit migration diff. Rebase and prove every test at **one** release candidate SHA.

**Gate:** No unresolved LIVE_BLOCKED, clean build, startup, full flow and negative cases with independent evidence; no hand-waved merge of disjoint pass counts.

### Phase 6 — Facility reuse proof

**Tasks:** Implement minimal but end-to-end Facility workflow including create, assign (if in contract), transition, read, history, display in Portal. Reuse common BFF transport and Backend identity/error/gateway conventions. Freeze Core and shared modules during acceptance test to prove no changes.

**Gate:** New app works on same release; diff shows no modifications to Core domain/shared security except pre-approved generalized contracts; full Helpdesk regression PASS.

### Phase 7 — Release and docs

**Tasks:** OpenAPI, quickstart, architecture overview, API examples, environment matrix, known limitations, CI, release notes, LICENSE/NOTICE check and clean machine rehearsal.

**Gate:** Independent reviewer clones pinned release and reproduces the complete demo only with published docs.

## 9. Testing strategy and detailed acceptance matrix

**Principles:** Tests target an exact build SHA and environment; build metadata includes schema version, Keycloak realm/config version, browser version, DB runtime account. Use unit tests (fast), HTTP integration tests with real verified token, database integration + concurrency, browser E2E, and independent clean install. Claims on PR descriptions are *author-reported* until reproduced.

| ID | Level | Scenario | Expected evidence / PASS |
|---|---|---|---|
| GW-001 | HTTP security | Anonymous → protected route | 401, no business write |
| GW-002 | HTTP security | JWT bad signature/issuer/audience/token type/subject | 401, no principal trusted |
| GW-003 | HTTP security | Forged X-User/X-Role, missing JWT | 401, no elevated identity |
| GW-004 | HTTP security | Valid JWT, insufficient operation authority | 403, no write |
| GW-005 | HTTP security | Cross-tenant or hidden resource | 404/403 per visibility contract, no leak/write |
| GW-006 | App policy | Other technician resolves request | Forbidden; state/history unchanged |
| GW-007 | App policy | Requester mismatch on confirm | Forbidden; state/history unchanged |
| GW-008 | Concurrency | Two writers same expectedVersion | One winner; loser 409; single history increment |
| GW-009 | Concurrency | Reassign concurrently with resolve/revoke | Ordering conforms to locked/conditional policy; no stale privilege write |
| GW-010 | Transaction | Inject failure between business fact and workflow commit | All-or-nothing rollback or documented uncertain outcome |
| GW-011 | Network | Drop COMMIT acknowledgment on POST | No blind retry; reconciliation possible |
| GW-012 | BFF | Missing/invalid CSRF on cookie-authenticated POST | 403/CSRF policy denial before upstream call |
| GW-013 | BFF | Callback PKCE/state mismatch | Login rejected; no authorized session |
| GW-014 | BFF | Backend returns 401 | BFF invalidates session; returns 401 |
| GW-015 | BFF | Backend returns 403 | BFF preserves 403, session not inappropriately invalidated |
| GW-016 | BFF | Unknown action/path/method, redirect upstream | 404/405 per contract, no arbitrary proxy/redirect follow |
| GW-017 | BFF | Payload over maximum size | 413, no upstream request |
| GW-018 | Route | All flags unset | Legacy OFF, Helpdesk OFF, Generic OFF, route registry proves absence |
| GW-019 | Route | Demo mode | New Helpdesk only; legacy and generic absent |
| GW-020 | Route | Legacy and new both ON | Startup fails deterministically |
| GW-021 | Route | Generic ON without authorized facade | Startup fails / no route mapping |
| GW-022 | Architecture | Core → Gateway/Infrastructure illegal dependency | ArchUnit rejects |
| GW-023 | Architecture | HTTP controller → Infrastructure concrete class | ArchUnit rejects |
| GW-024 | API contract | OpenAPI DTO/status/response compatibility | Consumer contract tests pass |
| GW-025 | E2E | Login → create → triage → assign → start → resolve → confirm → history | Real UI/BFF/Backend/DB proof; exact actors and versions |
| GW-026 | E2E | Restart Backend & BFF | Committed request/history preserved; session behaves as documented |
| GW-027 | Reuse | Add Facility without shared security/Workflow Core changes | Both apps pass full regression |
| GW-028 | Release | Fresh clone/bootstrap from documented instructions | Independent reviewer reproduction, clean logs |
| GW-029 | Security/ops | Browser/network headers, logs, build artifacts | No OAuth client secret, bearer token, DB password leakage |
| GW-030 | Observability | Request fails across BFF and Backend | Correlation ID sanitized and traceable; no sensitive values logged |

### 9.1. Test case mẫu có bước cụ thể

**TC-GW-009 — Concurrent revoke versus resolve**: (a) tạo request R ở tenant T, worker W hợp lệ; (b) phiên A bắt đầu resolve và dừng ngay trước authoritative check/update; (c) phiên B thu hồi quyền hoặc reassign và commit; (d) cho A tiếp tục; (e) assert thứ tự/serialization rules đã thiết kế, không có unauthorized commit, không có partial workflow/business history; (f) lưu DB snapshots, HTTP statuses, transaction log redacted. Không tuyên bố pass nếu chỉ unit-mock repository.

**TC-GW-011 — Lost COMMIT ACK**: (a) tạo request R; (b) gửi POST transition; (c) inject cắt kết nối sau khi server có thể đã commit; (d) BFF trả uncertain outcome, không retry; (e) authoritative read/history xác định đúng version trước khi operator/user quyết định thao tác tiếp; (f) chứng minh không duplicate history.

**TC-GW-018 — Endpoint mapping**: khởi động với default config; đọc `RequestMappingHandlerMapping` trong integration test; assert không có legacy, new Helpdesk hoặc generic Workflow mappings. HTTP 404 chỉ là kiểm tra bổ sung, không thay registry assertion.

**TC-GW-025 — Real demo**: dùng browser và Keycloak test realm để đăng nhập requester; tạo ticket; coordinator triage/assign; technician start/resolve; requester confirm; kiểm tra history, versions, page/list visibility; restart Backend rồi đọc lại. Lưu sanitized timestamps, SHA, screenshots (không chứa secrets) và DB evidence.

### 9.2. Definition of Done

Một phase chỉ DONE khi có source đúng commit, CI pass, negative tests pass, không có P0 blocker, tài liệu cập nhật, reviewer độc lập đã xác nhận, và bằng chứng reproducible được liên kết. “Tests available” hoặc “local author reported PASS” không tương đương independent acceptance.

## 10. Threat model và risks

| Risk | Impact | Mitigation | Owner đề xuất |
|---|---|---|---|
| Generic API bypass business workflow | Critical | default OFF + restricted authorized facade/resource binding | Backend/Identity |
| BFF open proxy or route traversal | High | per-app allowlist, normalized path, strict origin | BFF |
| JWT/session confusion | Critical | split BFF cookie boundary and Backend bearer verifier | Identity |
| Legacy API enabled in demo | High | route flags default OFF + startup validation | Backend |
| App-enforced policy races | Critical | transaction policy, conditional write/locks, concurrency suite | Backend |
| Business + Workflow partial commit | Critical | explicit unit-of-work same DB transaction, fault injection | Backend/Data |
| CSRF bypass | High | BFF CSRF enabled, same-origin session, browser tests | BFF |
| Cookie/session deployment mismatch | High | HTTPS ingress host/callback tests, config pinning | DevOps/BFF |
| RC PR drift / incompatible DTO | High | integration branch, manifest SHAs, contract regression | Integration owner |
| Facility feature creep | Medium/High | vertical slice acceptance, no large second product before IT E2E | Application |
| Secrets in logs/docs | High | allowlist sanitized logging, scanner, clean deployment | All |

## 11. Decision and responsibility matrix

| Decision / deliverable | Accountable proposed | Reviewers | Required output |
|---|---|---|---|
| GW topology + trust model | Architecture/Identity | Backend, Frontend | ADR + threat model |
| Backend security and authorized Workflow use case | Backend | Identity, Application | interface + tests |
| Helpdesk API and DTO mapping | Application/Backend | Frontend | OpenAPI + contract tests |
| BFF session, transport and route adapters | Frontend/BFF | Identity, Backend | 24+ regressions + browser proof |
| Route modes and conflicts | Backend | Security reviewer | mapping/startup tests |
| PostgreSQL consistency compatibility | Backend/Data | Independent reviewer | function inventory + concurrency/negative evidence |
| Facility reuse | Application | Backend, Identity | minimal E2E + no-core-change diff |
| Integration/release | Team lead | All | pinned manifest + clean install report |

Đây là đề xuất chia trách nhiệm theo năng lực; nhóm phải xác nhận người chịu trách nhiệm chính thức.

## 12. Checklist phê duyệt kiến trúc

- [ ] Sáu GW-ADR có status ACCEPTED và giải thích trade-offs.
- [ ] ADR bảo mật Application-enforced không mâu thuẫn PostgreSQL persistence semantics.
- [ ] Public Business API và Restricted Workflow API có phạm vi và chính sách riêng.
- [ ] Generic Workflow Controller **chưa được public** trước khi AuthorizedWorkflowUseCase đạt security gate.
- [ ] Controller chỉ gọi Application UseCase, không import `infrastructure`.
- [ ] BFF route modules dùng chung transport nhưng không mở arbitrary proxy.
- [ ] Legacy Route OFF và conflict flags fail startup.
- [ ] Có OpenAPI, canonical error response, no-store và versioning policy.
- [ ] Có contract tests, security negatives, concurrency tests, real E2E.
- [ ] Facility chứng minh reuse không sửa Core/shared Gateway policies.
- [ ] Release candidate pinned, clean-install independent accepted.

## 13. Open decisions (không được tự xem là đã chốt)

1. Tên namespace API dài hạn: giữ `/api/v1/mvp/requests` hay chuyển `/api/v1/helpdesk/requests` sau MVP?
2. Generic Workflow API có thật sự cần kích hoạt trong bản thi, hay giữ restricted/off và chứng minh reuse qua Java UseCase?
3. Canonical error envelope giữ `ApiError` hay migration sang RFC 9457 Problem Details? Nếu đổi phải version/compatibility plan.
4. Unit of work đi qua JDBC guarded transaction hiện có hay adapter/transaction abstraction mới? Cần thiết kế cụ thể để giữ cùng COMMIT.
5. Cách vận hành BFF/backend trong local loopback và Docker networking tương thích disposable DB guard của PR #63?
6. Mức Facility hoàn chỉnh: tối thiểu vertical slice E2E hay toàn bộ UX? Ưu tiên chứng minh reuse trên cùng release.
7. `CurrentPrincipal` hay explicit trusted execution context cho batch/event future clients?
8. Rate limiting, distributed sessions, external API management là post-MVP, không giả vờ đã triển khai.

## 14. Tiêu chí chấm thử hướng đến OLP PMNM 2026

Thiết kế này giúp chứng minh: nguồn mở có docs/release/build, tính nguyên gốc ở boundary/reuse, mức hoàn thiện ở real E2E, user friendliness qua Portal, sustainability qua tests/contracts/ports, và demo/reproducibility. Đây là mapping đánh giá nội bộ; điểm chính thức thuộc Ban Giám khảo. Không cộng điểm kiến trúc như một hạng mục không có trong thể lệ.

## 15. Source links và provenance

- Main: https://github.com/lexuanbao258-collab/dxos-open-core/tree/e06fa429cfd92b032c166eb3ebf32803d13191c2
- PR63: https://github.com/lexuanbao258-collab/dxos-open-core/pull/63
- PR70: https://github.com/lexuanbao258-collab/dxos-open-core/pull/70
- PR71: https://github.com/lexuanbao258-collab/dxos-open-core/pull/71
- Workflow Core: https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowRuntime.java
- Gateway controller: https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/backend/src/main/java/com/tricore/dxos/gateway/workflow/controller/WorkflowController.java
- Principal gate: https://github.com/lexuanbao258-collab/dxos-open-core/blob/e06fa429cfd92b032c166eb3ebf32803d13191c2/backend/src/main/java/com/tricore/dxos/gateway/workflow/security/WorkflowGatewayPrincipalGate.java
- Helpdesk controller: https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/backend/src/main/java/com/tricore/dxos/gateway/helpdesk/HelpdeskController.java
- BFF client: https://github.com/lexuanbao258-collab/dxos-open-core/blob/d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27/portal-bff/src/main/java/com/tricore/dxos/portal/PortalBackendClient.java
- BFF security: https://github.com/lexuanbao258-collab/dxos-open-core/blob/d833b363d22c1ad30a1f5d7bdc7b3d7712fb7d27/portal-bff/src/main/java/com/tricore/dxos/portal/PortalSecurity.java

---

**Kết luận:** Giữ topology **React → BFF → Backend Gateway → Authorized Application UseCase → Identity/Workflow/Data**. Không sửa code trước khi contract/security/transaction boundary được phê duyệt. Gateway v2 thành công khi Facility onboarding không phải nhân bản JWT parsing, BFF transport, API error convention hoặc sửa Workflow domain; kết quả phải được chứng minh bởi tests chạy trên cùng release candidate.
