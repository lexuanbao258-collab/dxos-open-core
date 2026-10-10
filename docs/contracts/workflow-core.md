# Workflow Core contracts — Issue #13

Owner: Lê Xuân Bảo. Persistence reviewer: Phan Ngọc Ánh.
Actor/trust reviewer: Phan Hải Yến.

## Responsibility and dependency direction

`com.tricore.dxos.core.workflow` owns reusable workflow models, controlled
transition invariants, normalized errors and `WorkflowPersistencePort`.
The project remains a Spring Boot modular monolith. Logical capabilities do
not select deployment topology or a workflow provider.

```text
External/Application -> Gateway -> Identity
                           |
                           +-> Workflow -> WorkflowPersistencePort
                                               ^
                                               |
                                      Data/persistence adapter (later)
```

Workflow imports only Java standard-library types. It does not call Identity,
depend on Request, or import infrastructure, Spring, JPA, JDBC or provider SDKs.
Data will implement the Workflow-owned port; it must not redefine transitions.
The existing Request prototype, IT Support lifecycle and V1/V2 remain unchanged.

## Domain types

| Type | Responsibility |
| --- | --- |
| `WorkflowDefinition` | Immutable definitionId, definitionVersion, initialState, states and transitions; defensive set copies |
| `WorkflowTransition` | Unique transitionId within a definition; sourceState and targetState must belong to states |
| `WorkflowInstance` | Immutable runtime snapshot with instanceId, exact definition binding, resource reference, currentState, runtimeVersion, lifecycle, createdAt and updatedAt |
| `ResourceReference` | Opaque resourceType/resourceId; no business entity or database identifier type |
| `ActorReference` | Opaque actorId supplied by a trusted upstream boundary; does not authenticate or authorize |
| `TransitionCommand` | instanceId, transitionId, actorReference and mandatory expectedVersion; no destination, new version or lifecycle input |
| `TransitionResult` | Previous snapshot, proposed updated instance, matching successful history record and derived expectedVersion |
| `TransitionRecord` | instanceId, transitionId, source/target state, actorReference, resultingRuntimeVersion and occurredAt |
| `WorkflowLifecycle` | NOT_STARTED, ACTIVE, COMPLETED only |
| `WorkflowErrorCode` / `WorkflowException` | Stable capability failure representation |

IDs and state keys are nonblank opaque strings. Definition/runtime versions are
nonnegative Java longs; history result versions are positive. No SQL column
limits, UUID requirement, HTTP status codes or provider representations are imposed.
Invalid structural constructor arguments throw `IllegalArgumentException` or
`NullPointerException`; operational failures use `WorkflowException`.

## Definition binding and lifecycle

Definitions validate initial-state membership, endpoint membership and unique
transition IDs. Collections are immutable. A `(definitionId, definitionVersion)`
key must identify immutable content in the future authoritative definition store.
The domain checks the key, not the authenticity of a caller-supplied definition;
upstream orchestration must resolve the authoritative exact version. Registry,
definition persistence and publishing policy are deferred.

An instance remains pinned to its exact definitionId/definitionVersion. Passing
another ID or version fails with DEFINITION_NOT_FOUND; there is no automatic
upgrade. `definitionVersion` describes the definition; `runtimeVersion` counts
successful state transitions and starts at zero.

`notStarted(...)` creates an initial snapshot with lifecycle NOT_STARTED and
identical creation/update timestamps. `activate(exactDefinition, startedAt)`
produces ACTIVE at the initial state, still version zero; activation is
initialization, not a recorded transition. The persistence port supports
creation, activation and transition as separate writes. Activation requires an
initial state with an outgoing transition. Definitions with a terminal initial
state can be represented but cannot be activated in this baseline.

A state with no outgoing transitions is terminal. Entering it marks COMPLETED.
Completed snapshots reject further transitions and activation; there is no reopen.
The model does not require an acyclic or fully reachable graph; graph policy,
timers, SLA, authorization guards and additional lifecycle statuses are deferred.

Instances have no public constructor or state/version setters. `restore(...)`
exists for adapter reconstitution, validates snapshot structure and lifecycle,
and does not update persisted state. It is not a state-change command or security
boundary. The port must compare restored proposal origins with authoritative data.

## Transition execution model

