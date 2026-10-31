---
title: DX-OS Open-Core — Workflow Acceptance Tests
document_id: G4-F16
version: 1.0-review-draft
status: DRAFT — NOT REVIEWED
source_commit: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
owner: Workflow / Application / Data / Security — to assign
reviewers: Independent G4 reviewer — pending
---

# Workflow Core — Acceptance Tests and Evidence Specification

> **Evidence policy:** **OBSERVED** = inspected Java/source-contract text at pinned SHA; **SELECTED** = the 18 user-approved G4-F16 decisions; **PROPOSED** = runnable acceptance requirement awaiting proof; **HISTORICAL** = previously reported executions in a canonical contract at their own recorded baselines; **CURRENT VERIFIED** requires execution logs and independent exact-SHA review. **No tests were executed to author this document. No PASS or G4 approval is assigned.**

## 1. Scope, source hierarchy and acceptance purpose

This specification defines **what would count as evidence** for reusable Workflow Core: domain behavior, the six port operations, real PostgreSQL atomicity, failure recovery, security guards, Helpdesk/Facility reuse, and reproducibility. It does **not** modify the Core implementation, promote drafts into canonical contracts, certify a persistence adapter or invent test outcomes. Contract changes require a separately approved ADR.

Pinned source commit: `0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`. Canonical Workflow contract: [docs/contracts/workflow-core.md](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/workflow-core.md), blob `11c5aaca1e7e08394710a6d0c5fe8e187d549d83`. Source surfaces: [WorkflowPersistencePort.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowPersistencePort.java), blob `5009b191a3eb555ba3248eda4728ab8d3aac251b`; [WorkflowRuntime.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowRuntime.java), blob `3b7e7ac755f0021b6ef0ea5a572748bdca585ff2`; [WorkflowInstance.java](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/workflow/WorkflowInstance.java), blob `297315a7a9ed250c1e5a11acde3ccdbc339109a2`.

**Documentation-workspace draft references, NOT tracked at this pinned GitHub commit:** [README.md](README.md) (G4-F09), [architecture.md](architecture.md) (G4-F10 revised), [domain-model.md](domain-model.md) (G4-F11), [contracts.md](contracts.md) (G4-F12), [state-machine.md](state-machine.md) (G4-F13), [persistence.md](persistence.md) (G4-F14), [authorization.md](authorization.md) (G4-F15). These relative links assume the complete 8-file documentation tree is assembled before commit. They are local draft references, not GitHub source permalinks.

## 2. Evidence states, counting and independent acceptance rules

| State | Usage; not interchangeable |
| --- | --- |
| `NOT RUN` | Test defined but no matching execution evidence at the stated revision/environment |
| `BLOCKED` | Prerequisite absent; record issue owner, unblock condition, deadline and affected Gate |
| `PASS` | Executed assertion met with logs, exit code, baseline and reviewer; not inferred from design |
| `FAIL` | Executed assertion violated; include minimized repro, actual state/history and severity |
| `INCONCLUSIVE` | Execution occurred but signal cannot establish assertion; rerun with improved instrumentation |
| `HISTORICAL` | Dated prior result reported in source; does not count as current exact-SHA PASS |

**Current status for all proposed AT cases in this document:** `NOT RUN HERE`, except the TOCTOU case which is also **DESIGN BLOCKED**. At the Gate, individual owners must distinguish tests proven elsewhere at this SHA from tests executed in this authoring session; references must be inspected before upgrading any status.

A `PASS` record needs the exact commit/dirty status, test selector, executed assertions, JUnit XML or authoritative results, command and exit code, environment, artifact integrity and a named independent reviewer. **P0 criteria:** any unauthorized mutation, partial state/history write, two activation or transition winners, hidden idempotent replay, forged authoritative definition, silently altered accepted Instant or unsafe blind retry is a release blocker.

## 3. Historical test evidence — do not promote to current proof

| Source-reported milestone | Focused Workflow | Full backend | Status in this document |
| --- | --- | --- | --- |
| 2026-10-07 (original contract verification, JDK 21.0.11) | 29 tests / 4 suites, source reports PASS | 139 / 12 suites, source reports PASS | `HISTORICAL` only |
| 2026-10-10 (milestone 1 reviewed head `a75073c`, JDK 21.0.11) | 43 tests / 4 suites, source reports PASS | 208 / 19 suites, source reports PASS | `HISTORICAL` only |

