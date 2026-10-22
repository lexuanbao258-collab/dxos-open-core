# Issue #23 — original body snapshot before seven-field standardization

**Preserved verbatim on 2026-10-22.** The original Issue #23 body is archived below to avoid losing the evolving specifications/earlier ownership context when changing the live GitHub Issue into a concise English acceptance tracker. **This snapshot is historical, not the controlling current acceptance status.** For current scope/status use [Issue #23](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23), [Stage 6 Scope-Cut V2](https://github.com/lexuanbao258-collab/dxos-open-core/issues/23#issuecomment-6292834608) and its linked PRs. GitHub issue comments remain available as dated evidence; do not mistake superseded 2026-10-12/17 statements for current assignments.

---

## Owner / Collaboration
- **Owner:** Lê Xuân Bảo (`@lexuanbao258-collab`)
- **Security co-designer / reviewer:** Phan Hải Yến (`@phanyen06ym-ien`)
- **Priority:** P0 — required before exposing Workflow APIs in production
- **Branch:** Create from up-to-date `main` when Identity and Workflow HTTP contracts are ready (avoid overlapping edits).

## Goal
Wire Workflow HTTP traffic through a real, fail-closed Gateway/security boundary in the existing Spring Boot modular monolith. **Not a new microservice**. Use the OIDC validation built by Yến; do not duplicate JWT parsing or create fake principals.

## Scope
- Connect verified authentication to Workflow HTTP endpoints and construct a stable `ActorReference` from the trusted issuer + subject mapping approved by Yến.
- Authorize `workflow:create`, `workflow:activate`, `workflow:transition`, `workflow:read` (final authority names jointly agreed with Yến).
- Define and enforce **resource/tenant-scoped authorization** before reading instances/history or mutating workflows; bearer scope alone must not grant access to arbitrary resources.
- Route requests to the existing WorkflowRuntime and Data adapter with clear security/HTTP boundaries.
- Consistent safe 401 (unauthenticated) and 403 (forbidden) errors from Spring Security filter chain; never leak tokens, SQL, stack traces or provider internals.
- No implicit trust of `X-User`, `X-Role`, self-declared `AuthenticatedPrincipal`, or raw unverified `Principal.getName()`.
- Preserve existing Request endpoints' intended security posture; agree on token/session/CSRF policy with Yến.

## Definition of Done
- [ ] Valid OIDC bearer token with sufficient permission/resource access reaches Workflow Runtime with the verified actor.
- [ ] Missing, expired, forged, wrong issuer/audience token returns 401; no write.
- [ ] Authenticated but unauthorized scope/tenant/resource returns 403; no read/write leak.
- [ ] Client-forged actor/role headers cannot alter identity or authorization.
- [ ] Spring Security filter-chain tests and negative cross-tenant/resource tests pass; no test-only mock authenticators in production configuration.
- [ ] Operational error mapping and existing Request regression tests pass.
- [ ] JDK 21 full backend tests/build pass and integration PR is reviewed by Yến.
- [ ] PR linked and merged after dependent security interface is available.

## Dependencies / boundaries
Depends on Yến's OIDC principal and authorities mapping and Bảo's Workflow HTTP API. Ánh owns persistence. Never modify Workflow Core contracts to implement Gateway authorization.


## Phase status and additional security gates — 2026-10-12

**Phase 1:** PR #29 (HEAD `fd5469e`) is OPEN, not merged, awaiting Yến's security review. It provides `WorkflowAccessPolicy`, `WorkflowAuthorization`, canonical authority contracts and foundation tests, **not** production ACL policy or authorized orchestration. Issue #23 stays OPEN after Phase 1 merge.

**Phase 2:** Requires verified Identity #24, real persistence #25, a production authoritative resource/tenant policy, and coordinated enforcement at transaction boundaries.

### Additional acceptance criteria
- [ ] Eliminate the **hidden duplicate `instanceId` oracle**, including simultaneous inserts; mapping duplicate 409 to 404 is not sufficient. Review server-generated opaque IDs at application/HTTP boundary without changing Core signatures, and specify compatibility and lost-response/idempotency behavior.
- [ ] Assign an authoritative owner/source for resource existence, visibility, tenant membership, resource bindings and ACL; do not infer access from `resourceId`, an assignee field or operation scope. Hidden/nonexistent must have equivalent public responses; visible forbidden 403; policy null/throw/timeout fail-closed.
- [ ] Define revocation/TOCTOU semantics and enforcement with Ánh: final ACL check coordinated with PostgreSQL commit, compatible locks or revision guards across ALL membership/ACL/ownership mutators, and authorized guarded reads/history materialization. `expectedVersion` alone does not protect ACL. Avoid an outer transaction that violates persistence port durable-commit semantics.
- [ ] Use only Yến's verified OIDC issuer + subject identity for actor: `oidc:<base64url(UTF8(verified issuer))>.<base64url(UTF8(verified subject))>` without padding/normalization. Enforce exact `workflow:create`, `workflow:activate`, `workflow:transition`, `workflow:read`; no client actor/header trust or second JWT verifier.
- [ ] Test real filter-chain/JWKS, cross-tenant, hidden/missing equivalence, duplicate races, concurrent ACL revoke, provider outage and no unauthorized snapshot/history/write; preserve `COMMIT_OUTCOME_UNKNOWN` without blind retry. Production HTTP #22 must remain disabled until gates pass.

