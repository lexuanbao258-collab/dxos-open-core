# DX-OS WORKFLOW CORE v2 — ARCHITECTURE DESIGN & REVIEW

**Project:** DX-OS Open-Core / TRICORE / OLP PMNM 2026
**Status:** PROPOSED — REVIEW REQUIRED / NO CODE CHANGE
**Review baseline:** `main` `e06fa429cfd92b032c166eb3ebf32803d13191c2`; PR #47 `4a2aa7e`, #48 `b3bd87d`, #63 `e12d105` (Draft; chưa tích hợp/independent E2E).
**Phạm vi:** Workflow Core — capability số 2 trong Identity, Workflow, Data, API Gateway.
**Mục đích:** Tài liệu thiết kế, đào tạo, phản biện và lập acceptance gate; KHÔNG phải mô tả code v2 đã tồn tại.

## 0. Executive summary và quyết định chưa chốt

Workflow Core hiện có domain model, Runtime và provider-neutral `WorkflowPersistencePort` tốt; `PostgresWorkflowPersistence` thực thi JDBC transactions, kiểm soát optimistic version, authoritative definition và history. Tuy nhiên Application Helpdesk trong PR #63 gọi trực tiếp các SQL functions `wf_port_*` và `helpdesk_*`, phối hợp business facts + workflow state + ACL trong một transaction PostgreSQL. Đây là chỗ xung đột với tuyên bố “đổi DB chỉ thay adapter”.

**Khuyến nghị:** Giữ modular monolith + domain-first workflow engine; không mở raw Workflow Runtime qua HTTP; giữ một owner duy nhất cho transition state/history; thiết kế application-facing authorized use-case và transactional integration boundary trước khi refactor SQL; chứng minh reuse qua Helpdesk + Facility bằng hai vertical slices thật. Với Stage 6/Application-enforced, cần ADR security migration riêng và independent negative/race tests.

**Decision register — cần team duyệt:** WF-ADR-01 scope (minimal finite-state engine vs BPMN engine); WF-ADR-02 persistence/unit-of-work; WF-ADR-03 authorization ownership; WF-ADR-04 definition provisioning/versioning; WF-ADR-05 public vs restricted API; WF-ADR-06 reuse proof và extension.

## 1. Thuật ngữ nền tảng

- **Workflow definition:** Bản khai báo bất biến theo `(definitionId, definitionVersion)`, chứa trạng thái khởi đầu, tập states và allowed transitions.
- **Workflow instance:** Một lần chạy cụ thể của definition, bind với resource (`ResourceReference`), có runtime version, lifecycle và current state.
- **State:** Trạng thái nghiệp vụ trừu tượng, ví dụ `SUBMITTED`, `TRIAGED`, `IN_PROGRESS`.
- **Transition:** Chuyển từ state A đến B qua transition identifier; chỉ thành công nếu đúng definition, lifecycle, version.
- **Actor:** Chủ thể gây chuyển tiếp; Runtime nhận `ActorReference` nhưng KHÔNG xác thực actor.
- **History/Event:** Bằng chứng transition đã commit; transition và matching history phải cùng thành công hoặc cùng không thành công.
- **Definition version vs runtime version:** Definition version chỉ bản thiết kế bất biến; runtime version tăng mỗi transition và dùng optimistic concurrency. Assignment version ở Helpdesk là version riêng.
- **Optimistic concurrency:** Caller cung cấp expectedVersion; writer stale nhận conflict (409 ở HTTP adapter).
- **Atomicity:** Tất cả mutation trong một đơn vị công việc thành công hoặc thất bại cùng nhau.
- **TOCTOU:** Quyền được kiểm tra rồi bị thay đổi trước lúc ghi; cần policy/locking/transaction semantics rõ.
- **COMMIT_OUTCOME_UNKNOWN:** Không khẳng định chắc transaction đã rollback hay commit do mất ACK; tuyệt đối không retry write mù.
- **Port/Adapter:** Interface mô tả semantic behavior; adapter thực thi bằng công nghệ cụ thể và phải qua contract tests.

## 2. Bằng chứng code và trạng thái GitHub