The canonical contract describes `InMemoryWorkflowPersistence`, concurrency fixtures and simulated lost responses. **Its fake does not establish real PostgreSQL isolation, durability, rollback, timestamp encoding or process crash recovery.** This specification requests separate durable DB and E2E proof. The historical figures above are source-derived reports, not re-executed or independently certified here.

## 4. Acceptance layers and traceability method

| Gate | Design intent | Required evidence | Disposition now |
| --- | --- | --- | --- |
| G4-D Design | F09–F16 consistent, no incompatible invariant | Versioned review checklists, decision register, open-issue dispositions | DRAFT / NOT REVIEWED |
| G4-S Source | Canonical Java/port/contract exact-SHA match | Source inventory + signatures + source-diff reports | PARTIAL SOURCE INSPECTION |
| G4-U Unit | Pure domain valid and negative behavior | JUnit selectors, XML, assertions, time/version counterexamples | NOT RUN HERE |
| G4-C Contract | Six port-method semantic conformance | Fake and candidate-adapter suites with origin/field comparison | NOT RUN HERE |
| G4-I Real DB | PostgreSQL atomicity, version races, rollback | Isolated DB integration report + state/history + bounded race evidence | NOT RUN HERE |
| G4-F Failure | Unknown COMMIT, reconciliation, no blind retry | Fault-injection logs and bounded authoritative reads | NOT RUN HERE |
| G4-T Security | Verified caller, authority, resource ACL, no bypass | HTTP negatives + durable no-mutation proof | NOT RUN HERE |
| G4-R Reuse | Helpdesk/Facility genuinely different policy slices | Two-App E2E and unchanged Core diff | NOT RUN HERE |
| G4-O Operations | Reproducible tests/build/install/cleanup | Environment manifest, full suite XML, release scripts | NOT RUN HERE |
| G4-Ix Independent | Real reviewer evaluated whole evidence | Signed review verdict, P0 blocker list, evidence checksums | NOT APPROVED |

`G4-Ix` is this documentation's proposed independent-review label; it does not rename any existing official project Gate.

## 5. Acceptance case schema and test catalogue

**Mandatory test card schema:** `ID → Requirement → Preconditions/Setup → Procedure/Steps → Positive & negative assertions → No-side-effect assertions → Evidence links → Status → Reviewer → Gate`. Each catalogue row below is a test design *summary*, not an execution record. Each must be expanded into a runnable named test or manual scripted procedure at execution time.