The pure domain operation is
`instance.transition(exactDefinition, command, occurredAt)`.
It checks the definition binding, instance ID, expected runtime version, active
lifecycle, transition ID and source-state match. The target comes exclusively
from the definition. Unknown transition IDs produce INVALID_TRANSITION;
source mismatch or NOT_STARTED produces TRANSITION_NOT_ALLOWED.

On success it creates a new immutable snapshot and one matching history record.
The resource, definition binding and createdAt stay fixed. runtimeVersion increases
exactly once; updatedAt equals record.occurredAt. Time must not precede the previous
updatedAt; equal timestamps are allowed. Runtime version overflow produces
OPERATION_FAILURE. Every rejected operation leaves the original snapshot intact.

`TransitionResult` is a validated proposal until persistence commits it. Its
constructor is package-private and checks snapshot/history consistency. There
is no application service, provider execution or endpoint in this Issue.
The future orchestration boundary loads the authoritative instance and definition,
derives a result, then calls `commitTransition(result)`. It must return success
to its caller only after the commit succeeds. Trust and authorization belong
upstream; Workflow does not resolve actor references through Identity.

## Persistence and optimistic concurrency

`WorkflowPersistencePort` provides:

- `loadDefinition(definitionId, definitionVersion)`: authoritative exact immutable
  definition; no latest-version fallback; missing key is DEFINITION_NOT_FOUND.
- `createInstance(initial)`: insert-only creation of the authoritative definition's
  initial NOT_STARTED snapshot, runtimeVersion zero, equal createdAt/updatedAt,
  and empty history. Returns the durably stored snapshot.
- `commitActivation(expected, activated)`: atomic full-snapshot comparison and
  activation, described below. Returns the durably stored snapshot.
- `loadInstance(instanceId)`: authoritative snapshot; missing instance is an error.
- `loadHistory(instanceId)`: immutable successful-transition history; existing
  instance without transitions returns an empty list; missing instance is an error.
- `commitTransition(result)`: atomically compare the persisted version with
  result.expectedVersion, update state/lifecycle/timestamp, increment runtimeVersion
  and append exactly one matching history record. Returns the committed result.

The adapter must compare the previous snapshot's definition binding, resource,
source state, lifecycle and timestamps against persisted data. An update must
preserve binding, resource and createdAt. Concurrency control must occur at commit,
not only during an earlier read. A version mismatch, including replay of a committed
proposal, produces VERSION_CONFLICT and changes neither state nor history.
Missing, terminal and inconsistent-origin rejections also make no changes. A provider
failure guarantees no changes only when the adapter confirms that the write did not
commit or was rolled back, as detailed below. There is no automatic retry or idempotent replay policy.

After checking the proposal's origin, adapters must load the authoritative exact
definition and rederive the transition from the persisted source, the record's
transitionId/actorReference/occurredAt and expectedVersion. Compare the resulting
snapshot and history record by value. A consumer's same-ID/version definition is
not authoritative: a missing transitionId is INVALID_TRANSITION; a differing target
state/lifecycle or history is TRANSITION_NOT_ALLOWED. Existing valid transitions
retain their version, lifecycle and history semantics; authorization remains upstream.

Each successful transition gets a unique, contiguous per-instance history version
starting at one. `loadHistory` orders ascending by resultingRuntimeVersion;
`TransitionRecord.BY_RUNTIME_VERSION` supplies that per-instance comparator.
Timestamps are not ordering keys, so ties remain deterministic. Rejected or confirmed
uncommitted attempts add no history. An unknown outcome may include a committed
transition/history pair; security audit logging is a separate future concern.
Separate instance/history reads do not promise a shared transactional snapshot.

### Write outcomes and reconciliation

The same four outcomes apply to createInstance, commitActivation and commitTransition:

| Outcome | Contract |
| --- | --- |
| Validation/business/concurrency rejection | The rejected write makes no changes; retain its existing normalized error code. |
| Provider failure with confirmed no commit or rollback | OPERATION_FAILURE; the write makes no changes. |
| Confirmed successful durable commit | Return the committed snapshot/result. |
| Commit outcome cannot be established | COMMIT_OUTCOME_UNKNOWN; the whole write may already be durable, or may not have committed. |