| Nguồn | Hiện trạng kiểm tra | Ý nghĩa review |
|---|---|---|
| `core/workflow/WorkflowRuntime.java` | Orchestrate create/activate/transition/read/history; không auth | Domain Runtime không thể exposed thẳng như authorized public API |
| `core/workflow/WorkflowPersistencePort.java` | Six methods và semantic contract chặt cho atomicity, exact version, unknown outcome | Provider-neutral ở lớp contract, chưa có bằng chứng multi-adapter |
| `infrastructure/workflow/PostgresWorkflowPersistence.java` | Implements port; tự quản JDBC transaction; PostgreSQL schema guard | Có transaction isolation riêng, không ghép Spring transaction tự động |
| `gateway/workflow/controller/WorkflowController.java` | Chưa đăng ký Spring controller; phụ thuộc trực tiếp WorkflowRuntime | Cố ý LIVE_BLOCKED vì thiếu authorized path |
| PR #47 | `AuthorizedWorkflowService` candidate; verified CurrentPrincipal + application pre-check | Chỉ precheck không tự chứng minh TOCTOU |
| PR #48 | Exact authority regression candidate | Không thay thế resource/tenant policy |
| PR #63 | Helpdesk 9 routes, direct guarded SQL functions và business+Workflow transaction | Implementation đang phụ thuộc PostgreSQL, cần lộ trình chuyển đổi |

Nguồn: https://github.com/lexuanbao258-collab/dxos-open-core ; PRs https://github.com/lexuanbao258-collab/dxos-open-core/pull/47 , /pull/48 , /pull/63 . Các kết quả test trong PR là **author-reported**, không được nâng thành independent acceptance.

## 3. RESPONSIBILITY — Core nên và không nên làm gì

**Workflow Core sở hữu:** definition/state/transition model; state machine invariants; lifecycle; runtime version; history semantics; persistence port contract; normalized workflow errors.
**Không sở hữu:** JWT/OIDC verification; employee/technician roles; tenant/resource ACL; helpdesk requester/assignee/resolution; HTTP routing; file storage; business dashboards; user provisioning.

**Phân giới:** Identity trả trusted principal; Application kiểm resource/tenant/business eligibility; Workflow domain quyết định transition được phép hay không; DB adapter duy trì atomicity; API gateway map request/response và HTTP errors.

## 4. CONTRACT — public domain/port semantics

`WorkflowPersistencePort`: `loadDefinition`, `createInstance`, `commitActivation`, `loadInstance`, `loadHistory`, `commitTransition`. Định nghĩa binding `(definitionId, definitionVersion)` immutable; read authoritative; optimistic version conflict; full snapshot matching; history phiên bản liên tiếp; preserve exact Instant. Adapter phải phân biệt confirmed rollback với COMMIT_OUTCOME_UNKNOWN.

**Contract boundaries:** `WorkflowRuntime` không nhận untrusted actor qua web; `TransitionResult` do Domain tạo không mặc nhiên được DB tin; adapter re-derive từ authoritative definition. Không thêm `save(anything)` generic có thể phá invariant. Không đưa JDBC Connection vào Core domain public surface.

**Proposed application port (không phải code hiện có):**

```java
public interface AuthorizedWorkflowUseCase {
    WorkflowView create(CreateWorkflowCommand command);
    WorkflowView activate(ActivateWorkflowCommand command);
    WorkflowView transition(TransitionWorkflowCommand command);
    WorkflowView get(String instanceId);
    List<WorkflowHistoryView> history(String instanceId);
}
```

Caller identity lấy qua trusted CurrentPrincipal hoặc một execution context được xây tại trusted boundary; tuyệt đối không coi `actorId` trong HTTP payload là trusted.

## 5. PUBLIC API — lựa chọn thiết kế

| Phương án | Lợi thế | Rủi ro | Nhận định |
|---|---|---|---|
| A. Raw generic Workflow HTTP public | Dễ dùng engine linh hoạt | Bypass resource binding/ACL, tăng attack surface | Không chọn cho MVP |
| B. Chỉ business APIs (Helpdesk, Facility) | Dễ đảm bảo use case policy | Platform API chưa minh họa độc lập | Chọn MVP |
| C. Business APIs + restricted generic platform API | Chứng minh platform capability | Đòi hỏi consumer authentication, binding policy, tenancy và exposure gate | Mục tiêu v2, default OFF |

Không bật `WorkflowController` main bằng `@RestController` trực tiếp. `POST /api/v1/mvp/requests` là Helpdesk application API, không phải generic Workflow API. Public HTTP version không đồng nghĩa definition version.

## 6. LOGIC / STATE / LIFECYCLE