| ID | Required behavior / setup and action | Explicit assertions / negative side effects | Evidence | Reviewer | Status |
| --- | --- | --- | --- | --- | --- |
| `AT-DOM-01` | **Definition validation and immutability** (`F11, F13`); arrange authoritative fixture and invoke relevant operation | Construct valid graph; reject blank IDs, missing initial, duplicate transition IDs, missing endpoints; prove copied sets cannot be mutated; rejected attempts do not mutate own instance/history | WorkflowDefinition and WorkflowTransition source + JUnit XML | Core | `NOT RUN` |
| `AT-DOM-02` | **Lifecycle and activation** (`F11, F13`); arrange authoritative fixture and invoke relevant operation | Create NOT_STARTED v0 at initial state with equal timestamps; activate ACTIVE v0 without history; terminal initial activation rejected; rejected attempts do not mutate own instance/history | Before/after snapshots, JUnit XML | Core | `NOT RUN` |
| `AT-DOM-03` | **Transition mapping / terminal** (`F11, F13`); arrange authoritative fixture and invoke relevant operation | Use transitionId and exact pinned definition, verify resulting target and COMPLETED on terminal; reject wrong source, unknown transition, completed replay; rejected attempts do not mutate own instance/history | TransitionResult/record assertion and negative XML | Core | `NOT RUN` |
| `AT-DOM-04` | **Version, time, overflow** (`F11, F13`); arrange authoritative fixture and invoke relevant operation | Reject mismatched expectedVersion; reject backward Instant; permit tied Instants; test Long.MAX_VALUE overflow OPERATION_FAILURE; rejected attempts do not mutate own instance/history | JUnit XML with error codes and immutable original | Core | `NOT RUN` |
| `AT-PORT-01` | **loadDefinition exact authority** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Version zero allowed; exact key absent rejected; altered same-key content prohibited by provisioning; no latest fallback; rejected attempts do not mutate own instance/history | Fixture + source contract / adapter read outputs | Workflow/Data | `NOT RUN` |
| `AT-PORT-02` | **createInstance insert-only** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Successful authoritative NOT_STARTED v0 empty history; duplicate even identical replay rejected before missing-definition; concurrent create one winner; rejected attempts do not mutate own instance/history | DB rows/history + concurrent trace | Data | `NOT RUN` |
| `AT-PORT-03` | **commitActivation origin CAS** (`F12, F13, F14`); arrange authoritative fixture and invoke relevant operation | Full value-field compare plus lifecycle, valid restored-equal snapshot; replay and concurrent v0 activations one winner; no history; rejected attempts do not mutate own instance/history | Race latches, DB snapshots, SQL/transaction logs sanitized | Data | `NOT RUN` |
| `AT-PORT-04` | **commitTransition re-derivation** (`F12, F13, F14`); arrange authoritative fixture and invoke relevant operation | Commit-time expectedVersion and authoritative definition; forged same-key target/history/actor mismatch rejected, repeated proposal conflicts; rejected attempts do not mutate own instance/history | Exact persisted rows, source/record compare, XML | Data | `NOT RUN` |
| `AT-PORT-05` | **loadInstance/loadHistory** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Missing instance error; immutable contiguous per-instance history from 1 ordered by resulting version, tied timestamps permitted; separate reads not shared snapshot; rejected attempts do not mutate own instance/history | Two read outputs + transaction semantics report | Data | `NOT RUN` |
| `AT-DB-01` | **Real DB transition race** (`F13, F14`); arrange authoritative fixture and invoke relevant operation | Two bounded writers on v3; exactly one version-4 winner, loser VERSION_CONFLICT, one version-4 history, no partial rows; rejected attempts do not mutate own instance/history | PostgreSQL actual test reports, PID/gates where available | Data | `NOT RUN` |
| `AT-DB-02` | **Real DB activation race** (`F13, F14`); arrange authoritative fixture and invoke relevant operation | Two concurrent activations at version 0; exactly one winner, one VERSION_CONFLICT, final ACTIVE v0, no history; rejected attempts do not mutate own instance/history | PostgreSQL race trace + final snapshot | Data | `NOT RUN` |
| `AT-DB-03` | **Atomic rollback** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Inject failure after snapshot update/before history; confirmed rollback changes neither; enforce unique history version; rejected attempts do not mutate own instance/history | Transaction fault trace and before/after DB | Data | `NOT RUN` |
| `AT-FAIL-01` | **Unknown create outcome** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Commit creation and lose response; return COMMIT_OUTCOME_UNKNOWN; reconcile, identical stored instance not proof of writer; rejected attempts do not mutate own instance/history | Fault-injection with unknown outcome and authoritative reads | Data/Ops | `NOT RUN` |
| `AT-FAIL-02` | **Unknown activation outcome** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Commit activation v0 and lose response; full lifecycle/full-snapshot reconciliation; no automatic retry; rejected attempts do not mutate own instance/history | Fault-injection and authoritative snapshot/history | Data/Ops | `NOT RUN` |
| `AT-FAIL-03` | **Unknown transition outcome** (`F12, F14`); arrange authoritative fixture and invoke relevant operation | Commit state+history and lose response; locate exact record at proposed version; later progress handled; no blind retry; rejected attempts do not mutate own instance/history | Fault-injection, before/after, reconciliation decision | Data/Ops | `NOT RUN` |
| `AT-DB-04` | **Exact Instant storage** (`F11, F14`); arrange authoritative fixture and invoke relevant operation | Nanos in fake, PostgreSQL accepted microsecond-aligned round trip or lossless storage, reject unrepresentable precision before mutation; rejected attempts do not mutate own instance/history | DB readbacks and format/time-zone tests | Data | `NOT RUN` |
| `AT-SEC-01` | **Authentication and exact authority** (`F15`); arrange authoritative fixture and invoke relevant operation | Anonymous/expired/missing exact authority denied before mutation; unrelated authority cannot imply grant; rejected attempts do not mutate own instance/history | HTTP negatives, DB invariant proof | Security | `NOT RUN` |
| `AT-SEC-02` | **Actor/header spoofing and bypass** (`F15`); arrange authoritative fixture and invoke relevant operation | Forged actor and X-User/X-Role/X-Actor-Id denied/ignored, direct route inventory has no unauthorized mutation bypass; rejected attempts do not mutate own instance/history | Route map, HTTP traces, DB before/after | Security | `NOT RUN` |
| `AT-SEC-03` | **IDOR/resource eligibility** (`F15`); arrange authoritative fixture and invoke relevant operation | Helpdesk unassigned technician and Facility ineligible approver denied read/mutation; no successful history record; rejected attempts do not mutate own instance/history | Policy fixtures, HTTP tests and persisted rows | Application/Security | `NOT RUN` |
| `AT-SEC-04` | **TOCTOU assignment race** (`F15`); arrange authoritative fixture and invoke relevant operation | Concurrent assignee change and transition controlled by separately approved consistency rule; otherwise BLOCKED; rejected attempts do not mutate own instance/history | Bounded race test and ADR | Application/Data | `BLOCKED: design` |
| `AT-REUSE-01` | **Helpdesk reused Core** (`F09, F10, F15`); arrange authoritative fixture and invoke relevant operation | Helpdesk authorized end-to-end slice and negative policy cases, pinned Core unchanged; rejected attempts do not mutate own instance/history | Git diff, HTTP+DB test report | Application | `NOT RUN` |
| `AT-REUSE-02` | **Facility reused Core** (`F09, F10, F15`); arrange authoritative fixture and invoke relevant operation | Facility independent workflow definition/policy, authorized slice and negatives with same Core SHA; rejected attempts do not mutate own instance/history | Git diff, HTTP+DB report | Application | `NOT RUN` |
| `AT-OPS-01` | **Build, regressions, clean install** (`F09, F10, F12`); arrange authoritative fixture and invoke relevant operation | Reproducible JDK/Gradle full tests, CI, clean install; include command exit and pinned SHA; rejected attempts do not mutate own instance/history | JUnit XML, build logs, environment and cleanup | Backend/DevOps | `NOT RUN` |
| `AT-GATE-01` | **Independent acceptance** (`F09–F15`); arrange authoritative fixture and invoke relevant operation | All mandatory cases have reproducible evidence, blocker disposition, independent reviewer signed decision; rejected attempts do not mutate own instance/history | Evidence index + review record | Independent reviewer | `NOT RUN` |