**Ownership:** Bảo — authorized orchestration/Gateway; Yến — verified Identity and authority mapping; Nguyễn Ngọc Ánh — PostgreSQL adapter/transaction coordination. The three agree on actual ACL source and revocation semantics before implementation is considered production-ready.

---

## Cập nhật định hướng PaaS / OLP — 2026-10-17

**Priority P0 — Security gate giữ nguyên; trạng thái: PAUSED chờ Ánh hoàn thiện PostgreSQL ACL contract.** Đây là Gateway boundary cho ứng dụng Tầng 2 #39, không phải một microservice riêng và không yêu cầu sửa lại Workflow Core.

- **Phase 1 thực tế đã merge:** PR #29. Dòng trạng thái 2026-10-12 phía trên là snapshot lịch sử, không còn phản ánh PR hiện tại.
- **Phase 2 Draft:** PR #36 có opaque instance ID generator và [đề xuất PostgreSQL ACL transaction](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue23-secure-instance-id-foundation/docs/contracts/issue-23-acl-transaction-proposal.md); generator **chưa nối vào đường create production**, authorized orchestration vẫn chưa triển khai.
- **Ba chính sách owner đã phê duyệt:** (1) resource owner không tự có Workflow permissions; cần explicit ACL + exact Identity authority, (2) Workflow Definition binding chính xác theo tenant, resource type, definition ID/version; revoke chặn CREATE mới nhưng không mặc nhiên xóa instances hiện có, (3) không hỗ trợ direct tenant transfer v1; tombstone không được tái sử dụng ID và từ chối các Workflow API thông thường.
- **Chưa phê duyệt:** DDL, forward-only migration, tenant/resource guard lock, transaction coordinator, read/history materialization consistency, revoke-race behavior. Ánh chịu trách nhiệm cung cấp interface/DDL và PostgreSQL disposable tests; Bảo review, quyết định và tích hợp `AuthorizedWorkflowService`; Yến cung cấp verified principal #24/#33.
- **Không được bật Workflow HTTP #22** cho đến khi các security gates, hidden 404 / visible forbidden 403 / invalid token 401, exact authority + tenant/resource ACL, commit outcome unknown và real concurrency/negative tests PASS.
- Consumer Tầng 2 #39 **có thể làm UI với mock được gắn nhãn**, nhưng chỉ tích hợp thật khi #23 hoàn thiện; backend demo #27 và release #40 phụ thuộc gate này.

Không tạo lại Gateway hoặc mở endpoint chỉ để phục vụ Swagger #37/demo. Phần Core cũ được giữ nguyên.

**Hành động kế tiếp khi Ánh rảnh:** nhận và review DDL + transaction coordinator contract, sau đó mới viết guarded orchestration; Issue tiếp tục OPEN.


---

## Cập nhật điều phối sau hoán đổi nhiệm vụ — 2026-10-17

**Owner kỹ thuật/security/integration:** @lexuanbao258-collab (Bảo). Ánh tập trung code frontend ở #39, **không còn là người chịu trách nhiệm chính cung cấp ACL DDL/guard hay điều phối kiểm thử hạ tầng cho #23**. Ghi chú cũ “chờ Ánh rảnh” ở phía trên là trạng thái lịch sử; không tự suy ra DDL đã được duyệt hoặc blocker đã được giải quyết.

**Next steps do Bảo chủ trì:** rà soát 3 owner ACL policies đã duyệt; lập/review schema & transaction coordinator proposal với người có chuyên môn trước khi triển khai; lập kế hoạch guarded reads/writes, revoke-race, typed error bridge, final ACL check trong cùng JDBC transaction; phối hợp verified OIDC với Yến (#24/#33); sau đó mới triển khai/test `AuthorizedWorkflowService` và quyết định mở #22. Có thể phân công code phần độc lập sau khi contract chốt.

**Giữ nguyên:** #23 OPEN/P0, PR #36 DRAFT, DDL/migrations và guard transaction **chưa được duyệt**, không bật production Workflow HTTP, không tự sửa Core contracts/Flyway hay chạy migration trên shared Supabase.