Workflow lifecycle: `NOT_STARTED -> ACTIVE -> COMPLETED` (các transitions phụ thuộc definition). Theo PR #63 `it-helpdesk@1`: `SUBMITTED -> TRIAGED -> IN_PROGRESS -> RESOLVED -> CONFIRMED`; transition keys `triage`, `start_work`, `resolve`, `confirm`. `assign` là Helpdesk application command, không tăng Workflow runtime version/history; có riêng assignment version.

**Create:** verify actor -> authorization and resource-binding rule -> create business facts + workflow instance + activation trong cùng business transaction boundary -> return after durable commit.

**Transition:** verify principal -> load authoritative request/tenant/resource/assignment -> policy check -> validate expected runtime version -> domain transition proposal -> guarded persistence writes state + history + business facts -> confirmed commit -> response.

**Read:** verify principal -> resource visibility before sensitive details/history -> read authoritative snapshot; snapshot và history gọi riêng không tạo ra cùng shared snapshot mặc định.

**Failure:** validation denial/no commit -> no mutation; known transaction rollback -> OPERATION_FAILURE; uncertain ACK -> COMMIT_OUTCOME_UNKNOWN, reconcile via authoritative read and operation correlation, không blind retry.

## 7. DEPENDENCIES — target architecture

```text
Portal -> BFF -> Backend Controller
                       |
                       v
               Authorized Helpdesk UseCase
                   |        |                   Identity Port  Business Policy  Transaction Boundary
                                         |
                                     Workflow Core
                                         |
                                WorkflowPersistencePort
                                         |
                              PostgreSQL Adapter (MVP)
```

- Core Workflow không import Identity/Gateway/Application/SQL/Spring.
- Application dùng Core public contracts; không gọi `wf_port_*` SQL trực tiếp.
- Infrastructure adapter import Core; DI/bootstrap nối implementation.
- Transaction coordinator/boundary không được làm mất atomicity giữa Helpdesk facts và Workflow history.

## 8. PROVIDER ADAPTER — thay công nghệ nhưng phải giữ semantics

**Option P1:** Giữ PostgreSQL + guarded SQL, expose một application transaction adapter có API rõ, từng bước tách SQL ra khỏi HelpdeskService; ít rủi ro nhất trước demo. **Option P2:** Thiết kế JDBC repositories + transaction coordinator, dùng một Connection cho business + Workflow; mở rộng portability nhưng refactor nhiều. **Option P3:** Spring Data JPA all-in; đổi cách locking/query/transaction, rủi ro cao. **Option P4:** External workflow engine (Flowable/Camunda); giảm engine tự viết nhưng đổi semantics lớn, không phù hợp cutover gấp.

**Quan trọng:** `PostgresWorkflowPersistence` hiện tự commit một logical operation, không tự chia sẻ Spring transaction. Ghép trực tiếp calls vào một `@Transactional` bên ngoài có thể không tạo một atomic transaction chung. Phải thiết kế transaction port riêng cho use case hoặc giữ guarded PostgreSQL transactional application adapter cho tới khi có chứng minh tương đương.

## 9. SECURITY / AUTHORIZATION

- Trusted principal từ Spring verified JWT/CurrentPrincipal; không dựa `X-User`/`actorId` untrusted.
- Operation authority exact-match là cần nhưng không đủ: tenant membership, resource ACL, requester/assignee eligibility là authoritative Application policy.
- DB privileges và app authorization phải được đối chiếu theo ADR-SEC-MVP (Application-enforced). Không xóa Stage 6 mechanisms theo suy luận; xác định từng SQL function là persistence/locking/privilege enforcement rồi có plan replacement.
- TOCTOU: actor revoke/reassign vs transition; policy reads và write phải có sync/lock/version semantics; negative concurrency tests dùng PostgreSQL thật.
- Hidden 404 vs 403 vs 409 phải có precedence thống nhất, không lộ resource metadata.
- Generic Workflow API cần validate resource binding ownership để tránh actor có `workflow:create` tạo instance gắn với resource người khác.

## 10. DATA, HISTORY VÀ AUDIT

Workflow history là transition history, **không đồng nghĩa toàn bộ Audit Core**. Business audit có thể cần lưu assignment, policy denial và các thay đổi không làm Workflow transition; không được tự bịa rằng mọi audit đã có. No second writable Helpdesk status; response status có thể được derive từ Workflow authoritative state. Assignment version độc lập workflow version.

## 11. REUSABILITY — hai ứng dụng thực tế

