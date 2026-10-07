# Workflow Core POC — Session 1

## Mục đích

POC kiểm chứng mô hình và contract **Workflow Core generic, độc lập provider**
của TRICORE, đáp ứng phần nghiên cứu và chạy/test một capability trong bài tập
Session 1 được giao. Đây là kiểm chứng code hiện có trên main, không phải POC
chọn hoặc kiểm chứng một Workflow engine bên thứ ba.

Task tài liệu: [Issue #16](https://github.com/lexuanbao258-collab/dxos-open-core/issues/16).
Code: [Issue #13](https://github.com/lexuanbao258-collab/dxos-open-core/issues/13),
đã merge qua [PR #15](https://github.com/lexuanbao258-collab/dxos-open-core/pull/15).
Baseline: `2b51a41fdb372edd9e00ac05dac8ccc9a52d738c`.
Chi tiết API/invariants: [Workflow contract](../contracts/workflow-core.md).

## Nghiên cứu và phạm vi kiểm chứng

Thiết kế giữ definition và runtime instance riêng để instance gắn với đúng phiên
bản definition. Caller chọn `transitionId`; definition quyết định đích, tránh
đưa quyền set state trực tiếp vào command. `expectedVersion` hỗ trợ kiểm tra
optimistic concurrency; state/history được mô tả là một commit nguyên tử qua
port do Workflow sở hữu. Đây là cơ chế generic, không sao chép lifecycle Request.

Các quyết định này được thể hiện trong contracts, domain code và test assertions,
không dựa trên việc lựa chọn BPMN/provider. Không có khảo sát engine bên thứ ba
hoặc kết luận production readiness trong POC này.

| Concept | Code / điều kiểm chứng |
| --- | --- |
| WorkflowDefinition / WorkflowTransition | Definition ID/version, initial state, state set, transitions với ID/source/target; membership, ID uniqueness và defensive copies |
| WorkflowInstance | ResourceReference generic, exact definition binding, current state, lifecycle, runtimeVersion và timestamps; immutable snapshot |
| TransitionCommand | instanceId, transitionId, ActorReference, expectedVersion; không có target state/new version/final lifecycle |
| TransitionResult | Previous/updated snapshot và record nhất quán; là proposal trước commit, không tự ghi persistence |
| TransitionRecord | Successful transition ID/source/target/actor/result version/time; không lưu failed attempts |
| WorkflowLifecycle | NOT_STARTED → ACTIVE → COMPLETED; terminal state không có outgoing transitions; không reopen |
| WorkflowPersistencePort | loadInstance, loadHistory, commitTransition; version check, state/version update và history append cùng một logical operation |
| Normalized errors | Definition/instance absent, invalid/not-allowed transition, completed instance, version conflict, operation failure; không chứa provider causes/HTTP codes |

Nguồn production: [core.workflow](../../backend/src/main/java/com/tricore/dxos/core/workflow/).
Nguồn tests: [Workflow tests](../../backend/src/test/java/com/tricore/dxos/core/workflow/).

## Cách chạy

Windows/PowerShell, JDK 21 và Gradle Wrapper của repository. Thiết lập
`JAVA_HOME` theo JDK trên máy; ví dụ máy kiểm chứng có JDK 21.0.11 tại
`C:\Program Files\Java\jdk-21.0.11`. Lần đầu cần Internet tải Gradle/dependencies.
Không cần database, Spring context cho tests Core, Identity provider hoặc
Workflow provider. Request MVC tests có Spring slices nhưng không cần database.

Từ repository root:

```powershell
Set-Location backend
java -version
.\gradlew.bat --version
.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*'
.\gradlew.bat test --tests 'com.tricore.dxos.request.*'
.\gradlew.bat test
.\gradlew.bat build
Set-Location ..
git diff --check
```

`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` chỉ cần khi chạy
toàn bộ backend với PostgreSQL. Xem [run instructions](../../backend/README.md).
Tác vụ này không chạy `bootRun` hoặc thao tác database.

## VERIFIED NOW — kết quả chạy lại

Chạy lại ngày **2026-10-07**, Windows 11, Oracle JDK **21.0.11**, Gradle Wrapper
**8.14.3**, Spring Boot baseline **3.5.16**. Nhánh `docs/session-1-homework`
chỉ sửa tài liệu; code được kiểm chứng là baseline main nêu trên.

| Command (backend/ nếu không ghi khác) | Kết quả thực tế | Số lượng |
| --- | --- | --- |
| `java -version` | PASS: Java 21.0.11 | Không áp dụng |
| `.\gradlew.bat --version` | PASS: Gradle 8.14.3, JVM 21.0.11 | Không áp dụng |
| `.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*'` | PASS; task :test thực sự chạy | 29 tests / 4 suites |
| `.\gradlew.bat test --tests 'com.tricore.dxos.request.*'` | PASS; task :test thực sự chạy | 109 tests / 7 suites |
| `.\gradlew.bat test` | PASS; task :test thực sự chạy | 139 tests / 12 suites |
| `.\gradlew.bat build` | PASS; các task UP-TO-DATE sau test; không phải clean rebuild | Không áp dụng |
| `git diff --check` (repository root) | PASS | Không áp dụng |

Mỗi lượt test có **0 failures, 0 errors, 0 skipped**. Số liệu được đọc ngay
sau từng lệnh từ `backend/build/test-results/test/TEST-*.xml`, không suy ra từ
số annotation hoặc sao chép báo cáo cũ. Full run gồm 29 Workflow, 109 Request
và 1 health test. HTML report local: `backend/build/reports/tests/test/index.html`
(báo cáo generated không commit).

Build kiểm tra thành công các artifact hiện có:
`dxos-backend-0.0.1-SNAPSHOT.jar` (57,566,833 bytes) và
`dxos-backend-0.0.1-SNAPSHOT-plain.jar` (46,130 bytes).
Các lệnh Gradle chạy với quyền truy cập cache/dependencies ngoài sandbox;
không sửa build/config để đạt PASS. JVM có cảnh báo class-data-sharing khi
Mockito gắn bootstrap classpath; test/build vẫn thành công.

Import review bằng `rg` xác nhận production Core chỉ import `java.*`; không
tìm thấy imports Spring/JPA/JDBC/Hibernate/PostgreSQL/Request/infrastructure/Identity.
Scope review xác nhận chỉ tài liệu thay đổi.

## Điều được chứng minh bởi code/tests

| Claim | Test / bằng chứng |
| --- | --- |
| Hai workflow không dùng Request states | WorkflowDefinitionTest.approval: publication `draft → review → published`; delivery: `queued → dispatched`; WorkflowInstanceTest.followsTwoIndependentLifecyclesWithoutBusinessSpecificStates |
| Execution bằng transitionId, không nhập destination | WorkflowDefinitionTest.mapsTransitionByIdAndDerivesTerminalState; WorkflowContractTest.commandCannotSupplyTargetStateLifecycleOrNewVersion |
| Invalid ID/source mismatch không đổi snapshot | WorkflowInstanceTest.rejectsUnknownTransitionAndSourceMismatchWithoutChangingSnapshot |
| Exact definition ID/version được pin | rejectsAnotherDefinitionIdOrVersion; pinsDefinitionVersionSeparatelyFromIncrementedRuntimeVersion |
| Runtime version tăng đúng một lần; history nhất quán | WorkflowInstanceTest.pinsDefinitionVersionSeparatelyFromIncrementedRuntimeVersion; WorkflowContractTest.rejectsHistoryThatDisagreesWithTransitionSnapshots |
| expectedVersion và conflict/replay protection | WorkflowInstanceTest.rejectsVersionConflictAndWrongInstance; WorkflowPersistencePortTest.rejectsStaleConcurrentProposalAndReplayWithoutChangingStateOrHistory |
| Hai writer cùng version chỉ có một winner trong fake | WorkflowPersistencePortTest.concurrentCommitsHaveExactlyOneWinner |
| Failure không lưu dở state/history trong fake | operationFailureLeavesBothStateAndHistoryUnchangedAndAllowsExplicitRetry; failureOnLaterTransitionPreservesAlreadyCommittedHistory |
| Terminal instance được bảo vệ | WorkflowInstanceTest.protectsCompletedInstanceAgainstTransitionsAndReopen; WorkflowPersistencePortTest.invalidAndTerminalAttemptsDoNotAppendHistory và portProtectsTerminalSnapshotEvenAgainstReconstitutedActiveProposal |
| History có thứ tự xác định dù timestamp trùng | WorkflowPersistencePortTest.ordersHistoryByUniqueRuntimeVersionEvenWhenTimestampsTie; comparator theo resultingRuntimeVersion |
| Provider independence | Rà soát imports core.workflow: chỉ Java standard library; không Spring/JPA/JDBC, Request, infrastructure, Identity hay SDK provider |

`definitionVersion` mô tả phiên bản definition; `runtimeVersion` đếm successful
transitions. `(definitionId, definitionVersion)` phải trỏ tới nội dung bất biến
trong definition store tương lai. Domain chỉ kiểm tra key; caller/upstream phải
cung cấp exact definition có nguồn tin cậy. ActorReference cũng là tham chiếu
được upstream cung cấp, không tự xác thực.

`restore(...)` phục vụ adapter reconstitution, không ghi state vào database.
Port phải so previous snapshot với dữ liệu authoritative. `activate(...)` là
initialization ở version zero; persistence cho create/activate và registry vẫn deferred.

## Điều POC không chứng minh — PLANNED / DEFERRED

- PostgreSQL transactional atomicity, isolation và rollback khi database thật lỗi.
- Process crash recovery hoặc durability.
- Distributed execution và production readiness.
- Hành vi của external Workflow engine.
- Gateway integration và trusted-context propagation.
- Identity provider integration.
- Chuyển Request prototype sang generic Workflow Core.

Fake `InMemoryWorkflowPersistence` chỉ nằm trong test source. `synchronized`
và single map replacement mô phỏng logical atomicity; không tương đương một
transaction PostgreSQL. Existing Request transaction tests cũng dùng mocks.
Không có adapter production, SQL/migration mới hay provider mới trong POC này.

## Git development evidence

Workflow đã có ba checkpoint thực tế, giữ nguyên lịch sử:

| Commit | Nội dung |
| --- | --- |
| d6d6b8b | feat(workflow): define generic workflow models and transition contracts |
| 56f5837 | feat(workflow): define persistence port and atomic transition contract |
| 971f6dd | test(workflow): verify contract invariants and document persistence guarantees |

PR #15 đã merge vào main ngày 2026-10-07; Issue #13 CLOSED. Đây là evidence
đã triển khai/kiểm chứng contracts, không phải đã tích hợp toàn bộ Workflow runtime.
Kết quả và coverage Session 1: [evidence](../evidence/session-1-homework.md).