## 6. Three high-priority executable scenario specifications

### AT-DB-01 — Same-version concurrent transition

**Requirement:** `WorkflowPersistencePort.commitTransition()` must apply optimistic concurrency at the durable write boundary. **Setup:** authoritative ACTIVE instance version `3`, a valid outgoing transition and exactly three existing success records with versions `1..3`; two independent requests with expectedVersion `3` and bounded coordination gates, real disposable PostgreSQL. **Steps:** load snapshots in both paths, prepare competing proposals, hold the actual write/transaction at a known synchronization point, release both and collect provider outcomes and persisted records; cap every latch/connection/statement with test timeouts and clean up in `finally`.

**Expected:** exactly one confirmed winner commits runtimeVersion `4` and exactly one matching history record at version `4`. The stale loser raises `VERSION_CONFLICT` (not a second success); previous records unchanged; neither partial update nor extra history; no leaked holder/connection. **Evidence:** JUnit XML, source SHA, bounded gate trace, connection/transaction evidence, before/after SQL snapshots and cleanup record. **Status:** `NOT RUN`.

### AT-DB-02 — Two activations both start from version 0

**Requirement:** `commitActivation` checks *full expected snapshot and lifecycle*, rather than only runtimeVersion. **Setup:** same NOT_STARTED snapshot at version `0`; two concurrent activation proposals; valid nonterminal initial state. **Steps:** use two real independent transactions with bounded commit synchronization, observe outcomes and read authoritative snapshot/history. **Expected:** exactly one activation succeeds; the other reports `VERSION_CONFLICT` for stale original lifecycle; final lifecycle ACTIVE and runtimeVersion still `0`; history remains empty; reactivation from the *current* ACTIVE snapshot is `TRANSITION_NOT_ALLOWED`. **Evidence:** real DB tests, detailed lifecycle before/after fields and no-side-effect proof. **Status:** `NOT RUN`.

### AT-FAIL-03 — Lost COMMIT acknowledgement after transition