| Dimension | IT Helpdesk | Facility | Shared Core |
|---|---|---|---|
| Request type | IT_HELPDESK | FACILITY_REQUEST | Generic resource reference |
| Actors | Employee, Technician, Coordinator | Employee, Facility Staff, Supervisor | Canonical subject from Identity |
| Business fields | Category, resolution, assignee | Location, urgency, repair work order | Không thuộc Workflow domain |
| Workflow definition | it-helpdesk@1 | facility@1 (proposed) | Versioned definition mechanism |
| API | Helpdesk commands | Facility commands | BFF/Gateway cross-cutting patterns |
| History | Workflow transitions | Workflow transitions | Same persistence port semantics |

**Reuse proof:** hai application có controller, application use case, business data và real vertical journey; chỉ tạo hai WorkflowDefinition không đủ. Đưa Facility lên mà không sửa `core.workflow` source, generic policy/contracts và base adapter; mọi change Core phải được phê duyệt rõ là platform extension.

## 12. ARCHITECTURE OPTIONS & TRADE-OFF

| Lựa chọn | Ưu | Nhược | Đề xuất |
|---|---|---|---|
| Finite-state engine tối thiểu | Hiểu và kiểm chứng tốt | Không có timer/BPMN/parallel gateways | Giữ cho MVP |
| BPMN engine đầy đủ | Expressive, ecosystem | Cost, migration, ops | Không thay trước demo |
| Direct Core Runtime từ HTTP | Ít lớp | Bypass trust/resource policy | Reject |
| Authorized Application Facade | Bảo mật và reuse | Cần transactional policy integration | Adopt |
| Full CQRS + event sourcing | Audit/event mạnh | Dual models, complexity | Deferred |
| State+history relational atomic writes | Dễ chứng minh correctness | Gắn relational constraints | Adopt MVP |

## 13. PACKAGE DESIGN (target, not current)

```text
backend/.../dxos/
  core/workflow/{WorkflowDefinition,WorkflowInstance,TransitionCommand,...}
  core/workflow/{WorkflowRuntime,WorkflowPersistencePort,WorkflowErrorCode,...}
  application/workflow/{AuthorizedWorkflowUseCase,AuthorizedWorkflowService}
  application/authorization/{WorkflowAccessPolicy,WorkflowAuthorization}
  application/helpdesk/{HelpdeskUseCase,HelpdeskApplicationService,command,view}
  application/facility/{FacilityUseCase,FacilityApplicationService,command,view}
  gateway/workflow/{WorkflowHttpController,dto,WorkflowErrorHandler}
  gateway/helpdesk/{HelpdeskController,dto}
  gateway/facility/{FacilityController,dto}
  infrastructure/workflow/postgres/{PostgresWorkflowPersistence,...}
  infrastructure/helpdesk/postgres/{HelpdeskRepositories,...}
  bootstrap/{WorkflowConfiguration,ApplicationTransactionConfiguration}
```

Không bắt buộc đổi package hàng loạt. Trước tiên thay direct dependencies bằng ports được kiểm thử; chốt transaction semantics rồi mới tổ chức lại file.

## 14. IMPLEMENTATION / MIGRATION PHASES

| Phase | Công việc | Evidence / Exit Gate |
|---|---|---|
| WF-G0 Baseline | Pin SHA, inventory definition/state/API/SQL functions/tests | Review signed-off, build không sửa |
| WF-G1 Semantics freeze | Chốt 6 WF-ADR và port error/concurrency/authorization contracts | Contract matrix được duyệt |
| WF-G2 Transaction design | Chọn P1/P2, vẽ ownership Connection/commit/rollback, TOCTOU | Failure injection plan approved |
| WF-G3 Application facade | Controller -> authorized use case; tách dependency infrastructure | ArchUnit và targeted tests |
| WF-G4 PostgreSQL negative tests | Race, revoke, stale version, unknown outcome, rollback | JUnit XML từ exact SHA |
| WF-G5 Helpdesk vertical E2E | OIDC -> HTTP -> facts+history -> restart | Independent full journey |
| WF-G6 Facility reuse slice | App thứ hai dùng Core không sửa Core | Reuse evidence và tests |
| WF-G7 Release | Flyway/operator SQL, clean install, docs/API contract | Other machine demo PASS |

**Merge protection:** PR #47/#48 và #63 là stacked/draft evidence; không cherry-pick rời mất base/dependency. Stage 6 migration cần review độc lập, không bật LIVE nếu tests chưa có.

## 15. TEST ACCEPTANCE MATRIX (30 scenarios)

