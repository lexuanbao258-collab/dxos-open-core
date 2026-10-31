---
title: DX-OS Open-Core — Data Core Contracts
document_id: G4-F20
version: 1.0-review-draft
status: DRAFT — NOT REVIEWED
source_commit: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
canonical_contract: docs/contracts/data-core.md
owner: Data Core — to confirm
reviewers: Data / Application / Security / Independent G4 — pending
---

# Data Core — Public Object Storage Contracts

> **Evidence legend:** **OBSERVED** = directly inspected pinned Java and canonical contract; **SELECTED** = explicitly approved G4-F20 authoring decision; **PROPOSED** = implementation/test/evidence obligation, not a runtime finding; **VERIFIED** = independently reproducible result with actual logs. No tests were executed in preparing this document, and no G4 Gate has been approved.

## 1. Scope and contract authority

**Contract boundary:** the Java `ObjectStoragePort` is the AS-IS provider-independent unstructured Data contract, with opaque object keys, metadata, content streams, write modes, and normalized storage failures. A general structured Data public contract is TO-BE and must be defined before claiming provider-independent PostgreSQL replacement; existing Application repositories and Workflow's persistence port are not silently reclassified as that contract. Access to a key is not authorization. Conformance for MinIO or an alternative S3-compatible/object provider must test bytes and metadata, create-only conflict, overwrite, missing objects, streaming and close behavior, provider failure translation, privacy, and uncertain cross-store outcomes.

**SELECTED:** This document specifies the existing `com.tricore.dxos.core.data` Object Storage Java SPI, value objects, behavioral semantics, failure rules, stream ownership, adapter obligations and test traceability. It neither alters the public API nor establishes a new REST endpoint, provider integration or SQL repository.

**Source pin:** `0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`.