**Requirement:** `COMMIT_OUTCOME_UNKNOWN` must not become confirmed rollback or automatic retry. **Setup:** successful state/history transaction, then simulate loss of connection/response *after durable COMMIT but before caller acknowledgement*; retain attempted `TransitionResult`. **Steps:** inject at that exact phase, record normalized failure, perform bounded authoritative `loadInstance` and `loadHistory`, compare the exact result version, actor, transition ID, source/target and timestamp; allow concurrent later progress. **Expected:** caller receives `COMMIT_OUTCOME_UNKNOWN`; committed state and history are both present, or the result remains unresolved if authoritative observations are insufficient; no duplicate write and no blind retry. **Counter-scenario:** confirmed rollback must map `OPERATION_FAILURE` and neither record nor state should change due to the attempt. **Evidence:** fault hook location, DB COMMIT trace, independent readback, decision log and XML. **Status:** `NOT RUN`.

## 7. Negative test oracles and operation-specific rules

| Test focus | Error/condition oracle | Forbidden side effect |
| --- | --- | --- |
| Duplicate create, including identical retry | `INSTANCE_ALREADY_EXISTS` before missing-definition lookup | No overwrite, no new history |
| Missing exact immutable definition | `DEFINITION_NOT_FOUND`; no latest fallback | No implicit definition upgrade |
| Stale transition before lifecycle test | `VERSION_CONFLICT` after exact definition/instance checks | No proposed history durably appended |
| Missing authoritative transition ID | `INVALID_TRANSITION` | No fabricated target or history |
| Wrong source, forged target or inconsistent origin | `TRANSITION_NOT_ALLOWED` | No persisted state/history divergence |
| Transition on terminal snapshot with matching version | `INSTANCE_COMPLETED` | No reopen or history append |
| Runtime version `Long.MAX_VALUE` overflow | `OPERATION_FAILURE` | No wraparound or new record |
| Replayed activation at version zero | `VERSION_CONFLICT` from lifecycle mismatch | No double activation/history |
| Activation target timestamp before previous updatedAt | Structural rejection in domain, and `TRANSITION_NOT_ALLOWED` per port activation contract | No persisted activation |
| Provider confirmed no-commit/rollback | `OPERATION_FAILURE` | Nothing from attempted write changes |
| Outcome ambiguous after attempted COMMIT | `COMMIT_OUTCOME_UNKNOWN` | No assumption of rollback, no blind retry; never partial state/history |
| Forged auth header or IDOR | Transport/Application denial, not invented Workflow auth error | No successful transition record |

**Error precedence:** create checks duplicate instance ID before definition lookup; `WorkflowInstance.transition()` checks exact binding and instance identity before version, then lifecycle, transition and time. `commitActivation()` must apply the ordered seven-step sequence in the canonical contract (missing ID → version/lifecycle mismatch → completed → other source mismatch → missing definition → bad target → provider outcome). Do not fabricate a universal total precedence order for every possible adapter `commitTransition()` multi-invalid scenario.

## 8. Cross-file G4-F09–F15 requirements → test IDs → Gate

| Workspace document | Requirement slice (draft, not accepted) | Test IDs | Gate |
| --- | --- | --- | --- |
| G4-F09 `README.md` | Generic reusable Workflow and two-App reuse proof | AT-REUSE-01/02, AT-GATE-01 | G4-R, G4-Ix |
| G4-F10 `architecture.md` | Logical modular-monolith runtime/port seam, provider substitution | AT-PORT-01/05, AT-OPS-01, AT-GATE-01 | G4-S, G4-C, G4-O |
| G4-F11 `domain-model.md` | Immutable definition/instance, lifecycle, transition record, error semantics | AT-DOM-01/02/03/04 | G4-U |
| G4-F12 `contracts.md` | Six operations, exact binding, commit outcome and error precedence | AT-PORT-01/02/03/04/05, AT-FAIL-01/02/03 | G4-C, G4-F |
| G4-F13 `state-machine.md` | Terminal rules, version race and activation v0 | AT-DOM-02/03/04, AT-DB-01/02 | G4-U, G4-I |
| G4-F14 `persistence.md` | Atomic history, durable rollback, precision and provider parity | AT-DB-01/02/03/04, AT-FAIL-01/02/03 | G4-I, G4-F |
| G4-F15 `authorization.md` | Verified identity, exact authority, resource ACL, IDOR, TOCTOU | AT-SEC-01/02/03/04, AT-REUSE-01/02 | G4-T, G4-R |

Existing F09–F15 scenario IDs (e.g., DM/CT/SM/PS/WA) belong to draft specifications; the AT identifiers form an acceptance index, not an assertion those earlier tests were executed. Link actual test class/method and artifact checksum when verification occurs.

