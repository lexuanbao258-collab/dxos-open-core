# Data and persistence boundaries

The backend remains a Spring Boot modular monolith. Logical capabilities do
not imply microservices, and this contract does not change deployment topology.

## Ownership and dependency direction

```text
External/Application -> Gateway -> Workflow -> WorkflowPersistencePort
                                                   |
                                                   v
                                       Data / persistence adapter (future)
```

The arrows describe the runtime delegation path. Source dependencies point
from an infrastructure adapter toward the capability-owned interface it
implements, not from Core to provider implementation.

Data owns `com.tricore.dxos.core.data` and the Object Storage contract documented
in [data-core.md](../contracts/data-core.md). It defines keys, metadata, stream
ownership, write policy and normalized storage errors independently of providers.
It does not import Request, application consumers, Workflow business models or
infrastructure. Shared types that couple capabilities are not introduced.

Workflow owns `WorkflowPersistencePort`, workflow states, transitions and their
business invariants. A future persistence adapter may implement that existing
Workflow-owned contract and must preserve its semantics and error mapping.
Data does not redefine it. Its instance/history atomicity requirement is local
to that contract and does not extend to Object Storage operations.

Request and RequestStatus belong to the existing application prototype; their
business semantics are not Data contracts. Workflow has no direct runtime
dependency on Identity. Gateway handles the gateway boundary, not persistence.

## Future adapter locations and errors

The milestone 1 Workflow-owned port also requires authoritative exact-version
definition loading, insert-only instance creation and atomic full-snapshot
activation. Data provisions immutable definition versions outside the runtime
port. Activation preserves runtime version zero, so lifecycle and all source
fields must be compared at the write boundary, not just version. Creation never
upserts. Transition/history contracts are unchanged; pagination is deferred.
See [Workflow Core contract](../contracts/workflow-core.md#definition-provisioning-and-ownership)
for provisioning, error precedence, timestamp precision and PostgreSQL handoff.

- `com.tricore.dxos.infrastructure.storage`: future Object Storage adapters
  implement `ObjectStoragePort`. Provider configuration, namespace mapping,
  clients, SDK objects, stream wrappers and conditional writes stay here.
- `com.tricore.dxos.infrastructure.persistence`: future database adapters
  implement contracts owned by the respective capabilities, including
  `WorkflowPersistencePort` when supplied by the Workflow owner. JPA/JDBC,
  transaction mechanisms and provider details stay outside Core.

No adapter is implemented in this task. Storage adapters must map invalid
key/input to `INVALID_INPUT`, missing objects to `NOT_FOUND`, failed conditional
operations/existing create-only objects to `CONFLICT`, temporary provider/network
failures to `UNAVAILABLE`, and permission/access failures to `ACCESS_FAILURE`.
Mapping covers lazy content reads and close as well as initial port calls.
No credentials, connection strings, SQL, tokens, provider messages, exceptions
or stack traces may escape through the contract. Persistence adapters use the
error contract of their owning capability; storage errors do not replace
Workflow errors.

## Database and Object Storage atomicity

A PostgreSQL transaction and an Object Storage write/delete are **not a
distributed atomic transaction**. Database rollback does not undo an object
write, and committing a database reference does not ensure an object operation
succeeds. Objects can become orphaned; references can point to missing content;
a timeout can leave an unknown object-write outcome.

Future application orchestration and adapters must handle failures, recovery,
retry and compensation for each use case, including reconciliation or orphan
cleanup where appropriate. They must choose ordering and idempotency policy
explicitly. These are future design decisions, not guarantees of this port.
This task adds no distributed transaction framework or cross-store transaction.

## Migration constraints

The current Flyway migrations are:

| Version | Existing purpose | Ownership |
| --- | --- | --- |
| `V1__create_requests.sql` | Request prototype table | Application prototype |
| `V2__add_request_workflow.sql` | Prototype assignment/resolution/version fields and Request status history | Application prototype |

1. V1 and V2 are immutable. Never edit an applied migration or alter its checksum.
2. Every future schema change uses a new migration version. This task creates
   and modifies no SQL or migration file.
3. Migrations are forward-only. Corrections require a subsequent forward
   migration; do not rewrite history or rely on a down migration.
4. New migrations must consider backward and data compatibility: existing
   rows, older application behavior during rollout, constraints, defaults,
   backfills and preservation of persisted data. Stage incompatible changes
   when the deployment/use case requires it.
5. Prototype/application schema and future Core-owned persistence schema are
   distinct ownership concerns. Existing Request tables and status checks
   must not automatically become Core contracts or generic Workflow schema.
6. Future persistence schema and adapters must implement the capability-owned
   contract. Workflow's owner controls its persistence semantics; Data cannot
   derive replacement Workflow semantics from prototype tables.

Migration compatibility and adapter transaction guarantees require future
database validation. Provider-independent contract tests here do not prove
Flyway execution, PostgreSQL transactions or production storage behavior.