- [Historical canonical Data Core contract](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/data-core.md) — blob `99b6ad1d67b2e8d730f13b7a082659e4c4e75b3d`.
- [`ObjectStoragePort.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/data/ObjectStoragePort.java) — blob `0392d78bf86f828a6f21fdc6947e2c57ab93ec47`.
- [`ObjectContent.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/data/ObjectContent.java) — blob `8ff5db66049d8e70cd7ef8bfa252022a7042bd27`.
- [`StorageException.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/data/StorageException.java) — blob `c14a4ccd64efdbd079327552644a6c65346f30c6`.
- [Data/persistence boundaries](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/architecture/data-persistence-boundaries.md) — capability ownership and SQL/object transaction limits.

**Documentation location versus authority:** this tracked `contracts.md` is the current documentation location and a review draft. The `canonical_contract` metadata names the historical canonical contract linked above, interpreted at `source_commit`; that contract and the pinned Java sources remain authoritative for claims about that revision. Moving documentation does not transfer normative authority or supersede the historical contract. Any transfer requires explicit owner/team approval. Contract changes, signature additions, changes to error semantics, write mode defaults or ownership rules require a separate reviewed ADR and compatibility assessment. **No universal error precedence is established in the source**, so none is invented here.

## 2. Ownership and dependency rules

| Boundary | Owner | Contract consequence |
| --- | --- | --- |
| Object storage capability | `core.data` | Pure Java 21 SPI; no Spring, JPA, JDBC, SQL or provider SDK dependencies |
| Business resource authorization | Helpdesk / Facility Application | Authenticated trusted context and resource ACL before invoking storage; an `ObjectKey` is not a credential |
| Object storage adapter | Infrastructure | Provider client/configuration, namespace mapping, stream wrapping, conditional writes and normalized errors |
| Workflow persistence | Workflow Core owns `WorkflowPersistencePort`; infrastructure implements it | Does not inherit Object Storage API or replace Workflow's concurrency/error contract |
| Transport | Gateway / Application HTTP adapter | HTTP status, user-facing errors and exposure rules are outside Data Core |

## 3. Exact public Java SPI

```java
public interface ObjectStoragePort {
    ObjectMetadata store(StoreObjectCommand command);
    ObjectContent read(ObjectKey key);
    ObjectMetadata metadata(ObjectKey key);
    void delete(ObjectKey key);
}
```

| Public type | OBSERVED contract |
| --- | --- |
| `ObjectKey` | Nonnull/nonblank opaque, case-sensitive, preserved verbatim |
| `ObjectMetadata` | Key, nonblank supplied content type, exact nonnegative byte count, immutable custom metadata map |
| `StoreObjectCommand` | Required metadata, caller-owned input stream, required explicit `StoreMode` |
| `StoreMode` | Exactly `CREATE_ONLY`, `OVERWRITE`; no implicit default |
| `ObjectContent` | AutoCloseable read resource with one stream and metadata of the same object version |
| `StorageErrorCode` | `INVALID_INPUT`, `NOT_FOUND`, `CONFLICT`, `UNAVAILABLE`, `ACCESS_FAILURE` |
| `StorageException` | Sanitized runtime exception with `code()`, code-name message, no cause, suppression or stack trace |

**Validation details:** Zero-length content is legal. Negative/unknown declared length is not accepted. Custom metadata names are nonblank; values nonnull and may be empty; empty map accepted; defensive `Map.copyOf`. The port does not infer content type or normalize object keys. A record containing `InputStream` has final component references **but not an immutable stream**. Inputs violating Data's contract use `StorageException(INVALID_INPUT)` rather than borrowing Workflow Core's structural-exception convention.

## 4. `store(command)` — preconditions, postconditions and negative outcomes

**Preconditions:** command, metadata, input and mode all required; metadata structurally valid. The input is caller-owned and starts at its current position.

**Execution:** port consumes input synchronously from the current position through EOF. The declared byte length **must equal consumed bytes**. A mismatch or caller input read failure maps to `INVALID_INPUT`. The input must not be retained for asynchronous work or closed by the port/adapter.

**Successful result:** return `ObjectMetadata` describing the stored object, respecting the selected write mode. Success is per-operation; no multi-object transaction, retry safety or generalized durability guarantee is added here.

| Mode | Required behavior | Concurrent behavior | Forbidden inference |
| --- | --- | --- | --- |
| `CREATE_ONLY` | Create iff absent | Existing key yields `CONFLICT`, preserving previous content **and metadata**; atomic absent condition, not check-then-overwrite | An early read proves absence until commit |
| `OVERWRITE` | Create if absent or replace **all** content and **entire** custom metadata map | No specified ordering of concurrent overwrites | CAS/expectedVersion, metadata merge, implicit default mode |

**Negative cases:** invalid length / unreadable caller stream → `INVALID_INPUT`; create-only collision → `CONFLICT`; temporary provider unavailability → `UNAVAILABLE`; provider access denial → `ACCESS_FAILURE`. An operational failure can leave **uncertain provider outcome**: exception alone does not prove rollback or absence of the object. Preserve caller stream ownership even on failure.

### Example caller-owned store input

```java
try (InputStream input = openCallerOwnedInput()) {
    StoreObjectCommand command = new StoreObjectCommand(metadata, input, StoreMode.CREATE_ONLY);
    ObjectMetadata stored = storage.store(command);
    // Use stored metadata only after the call returns successfully.
}
```

`openCallerOwnedInput()` is illustrative Application code, not an existing Core method. Failure handling must not blindly reuse a partly consumed input stream.

## 5. `read(key)` — content version and resource contract

**Preconditions:** key must be a valid `ObjectKey`; missing key → `NOT_FOUND`.

**Successful result:** one `ObjectContent` resource whose `metadata()` and `content()` refer to the **same object version**. This does not imply snapshot isolation across separate `read` and `metadata` calls or introduce provider-level object-version API.

**Ownership:** caller closes returned `ObjectContent` on success, partial consumption and failure:

```java
try (ObjectContent object = storage.read(key)) {
    object.content().transferTo(destination);
}
```

The caller separately owns `destination`. `ObjectContent.close()` releases underlying read resources, is idempotent, and preserves metadata access afterwards. Closing the returned stream directly also releases underlying resources. The stream is single-use, must become unusable after close (`INVALID_INPUT`) and is not required to support mark/reset. Provider errors from **lazy reads or close** must become sanitized `StorageException`, not raw provider `IOException` or causes.

## 6. `metadata(key)` and `delete(key)` contracts

| Operation | Successful behavior | Missing key / repeated call |
| --- | --- | --- |
| `metadata(key)` | Return `ObjectMetadata` **without opening a content resource** | `NOT_FOUND` |
| `delete(key)` | Remove object content and metadata | `NOT_FOUND`, including repeated delete; no idempotent-success shortcut |

These operations do not establish a multi-operation atomic snapshot or automatic recovery from an uncertain provider outcome. The contract does not define listing, pagination or object search.

## 7. Source-aligned validation and errors

| Code | Conditions defined by the contract | Adapter obligation |
| --- | --- | --- |
| `INVALID_INPUT` | Null/blank key, invalid metadata/command/mode/length, caller-input failure, invalid read-resource use | Normalize before/during operation; no raw input/provider exception |
| `NOT_FOUND` | Absent key on read, metadata, delete | Stable missing-key semantics; repeated delete still error |
| `CONFLICT` | Existing `CREATE_ONLY` key or failed conditional write | Preserve existing object on create-only conflict |
| `UNAVAILABLE` | Temporary provider/network unavailability | Sanitize provider diagnostics |
| `ACCESS_FAILURE` | Provider access/permission failure | Do not expose credentials, provider tokens/messages |

The canonical contract defines these categories **but does not prescribe a total ordering when several conditions simultaneously hold**. Tests may verify unambiguous cases; do not claim a new precedence invariant without an ADR. HTTP status mapping is outside Core.

`StorageException` uses the enum code name as message, no cause/suppression/stack trace. The adapter must sanitize unknown failures and lazy content read/close errors. Sensitive SQL, credentials, connection strings, access tokens, exception details and provider-specific messages must not leak.

## 8. Concurrency, visibility, consistency and uncertain failures

**OBSERVED per-operation requirements:**
- `CREATE_ONLY`: absent-key condition must be race-safe; a loser produces `CONFLICT` without corrupting the winner's content or metadata.
- `OVERWRITE`: replace complete object content and metadata on success, without guaranteed concurrent overwrite order or CAS.
- `read`: metadata/content returned by the same resource describe one version.
- Missing-key delete and metadata semantics are preserved.

**Not promised:** provider-wide linearizability, cross-object or SQL/object distributed atomicity, exactly-once, automatic rollback on failure, retry/idempotency safety or shared snapshot across multiple calls. Provider-specific consistency, visibility, publication and partial-failure behavior require separate design and real-provider evidence.

**PROPOSED recovery rule:** When provider outcome is uncertain, Application/infrastructure documents reconciliation before any explicit retry. A fresh or explicitly reset store input is required if a retry is eventually authorized. An SQL rollback does not undo an object write. Do not copy Workflow's `COMMIT_OUTCOME_UNKNOWN` code into the five-code Data enum.

## 9. Authorization and two-Application reuse

An `ObjectKey` locates an object but **does not convey permission**. Verified identity and the Application's owner/assignee/approver/resource-ACL rules gate any upload, download or deletion. Storage Core neither authenticates actors nor understands Helpdesk tickets or Facility requests.

**PROPOSED reuse proof:** demonstrate two distinct Application authorization and attachment-mapping use cases on an unchanged Data Core SHA, running the same four-port-operation semantics, including negative cross-resource access tests. Do not represent illustrative use cases as already deployed.

## 10. Provider conformance matrix (acceptance requirements, not passed tests)

| ID | Contract requirement | Expected evidence on real provider | Reviewer | Status |
| --- | --- | --- | --- | --- |
| CT-01 | Four exact SPI operations | Source signatures + actual adapter call results | Data | NOT RUN HERE |
| CT-02 | Opaque key and namespace isolation | `A` vs `a`, unusual accepted keys, collision-free mapping | Data/Infra | NOT RUN HERE |
| CT-03 | Metadata and byte validation | Zero bytes, exact byte counts, map immutability, invalid-input errors | Data | NOT RUN HERE |
| CT-04 | `CREATE_ONLY` concurrent writers | Deterministically bounded race; one winner, loser `CONFLICT`; content/hash and metadata unchanged | Data/Infra | NOT RUN HERE |
| CT-05 | `OVERWRITE` semantics | Full content + metadata replacement; no claimed CAS or ordering | Data/Infra | NOT RUN HERE |
| CT-06 | `read` version consistency | Content bytes match metadata for one returned resource | Data/Infra | NOT RUN HERE |
| CT-07 | `metadata` / repeated delete | Metadata-only access, missing/repeated delete `NOT_FOUND` | Data | NOT RUN HERE |
| CT-08 | Store input ownership | Instrument stream; ensure port never closes input, incl. failures | Data | NOT RUN HERE |
| CT-09 | Read resource lifecycle | Partial read, double-close, stream-direct-close, after-close `INVALID_INPUT` | Data | NOT RUN HERE |
| CT-10 | Sanitized failure categories | Inject provider/network/access/lazy-read/close failures, inspect code/message/cause | Security/Data | NOT RUN HERE |
| CT-11 | Uncertain outcome / publication | Provider fault windows, consistency and reconciliation narrative | Infra | DESIGN OPEN |
| CT-12 | Helpdesk/Facility authorization reuse | Distinct business policies, denied cross-resource access, unchanged Core | Application/Independent | NOT RUN HERE |

The canonical source documents `ObjectStoragePortTest` with a **test-only** `InMemoryObjectStorage`. This is **source-documented historical coverage**, not a current test result, a real provider guarantee or independently confirmed acceptance.

### Evidence submission template

For each `CT-xx`, capture: exact git SHA and branch, JDK/Gradle version, provider version/config (without secrets), test command/selector, exit code, JUnit XML/report path, deterministic fixture IDs, before/after bytes/hash and metadata where appropriate, sanitized logs, and named reviewer verdict. Use `NOT RUN`, `BLOCKED`, `INCONCLUSIVE`, `FAIL`, or independently reproducible `PASS` as applicable. Never prefill PASS from documentation.

## 11. Compatibility and independent review

| Gate | Criteria | Present disposition |
| --- | --- | --- |
| G4-D Design | Decisions Q01–Q18 faithfully represented | DRAFT / NOT REVIEWED |
| G4-S Source | SPI, values and canonical contract pinned; import graph checked | KEY SOURCE INSPECTED; COMPLETE AUDIT PENDING |
| G4-C Contract | Actual unit/negative tests and XML evidence | NOT RUN HERE |
| G4-P Provider | Real-provider functionality/concurrency/stream/fault tests | NOT VERIFIED |
| G4-A Security | No provider leakage, application ACL and negative cases | NOT RUN HERE |
| G4-R Reuse | Two separate Application policies, unchanged Core | NOT RUN HERE |
| G4-I Independent | Reviewer signs exact SHA/evidence | NOT APPROVED |

**P0 review blockers:** fabricated methods/HTTP endpoints, different `CREATE_ONLY` race behavior, unsafe stream closure, key collisions, metadata/content mismatch, unsanitized provider exceptions or secrets, implicit CAS or retry guarantees, treating object possession as authorization, or a claimed Gate without exact evidence.

**Open follow-ups:** runtime adapter availability and ownership; actual provider consistency and stream wrapper; collision-safe namespace encoding; failure/reconciliation/idempotency policy; Application attachment ACL; real provider conformance report; independent decision log.

**Final status:** `G4-F20 DRAFT / NOT REVIEWED`. Selected design, checked source and proposed acceptance criteria are not evidence of tests passing or G4 Gate approval.