A lost connection/response after COMMIT can leave the caller uncertain even when
the database committed successfully. A provider exception does not prove rollback.
Adapters must use COMMIT_OUTCOME_UNKNOWN whenever they cannot establish the write's
outcome, rather than falsely promise no changes with OPERATION_FAILURE. These are
normalized Workflow errors: never expose provider causes, SQL or credentials.
Atomicity is unconditional: creation is complete or absent; activation never adds
history; a transition's instance update and exactly one matching history record
commit together or neither commits. Outcome uncertainty never permits partial writes.
Other concurrent writers may advance the data even when this write was rejected.

Never blindly automatically retry a write after an error. Keep the attempted source,
proposal and record for reconciliation, then use authoritative loadInstance and
loadHistory reads:

- Creation: inspect instanceId, exact definition binding, resource, lifecycle/version
  and timestamps. An existing identical snapshot does not prove which writer created
  it; replay still yields INSTANCE_ALREADY_EXISTS, not idempotent success.
- Activation: inspect lifecycle and the full snapshot, not version alone. It can have
  committed at version zero without history, and later transitions may have advanced it.
- Transition: find the exact proposed record at its resultingRuntimeVersion and compare
  its contents with the authoritative instance/history. Later transitions may have
  advanced the instance; they do not erase the earlier committed record.

Separate reads are individually consistent, not a shared snapshot. Re-read if they
straddle concurrent progress; a mismatched pair alone does not prove partial mutation.
An old or absent snapshot while a transaction is unresolved does not prove rollback.
If reads fail, conflict or cannot establish the outcome/writer, keep it unresolved
and use adapter-owned recovery/operator reconciliation. Do not report success merely
from an exception, a duplicate ID or an ambiguous read. An explicit retry requires
the prior transaction to be settled, authoritative concurrency checks and an explicit
application recovery decision; this port adds no operation receipts or automatic retries.

### Definition provisioning and ownership

Workflow owns these persistence semantics. Data/infrastructure implements the port
and provisions definitions through its own controlled seed/import mechanism.
Provisioning is not a runtime operation in this port; no definition CRUD API or
production registry implementation is introduced in milestone 1.

Each (definitionId, definitionVersion) identifies immutable content. An identical
re-import may be accepted by provisioning; different content under an existing key
must be rejected. A changed definition gets a new explicit version. Definitions
referenced by instances must remain resolvable; provisioning must not remove or
overwrite them. Store the complete states/transitions/initialState and reconstruct
the existing WorkflowDefinition; never trust a consumer's same-key definition.
Version zero remains valid. Instance creation must check that the exact definition
exists as part of the write. Multiple instances may reference the same resource;
this contract imposes uniqueness on instanceId, not ResourceReference.

### Insert-only creation

Validate a supplied snapshot against the authoritative definition, not just its
ID/version. It must equal WorkflowInstance.notStarted(instanceId, exactDefinition,
resourceReference, createdAt) by field value. Structurally valid ACTIVE, COMPLETED
or forged-initial-state snapshots produce TRANSITION_NOT_ALLOWED.

An existing instanceId always produces INSTANCE_ALREADY_EXISTS, including an
identical retry or a different definition/resource. Check duplicate ID before
definition lookup. Concurrent writers for one ID have exactly one winner; all
losers preserve the existing instance and history. Missing definition produces
DEFINITION_NOT_FOUND. Confirmed no commit/rollback produces OPERATION_FAILURE and
inserts neither instance nor history. COMMIT_OUTCOME_UNKNOWN may mean the initial
instance is already durable with empty history; follow the reconciliation policy.
There is no implicit overwrite, upsert or idempotency.

### Atomic activation and error precedence

Compare all expected source fields with authoritative data at the write boundary:
instanceId, definitionId, definitionVersion, resourceReference, currentState,
runtimeVersion, lifecycle, createdAt and updatedAt. Compare values, not Java object
identity. A restored but exactly equal source is valid.

The proposed target must equal expected.activate(authoritativeDefinition,
activated.updatedAt()). Only NOT_STARTED -> ACTIVE is allowed. Preserve identity,
definition binding, resource, initial state and createdAt. runtimeVersion remains
zero; updatedAt may equal the previous value but may not move backwards.
Activation adds no transition history. An initial terminal state still cannot
activate, as required by the existing domain model.