## 9. Environment, reproducibility and evidence index

**Proposed command set from the canonical Workflow contract** (Windows PowerShell, repository `backend/`, JDK 21; verify current test names and Gradle config before use):

```powershell
cd backend
.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*' --rerun-tasks
.\gradlew.bat test --tests 'com.tricore.dxos.request.*' --rerun-tasks
.\gradlew.bat test --rerun-tasks
.\gradlew.bat build
```

These are **examples not executed here**. For real DB, fault injection and E2E security suites, exact invocation scripts and DB fixtures must be identified from their implementation before running. Never claim a guessed test selector is real. Use disposable database with bounded resource cleanup and no production credentials.

### Evidence manifest template (no fabricated paths or runs)

| Field | Required real value / rule |
| --- | --- |
| `evidence_id`, `test_id`, `gate` | Unique IDs and linked acceptance rows |
| `repo`, `commit`, `branch`, `working_tree` | Exact full SHA and dirty-state, clean install baseline where relevant |
| `test_class` / `selector` / `command` | Existing test path, actual invocation and working directory |
| `jdk`, `gradle`, `db`, `adapter`, `os` | Actual versions, config/precision and disposable DB setup |
| `started_at`, `ended_at`, `exit_code` | Timestamps and actual process result |
| `tests_run`, `failures`, `errors`, `skipped` | Parsed JUnit counts or other authentic suite totals |
| `expected`, `observed`, `side_effect_oracle` | Assertions with authoritative state/history evidence |
| `logs_uri`, `junit_xml_uri`, `db_proof_uri` | Verified artifact locations, sanitized |
| `cleanup`, `fault_injection_phase` | Gate release, transaction rollback/termination and DB cleanup |
| `reviewer`, `verdict`, `review_time` | Independent signed disposition and observed limitations |

**Evidence index placeholder:** no logs, JUnit XML, DB proof, CI URL, alternative-adapter report or reviewer signature has been attached or generated by this authoring step. Populate only after verified collection. Broken evidence hyperlinks must not be invented.

## 10. P0 blockers, FAIL/INCONCLUSIVE rules and Gate disposition

**Blockers:** loss of durable atomicity; two confirmed winners for same-version transition or version-zero activation; acceptance of forged definition/actor/resource authority; denied request mutating workflow; failed snapshot/history invariants; unsafe outcome-unknown retry; unrepresentable `Instant` silently rounded; nonconformant alternate adapter; missing independent review.

- `FAIL` if an executed test shows a violated mandatory invariant; attach smallest reproducible case and block corresponding Gate pending fix and rerun.
- `INCONCLUSIVE` if missing authoritative connection attribution, unbounded race, test timeout without proof, absent complete readback, or unresolved commit outcome makes the assertion unverifiable; do not coerce to either success or rollback.
- `BLOCKED` if an adapter, approval, environment or policy consistency ADR needed by a test does not exist; identify owner and unblock dependency. A blocked P0 item prevents final signoff.
- `PASS` applies to an individual test only with actual execution evidence and reviewer verification. Passing a fake adapter alone cannot move G4-I or G4-F to PASS.
- `HISTORICAL` is kept for old documented results; it does not satisfy an exact-SHA current Gate without current artifact review and compatibility confirmation.

**P0 decision rule:** independent G4 acceptance is prohibited while a mandatory P0 scenario is `FAIL`, `BLOCKED`, `INCONCLUSIVE`, `NOT RUN` or only `HISTORICAL`, unless a formally authorized and documented scope change removes that requirement through review. This document provides no such authorization.

## 11. Independent reviewer record template

| Field | Entry required at review |
| --- | --- |
| Reviewer and review date | **PENDING** — identity/time not supplied |
| Exact source SHA and evidence manifest ID | **PENDING** — no artifact package |
| G4-D/S/U/C/I/F/T/R/O/Ix verdict | **PENDING** — do not infer acceptance |
| P0 findings and resolution links | **PENDING** |
| Outstanding P1/P2 and accepted limitations | **PENDING** |
| Final disposition | **NOT REVIEWED / NOT APPROVED** |

**Final authoring status: `G4-F16 DRAFT / NOT REVIEWED`; no runtime test, PostgreSQL fault injection, Helpdesk/Facility E2E or independent Gate was performed for this package.**