| ID | Scenario | Expected acceptance |
|---|---|---|
| WF-T01 | Unknown definition version | DEFINITION_NOT_FOUND, no write |
| WF-T02 | Immutable definition duplicate | Identical import idempotent if admin provision supports it; altered version rejected |
| WF-T03 | Create valid NOT_STARTED | Version 0, no history |
| WF-T04 | Duplicate instance ID | INSTANCE_ALREADY_EXISTS, no overwrite |
| WF-T05 | Activate valid instance | ACTIVE, version remains 0 |
| WF-T06 | Concurrent activation | One winner; stale proposal conflict |
| WF-T07 | Unknown transition ID | INVALID_TRANSITION, no write |
| WF-T08 | Transition wrong source state | TRANSITION_NOT_ALLOWED, no write |
| WF-T09 | Stale expected runtime version | VERSION_CONFLICT |
| WF-T10 | Parallel transitions same instance | One winner, contiguous history |
| WF-T11 | Matching transition history atomicity | State and exactly one matching record commit together |
| WF-T12 | Failure after state update before history | Rollback everything |
| WF-T13 | Connection loss after COMMIT | COMMIT_OUTCOME_UNKNOWN, no blind retry |
| WF-T14 | Authoritative rederive | Forged proposal cannot override official definition |
| WF-T15 | Exact timestamp values | No rounding by adapter |
| WF-T16 | Read history ordering | Monotone resulting runtime versions |
| WF-T17 | Missing instance history | INSTANCE_NOT_FOUND |
| WF-T18 | Completed lifecycle transition | INSTANCE_COMPLETED or appropriate contract error |
| WF-T19 | Anonymous protected endpoint | 401; no domain access |
| WF-T20 | Forged actor header | No identity escalation |
| WF-T21 | Missing exact operation authority | 403/no write |
| WF-T22 | Cross-tenant request | Denied and no data leak |
| WF-T23 | Resource hidden | 404 without exposing state/history |
| WF-T24 | Revocation and transition race | Consistent with specified lock ordering |
| WF-T25 | Assignee reassign vs resolve | Correct assignmentVersion guard, no stale resolve |
| WF-T26 | Helpdesk assignment command | No unintended workflow history/version increment |
| WF-T27 | Business facts + workflow write | One atomic PostgreSQL transaction |
| WF-T28 | Generic HTTP endpoint disabled by default | No registered mapping |
| WF-T29 | Facility reuse | Second real app, no Core source change |
| WF-T30 | Restart/clean install | Durable history and reproducible provision/migrations |

**Verification policy:** PASS chỉ được công bố khi lưu commit SHA, DB schema/migration, operator command, sanitized environment, JUnit XML, failure injection evidence, reviewer độc lập. Unit test không thay thế PostgreSQL race tests và real HTTP E2E. `COMMIT_OUTCOME_UNKNOWN` phải được thử bằng fault injection, không khẳng định chắc result chỉ từ một exception.

## 16. RISK REGISTER

| Risk | Severity | Mitigation / Owner suggested |
|---|---|---|
| PR #63 Application gọi trực tiếp guarded SQL functions | P0 | Backend: transaction abstraction và migration compatibility plan |
| Postgres adapter tự commit, no shared Spring transaction | P0 | Backend/Data: explicitly designed unit of work, atomicity tests |
| Stage 6 vs Application-enforced authorization | P0 | Security owner: inventory controls + negative/race tests |
| Raw Workflow API exposed without resource authorization | P0 | Gateway owner: default OFF until authorized port |
| Legacy Request JPA state machine parallel | P0 | Application owner: profile OFF, no parallel writable status |
| Facility app only definition, no real workflow | P1 | Application owner: second vertical slice |
| Test evidence on older SHAs | P1 | QA: exact integrated SHA, re-run independent |
| Migration scripts outside reproducible installer | P1 | Infra: clean install, Flyway/operator runbook |

## 17. REVIEW GATES & SIGN-OFF

- [ ] Architecture owner: WF-ADR-01..06 approved.
- [ ] Backend owner: SQL call ownership and transaction write diagram approved.
- [ ] Security reviewer: application policy, revocation/TOCTOU, DB privilege coverage approved.
- [ ] Application reviewer: Helpdesk lifecycle and Facility reuse contract approved.
- [ ] QA: contract tests, PostgreSQL fault tests, HTTP negative tests and clean install run at pinned SHA.
- [ ] Release lead: OpenAPI, deployment guide, LICENSE and evidence index verified.

**Status:** PROPOSED. Không có thay đổi mã nguồn/PR và chưa có kết quả nghiệm thu từ các ca test tương lai.