Errors are checked in this order:

1. Missing expected.instanceId: INSTANCE_NOT_FOUND.
2. Stored runtimeVersion or lifecycle differs from expected: VERSION_CONFLICT.
3. Stored/expected lifecycle is COMPLETED: INSTANCE_COMPLETED.
4. Other source-field differences, unchanged ACTIVE reactivation,
   or backwards activation time: TRANSITION_NOT_ALLOWED.
5. Missing authoritative definition: DEFINITION_NOT_FOUND.
6. A target differing from domain activation or a terminal initial state:
   TRANSITION_NOT_ALLOWED.
7. Provider write failure: OPERATION_FAILURE for confirmed no commit/rollback;
   COMMIT_OUTCOME_UNKNOWN if the outcome cannot be established.

Both version and lifecycle are mandatory compare-and-write predicates. Checking
version alone cannot prevent competing activations because both start and finish
at version zero. Exactly one concurrent activation succeeds. Replay of its original
NOT_STARTED source produces VERSION_CONFLICT, even with unchanged timestamps.
Attempting activation from the current ACTIVE snapshot produces TRANSITION_NOT_ALLOWED.
The full comparison and update must be one atomic operation; a prior read is not
a substitute. Rejection and confirmed no commit/rollback make no changes. An unknown
outcome may have committed the complete activation; it never adds transition history.

### PostgreSQL adapter handoff: time and storage

Core uses Instant with nanosecond precision and compares timestamps exactly.
The provider-free fixture deliberately preserves nanosecond values. PostgreSQL
timestamp/timestamptz has microsecond resolution and fractional precision 0..6:
[PostgreSQL date/time documentation](https://www.postgresql.org/docs/18/datatype-datetime.html).
Implicit database rounding would change snapshots and can break origin checks.

For a PostgreSQL adapter using timestamptz(6), configure the future orchestration
clock/input boundary outside Core to supply microsecond-aligned instants. Reject
unrepresentable precision/range with OPERATION_FAILURE before mutation; do not
silently truncate or round a proposal, expected snapshot or returned record.
Alternatively, Data may use a lossless seconds/nanoseconds representation. Core
does not impose PostgreSQL's precision/range or change domain timestamps.
Reads, write results and history must reconstruct the exact accepted Instant
values independently of session time zone. updatedAt and the transition record's
occurredAt must remain equal; tied timestamps remain legal and versions order history.

Data owns new storage/migrations, unique instance/definition-version keys and
per-instance history-version constraints. Use a local transaction/conditional write
or row lock to protect activation and transition at the write boundary. Definition
binding, resource and createdAt are immutable. Return successful results only after
durable commit and normalize provider errors without exposing provider causes.
Apply the write-outcome and reconciliation policy above to lost COMMIT responses;
never map an uncertain outcome to a rollback guarantee or blindly retry a write.
Instance/history reads are individually consistent, not a shared snapshot.
Milestone 1 changes no Request entities or Flyway V1/V2 and implements no adapter.

## Normalized errors

| Code | Meaning |
| --- | --- |
| DEFINITION_NOT_FOUND | Required exact definition absent or different binding supplied |
| INSTANCE_NOT_FOUND | Instance absent or command targets a different instance |
| INSTANCE_ALREADY_EXISTS | Insert-only creation found an existing instanceId, including identical replay |
| INVALID_TRANSITION | transitionId absent from the definition |
| TRANSITION_NOT_ALLOWED | Wrong source, not active, invalid activation or inconsistent proposal origin |
| INSTANCE_COMPLETED | Terminal instance cannot transition or reactivate |
| VERSION_CONFLICT | Expected runtime version differs; activation also compares lifecycle, including stale/replayed activation at version zero |
| OPERATION_FAILURE | Operation/read failure or runtime-version overflow; provider write failure only with confirmed no commit/rollback |
| COMMIT_OUTCOME_UNKNOWN | Adapter cannot establish whether an atomic write committed; it may already be durable; reconcile, never blindly retry |

Adapters must translate provider exceptions into these codes. `WorkflowException`
has a stable code-name message, no provider cause, suppressed exceptions or stack
trace. HTTP mapping and internal provider diagnostics belong outside Core.
For domain transitions, definition and instance matching precede version checks; version checks precede
lifecycle/transition checks. Thus a stale terminal command reports VERSION_CONFLICT.

## Tests and evidence

All new tests are plain JUnit/AssertJ with no Spring context, database, Identity
provider or Workflow provider. Fixtures exercise publication and delivery graphs,
not Request states. Tests cover definition validation/immutability, transition
mapping, source mismatch, binding, versions, lifecycle, timestamps, overflow,
command shape and history/result consistency.

`InMemoryWorkflowPersistence` exists only in test source. Its synchronized commit
uses a single replacement of an immutable instance/history pair. Tests cover
successful commits, stale/replayed proposals, two concurrent writers with one
winner, immutable ordered history with tied timestamps, restored-origin rejection,
terminal protection and injected operation failure with no partial change. The fake
also commits before simulating a lost response for all three writes, throws
COMMIT_OUTCOME_UNKNOWN and preserves the committed data. Tests verify creation
replay rejection, version-zero activation, atomic transition/history and rejection
of targets, lifecycles or transition IDs derived from forged same-key definitions.

Commands run from `backend/`, with JDK 21:

```powershell
.\gradlew.bat test --tests 'com.tricore.dxos.core.workflow.*'
.\gradlew.bat test --tests 'com.tricore.dxos.request.*'
.\gradlew.bat test
.\gradlew.bat build
```

Original Workflow Core verification on 2026-10-07 with JDK 21.0.11 and Gradle Wrapper 8.14.3 (before milestone 1):

| Check | Result |
| --- | --- |
| Focused Workflow Core tests | PASS: 29 tests across 4 suites |
| Request regression tests | PASS: 109 tests across 7 suites |
| Full backend tests | PASS: 139 tests across 12 suites; no failures/errors/skipped |
| Backend build | PASS: executable JAR and plain JAR generated |
| Core import review | Java standard-library imports only; no provider or upward dependencies |
| git diff --check | PASS |
| Scope comparison against baseline ce56984 | Only core.workflow, its tests and this document changed |

The first focused run was blocked by sandbox networking while the Wrapper tried
to download Gradle. Rerunning with the necessary access passed without changing
Gradle, configuration or dependency declarations.

**Limitation:** the fake models the required persistence semantics; it does not
prove PostgreSQL transactions, isolation, rollback, locking, process-crash recovery
or durable storage. Existing Request transaction tests also use mocks. A real
adapter and PostgreSQL atomicity/concurrency POC remain required.

### Milestone 1 verification (2026-10-10, reviewed head a75073c)

Based on clean main/origin/main `775edae`. Verification used JDK 21.0.11 and
the unchanged Gradle Wrapper 8.14.3 from `backend/`:

| Command | Actual result |
| --- | --- |
| `./gradlew.bat test --tests "com.tricore.dxos.core.workflow.*" --rerun-tasks` | PASS: 43 tests, 4 suites, zero failures/errors/skipped |
| `./gradlew.bat test --rerun-tasks` | PASS: 208 tests, 19 suites, zero failures/errors/skipped |
| `./gradlew.bat build` | PASS: bootJar/jar built; prior full test results up-to-date |
| `git diff --check` | PASS |

The existing transition fixtures now initialize through provision/create/activate
instead of bypassing the public persistence operations. Additional contract tests
cover exact version loading (including zero), immutable provisioning, insert-only
creation/replay, authoritative initial state, creation failure and concurrent
creation, restored-equal activation sources, source/target field tampering,
nanosecond round-trip, terminal initial state, activation failure/replay and two
concurrent activations with unchanged runtime version zero. Tests remain plain
JUnit/AssertJ; no PostgreSQL, Spring context or provider dependency is introduced.
Database isolation, durability, timestamp encoding and actual rollback remain
Data-owned integration verification, not evidence supplied by this fake.

## Deferred

Real persistence adapter; definition provisioning and initialization storage implementation;
PostgreSQL atomicity POC; Request integration/refactor; Gateway/trusted context;
Identity integration; provider selection; BPMN/Flowable/n8n; generic Workflow API,
UI, Docker, CI and history pagination changes. Runtime and REST belong to later
milestones. No provider or deployment topology decision is made.
