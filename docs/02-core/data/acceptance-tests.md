---
title: DX-OS Open-Core — Data Core Acceptance Tests
document_id: G4-F23
version: 1.0-review-draft
status: DRAFT — NOT REVIEWED
source_commit: 0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed
canonical_contract: docs/contracts/data-core.md
reviewers: Data / Infrastructure / Application / Security / Independent — pending
---

# Data Core — Acceptance & Verification Specification

> **Evidence policy.** **OBSERVED** = exact-SHA Java/canonical source inspected; **SELECTED** = user-approved Q01–Q18 authoring choices; **PROPOSED** = test/design/adapter/recovery requirement not executed; **VERIFIED** = current executed results with reproducible run artifacts and independent confirmation. This is a **design-time acceptance specification**: all catalog cases are `NOT RUN` or `DESIGN OPEN`. **No test executions, current PASS outcomes, provider conformance verdicts, or independent Gate approvals are asserted.**

## 1. Contract baseline and immutable source pin

- Commit: `0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`.
- [Canonical Data Core contract](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/contracts/data-core.md), blob `99b6ad1d67b2e8d730f13b7a082659e4c4e75b3d`.
- [Data/persistence boundaries](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/docs/architecture/data-persistence-boundaries.md), blob `10b34157faf47f6c3a2831d1965264f39e941b72`.
- [`ObjectStoragePort.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/data/ObjectStoragePort.java), blob `0392d78bf86f828a6f21fdc6947e2c57ab93ec47`.
- [`ObjectContent.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/data/ObjectContent.java), blob `8ff5db66049d8e70cd7ef8bfa252022a7042bd27`.
- [`StorageErrorCode.java`](https://github.com/lexuanbao258-collab/dxos-open-core/blob/0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed/backend/src/main/java/com/tricore/dxos/core/data/StorageErrorCode.java), blob `224ee291b0787682bfb5333afa764dfef668fe95`.

**OBSERVED:** public storage SPI has four operations (`store`, `read`, `metadata`, `delete`), two explicit write modes and five normalized errors: `INVALID_INPUT`, `NOT_FOUND`, `CONFLICT`, `UNAVAILABLE`, `ACCESS_FAILURE`. The source contract requires caller-owned store input and returned read resource; read metadata/bytes must represent one object version; `CREATE_ONLY` must enforce absent-key writes under concurrency. Object Store and SQL do **not** share an implicit distributed transaction. No universal error precedence, version CAS, guaranteed rollback, exactly-once, or generic Data-side `COMMIT_OUTCOME_UNKNOWN` is added here.

**Historical/source-documented coverage, not current execution:** canonical `data-core.md` describes `ObjectStoragePortTest` using test-only `InMemoryObjectStorage`, covering operation semantics, validation, concurrency, resource lifecycle and error sanitization. This is not proof of real-provider behavior and was **not rerun for this document**. Test selector paths should be verified against pinned source before executing rather than invented in this specification.

## 2. Acceptance status vocabulary and evidence policy

| State | Required meaning |
| --- | --- |
| `NOT RUN` | No execution artifact is available for the claimed case/environment. Default for this document. |
| `BLOCKED` | Could not execute because provider, credential, fixture, dependency or review access was missing; record blocker and owner. |
| `PASS` | Real test was executed at pinned SHA and environment with successful assertions, exit code and artifact links; reviewer evaluates whether sufficient. |
| `FAIL` | Executed assertions failed; capture defect and observed side effects, never suppress negative evidence. |
| `INCONCLUSIVE` | An attempted run could not establish required outcome, or race/fault conditions were not proven. |
| `HISTORICAL` | A previous-source statement/run; not current PASS and not substitutes for real-provider proof. |

Never infer `PASS` from source existence, an in-memory fake, a green badge without logs, an accepted document, or a merged PR. A final Gate verdict requires independent assessment beyond local test success.

## 3. Acceptance Gates (proposed, not approved)

| Gate | Verification domain | Evidence | Current status |
| --- | --- | --- | --- |
| G1 Source/Contract | Exact SHA, current contract and public API | Blob inventory and import/interface review | SOURCE INSPECTED (scoped); GATE NOT REVIEWED |
| G2 Unit/Negative | Values, invalid inputs, four operations, stream semantics | Reproducible JUnit selectors/XML, fixture results | NOT RUN |
| G3 Real Provider | Adapter registration, namespace, read/write/delete semantics | Provider-versioned conformance test report | NOT VERIFIED |
| G4 Concurrency/Failure | Race-safe conditional create, stream/close faults | Bounded concurrent execution and fault injection | NOT RUN |
| G5 Recovery/Consistency | Object-first/DB-first windows, unknown outcome, guarded cleanup | DB + object snapshots, failure timelines, reconciliation evidence | DESIGN OPEN / NOT RUN |
| G6 Security/Reuse | Protected use cases, IDOR, spoofing, Helpdesk/Facility | Security E2E, same Core SHA reuse audit | NOT RUN |
| G7 Independent | P0 blockers, reviewer signatures and exact evidence | Signed verdict per gate | NOT APPROVED |

## 4. Deterministic fixture and execution requirements

**PROPOSED:** create isolated disposable object namespace and database fixtures, never using production data. Capture fixture identifiers, bytes/hash, full metadata map, declared content length, stream ownership markers and before/after object and SQL states. Use coordinated barriers/latches and bounded timeouts rather than timing-only sleeps for races. Ensure the test proves it reached the intended concurrent/failure window; otherwise mark `INCONCLUSIVE`.

For storage calls, tests must not assume cross-call common snapshots or provider rollback. Each candidate provider requires a separately versioned conformance run; source-compatible interfaces alone cannot establish equivalence. `OVERWRITE` has no expected version and competing writes have no guaranteed winner order. Real-provider read-after-write, publication and delete visibility must be established or identified as an explicit limitation. SQL object references remain Application-owned.

## 5. Full acceptance test catalogue — planned, not executed

### AT-DM-01 — Opaque key validation, exact casing, distinct keys

- **Design trace:** G4-F19/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Create `A`, `a`, blank and whitespace-only key fixtures.
- **Steps:** Construct values and round-trip real provider keys.
- **Expected assertions:** Invalid keys INVALID_INPUT; A/a remain distinct; no normalized collision.
- **Required negative/side-effect assertions:** No unintended object change.
- **Evidence artifact required:** Unit + provider readback; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-DM-02 — Metadata invariants and immutability

- **Design trace:** G4-F19/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Use zero/negative bytes, null/blank content type, map with invalid keys/values.
- **Steps:** Construct metadata; mutate input map after construction.
- **Expected assertions:** Zero bytes accepted; invalid inputs INVALID_INPUT; metadata unchanged.
- **Required negative/side-effect assertions:** No mutable alias or leaked provider metadata.
- **Evidence artifact required:** Unit assertions; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-DM-03 — Exact byte-length enforcement

- **Design trace:** G4-F19/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Controlled streams 0, N and N±1 declared lengths.
- **Steps:** Store streams from current position to EOF.
- **Expected assertions:** Correct N accepted; mismatches and caller input read errors INVALID_INPUT.
- **Required negative/side-effect assertions:** Caller input not closed; outcome of failed provider write not assumed rollback.
- **Evidence artifact required:** Instrumented unit+provider; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-OPS-01 — Four public methods round-trip

- **Design trace:** G4-F17/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Fresh isolated key; stable bytes and content type.
- **Steps:** CREATE_ONLY store; metadata; read; delete.
- **Expected assertions:** Stored metadata/bytes equal expected; delete removes content+metadata.
- **Required negative/side-effect assertions:** No exposed raw provider exception.
- **Evidence artifact required:** Provider evidence; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-OPS-02 — Missing and repeated deletion

- **Design trace:** G4-F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Absent key.
- **Steps:** read; metadata; delete; repeat delete.
- **Expected assertions:** Each missing-key case returns NOT_FOUND.
- **Required negative/side-effect assertions:** No ghost reference inserted.
- **Evidence artifact required:** Unit+provider; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-CON-01 — Concurrent CREATE_ONLY one winner

- **Design trace:** G4-F18/F20/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Real provider fresh key; two bounded coordinated writers with distinct bytes/metadata.
- **Steps:** Release both at barrier; wait with bounded deadlines; read winner.
- **Expected assertions:** Exactly one success, one CONFLICT; content and metadata match winner.
- **Required negative/side-effect assertions:** Loser makes no changes; no mixed version.
- **Evidence artifact required:** Provider race and before/after hash; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data/Infra (assignment/approval pending).
### AT-CON-02 — Existing CREATE_ONLY preserves data

- **Design trace:** G4-F20/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Seed existing key.
- **Steps:** Attempt CREATE_ONLY with different bytes/metadata.
- **Expected assertions:** CONFLICT.
- **Required negative/side-effect assertions:** Original bytes/hash and full metadata unchanged.
- **Evidence artifact required:** Provider readback; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data/Infra (assignment/approval pending).
### AT-CON-03 — OVERWRITE creates/replaces complete metadata

- **Design trace:** G4-F20/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Fresh key then existing key with disjoint metadata maps.
- **Steps:** OVERWRITE twice and read.
- **Expected assertions:** Content and full metadata from last confirmed successful write; removed old keys.
- **Required negative/side-effect assertions:** No claimed CAS or concurrent ordering guarantee.
- **Evidence artifact required:** Provider readback; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data/Infra (assignment/approval pending).
### AT-READ-01 — Read content/metadata same version

- **Design trace:** G4-F19/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Coordinate read with independent overwrite under bounded scheduling.
- **Steps:** Acquire ObjectContent, inspect metadata, consume bytes.
- **Expected assertions:** Metadata and bytes of returned read resource correspond to one object version.
- **Required negative/side-effect assertions:** No mixed metadata/content; separate reads not assumed shared snapshot.
- **Evidence artifact required:** Real provider coordinated read; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data/Infra (assignment/approval pending).
### AT-STREAM-01 — Store input caller-owned

- **Design trace:** G4-F19/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Instrument input with close counter and nonzero current position.
- **Steps:** store success and injected failure.
- **Expected assertions:** Consumes remaining bytes synchronously; no adapter close/retain.
- **Required negative/side-effect assertions:** Close counter remains zero until caller closes.
- **Evidence artifact required:** Instrumented streams; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-STREAM-02 — Read resource close behavior

- **Design trace:** G4-F19/F20; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Provider read stream counters.
- **Steps:** Partial read, close resource twice, close stream directly, use after close.
- **Expected assertions:** Idempotent close, release resources, after-close INVALID_INPUT, metadata still accessible.
- **Required negative/side-effect assertions:** No leaked resource / unsafe reuse.
- **Evidence artifact required:** Instrumented provider wrapper; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data (assignment/approval pending).
### AT-ERR-01 — Five codes and no leakage

- **Design trace:** G4-F19/F20/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Deterministic provider exceptions incl. unavailable/access/conflict.
- **Steps:** Fault-inject initial call, lazy read, resource close.
- **Expected assertions:** Errors mapped to INVALID_INPUT/NOT_FOUND/CONFLICT/UNAVAILABLE/ACCESS_FAILURE as appropriate.
- **Required negative/side-effect assertions:** No provider cause/message/credentials/stack trace.
- **Evidence artifact required:** Fault injection+sanitized logs; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Security/Data (assignment/approval pending).
### AT-PROVIDER-01 — Adapter conformance across providers

- **Design trace:** G4-F18/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Document real provider/version/config and namespace.
- **Steps:** Run identical cases on each candidate provider.
- **Expected assertions:** Same contract behavior or explicit BLOCKED incompatibility.
- **Required negative/side-effect assertions:** No provider-specific API changes in Core.
- **Evidence artifact required:** Per-provider report; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Data/Independent (assignment/approval pending).
### AT-REC-01 — Object-first SQL failure window

- **Design trace:** G4-F18/F22; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Isolated DB reference and object namespace; synthetic SQL failure.
- **Steps:** Store object then abort SQL write.
- **Expected assertions:** Orphan identified without automatic unsafe deletion.
- **Required negative/side-effect assertions:** Preserve live/uncertain objects until safe reconciliation.
- **Evidence artifact required:** DB+object snapshots; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Application/Infra (assignment/approval pending).
### AT-REC-02 — DB-first object failure window

- **Design trace:** G4-F18/F22; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Reference staged/not publicly visible; synthetic storage failure.
- **Steps:** Prepare DB reference, inject storage failure before publication.
- **Expected assertions:** Dangling reference not exposed as valid attachment; record unresolved state.
- **Required negative/side-effect assertions:** No false success to consumer.
- **Evidence artifact required:** DB+object snapshots; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Application/Infra (assignment/approval pending).
### AT-REC-03 — Unknown object outcome and safe retry

- **Design trace:** G4-F21/F22; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Fault at/after provider acknowledgment; instrument stream and concurrent writer.
- **Steps:** Lose response; reconcile with provider-specific consistent reads.
- **Expected assertions:** No inferred rollback or success from timeout alone; keep unresolved when ambiguous.
- **Required negative/side-effect assertions:** No blind retry; fresh/reset input required for any approved retry.
- **Evidence artifact required:** Fault injection, operation timeline; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Infra (assignment/approval pending).
### AT-REC-04 — Concurrent cleanup protection

- **Design trace:** G4-F22; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Create reference/cleanup race with barriers and bounded waits.
- **Steps:** Race guarded cleanup against new authorized reference.
- **Expected assertions:** Never delete currently referenced object; unresolved cases escalated.
- **Required negative/side-effect assertions:** Cleanup action audited; no stale destructive decision.
- **Evidence artifact required:** DB+provider concurrent proof; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Application/Infra (assignment/approval pending).
### AT-SEC-01 — Authentication and resource ACL

- **Design trace:** G4-F17/F18/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Helpdesk/Facility actors, mismatched ownership, trusted principal.
- **Steps:** Attempt unauthorized read/store/delete via protected use case.
- **Expected assertions:** Deny before storage side-effect.
- **Required negative/side-effect assertions:** No object mutation or unauthorized bytes.
- **Evidence artifact required:** Security E2E; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Security (assignment/approval pending).
### AT-SEC-02 — IDOR, spoofed actor and secret leakage

- **Design trace:** G4-F18/F21; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Two distinct resources; forged identity headers; provider error.
- **Steps:** Attempt cross-resource object access and induced failure.
- **Expected assertions:** Forged actor rejected; object key alone insufficient; sanitized errors.
- **Required negative/side-effect assertions:** No provider secrets, no cross-tenant disclosure.
- **Evidence artifact required:** Security E2E + log review; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Security (assignment/approval pending).
### AT-REUSE-01 — Helpdesk and Facility distinct policies

- **Design trace:** G4-F17-F22; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Both use cases on same pinned Core SHA with different ACL/retention.
- **Steps:** Execute positive/negative storage workflows.
- **Expected assertions:** Same ObjectStoragePort unchanged, separate business policy holds.
- **Required negative/side-effect assertions:** No hardcoded Helpdesk branches in Core.
- **Evidence artifact required:** Two-App E2E + diff audit; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Application/Independent (assignment/approval pending).
### AT-OPSENV-01 — Reproducible environment

- **Design trace:** G4-F17-F23; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** Clean test namespace; pinned sources/JDK/Gradle/provider config.
- **Steps:** Build and execute named selectors; gather JUnit XML/exit codes.
- **Expected assertions:** Independent reviewer can reproduce exact result.
- **Required negative/side-effect assertions:** Cleanup leaves no shared-data damage.
- **Evidence artifact required:** Command transcript + artifacts; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Operations (assignment/approval pending).
### AT-GATE-01 — Independent final disposition

- **Design trace:** G4-F17-F23; **current execution:** `NOT RUN`.
- **Setup / prerequisites:** All cases and evidence indexed, owners named.
- **Steps:** Review blockers/status, sign each Gate.
- **Expected assertions:** PASS only if actual evidence matches; otherwise NOT RUN/BLOCKED/INCONCLUSIVE/FAIL.
- **Required negative/side-effect assertions:** No self-approval or fake PASS.
- **Evidence artifact required:** Signed review matrix; pin SHA, logs, actual result, and exact environment.
- **Reviewer:** Independent (assignment/approval pending).

## 6. Failure windows and expected recovery decisions

| Window | Object-first possible state | DB-first possible state | Acceptance requirement | Status |
| --- | --- | --- | --- | --- |
| Before first side effect | No new object/reference | No new object/reference | No false published success | NOT RUN |
| First action succeeds, second not attempted | Object exists, SQL reference absent | SQL reference staged/persisted, object absent | Identify orphan/dangling condition; do not expose invalid attachment | NOT RUN |
| Second action fails definitively | Existing object but SQL rollback | DB reference may exist despite storage failure | Preserve evidence, require guarded compensation/reconciliation | NOT RUN |
| Response lost near write completion | Object may exist or not | SQL reference and object may disagree | Keep outcome unresolved; no blind retry/cleanup | NOT RUN |
| Concurrent reference creation / cleanup | Object may be about to gain live reference | Reference may be published while object cleanup begins | Prevent stale destructive cleanup; require guard + audit | NOT RUN |

**SELECTED:** comparing object-first and DB-first does not approve either as the production algorithm. Application ADR must determine ordering, reference publication, explicit retry/idempotency policy, retention and authorized cleanup. Compensation is a separate possibly failing operation, not distributed rollback. A missing SQL reference during an unresolved transaction is not sufficient proof of an orphan safe to delete.

## 7. Security, two-Application reuse and cross-file traceability

| Document | Requirements traced to planned cases | Required independent proof |
| --- | --- | --- |
| G4-F17 `README.md` | AT-OPS-01/02, AT-PROVIDER-01, AT-REUSE-01 | Core scope and two distinct Applications |
| G4-F18 `architecture.md` | AT-CON-01, AT-REC-01/02, AT-SEC-01/02 | Adapter boundaries, SQL/object windows, trusted use cases |
| G4-F19 `domain-model.md` | AT-DM-01/02/03, AT-STREAM-01/02 | Exact input constraints, immutable values, resource lifecycle |
| G4-F20 `contracts.md` | AT-OPS-01/02, AT-CON-01/02/03, AT-ERR-01 | Four SPI methods, write modes, five normalized errors |
| G4-F21 `storage-adapters.md` | AT-PROVIDER-01, AT-CON-01, AT-READ-01, AT-ERR-01 | Actual provider conformance, stream and error wrappers |
| G4-F22 `consistency-recovery.md` | AT-REC-01/02/03/04, AT-SEC-02 | Fault windows, unknown outcomes, protected cleanup |

The G4-F17…F22 references are documentation-workspace drafts; this table identifies intended traceability, **not** evidence that those drafts were merged, reviewed or accepted. Same SHA and different business policies are required for Helpdesk and Facility reuse. `ObjectKey` cannot substitute for authentication/authorization. Negative security tests must demonstrate denial **and no unauthorized object mutation or data disclosure**.

## 8. Reproducibility and evidence manifest

**Record for each actual run** (do not fabricate values): repository SHA/branch/dirty-tree state; JDK version; Gradle Wrapper version; operating system; test command and exact selector; provider product/version/endpoint category with secrets redacted; isolated namespace; PostgreSQL version/config if applicable; selected fault injector and deterministic checkpoints; start/end timestamps; process exit code; JUnit XML/report paths; sanitized logs; object content hashes and metadata before/after; SQL snapshots; reviewer and verdict.

**Potential command shape (requires verification of test selectors and environment before use):**

```powershell
cd backend
.\gradlew.bat test --rerun-tasks
```

This is a **suggested future execution**, not a command executed in the documentation authoring session. Provider conformance suites may need separate selectors/configuration and are **not claimed to exist**. Store exact artifact paths only after the corresponding run produces them. Evidence must be retained in the team's controlled evidence area and linked from the review checklist.

## 9. P0 blockers and final disposition rules

- **P0:** Any provider adapter that violates race-safe `CREATE_ONLY` or mutates winner data on conflict.
- **P0:** Caller-owned store stream closed/retained by adapter, leaked provider read resources, incorrect lazy-read/close error normalization.
- **P0:** Incorrect byte count, case-folded/colliding keys, content and metadata mixed across object versions.
- **P0:** Provider causes, credentials, SQL or secrets leaking across Core boundary.
- **P0:** Unauthorized object access/delete, IDOR, forged actor acceptance or unguarded cleanup.
- **P0:** Blind retry after uncertain write, unsafe cleanup while references are racing, or false SQL/object atomicity claims.
- **P0:** Claiming fake-based tests prove real-provider durability or marking NOT RUN/INCONCLUSIVE cases PASS.

A failed case is `FAIL` regardless of unrelated successful tests. A missing real-provider environment is `BLOCKED`, not silently skipped or passed. A race without demonstrated overlap is `INCONCLUSIVE`. A workflow or recovery design awaiting ADR can be `DESIGN OPEN` and blocks corresponding Gate acceptance. Independent reviewer must explicitly resolve each P0 before recording any Gate approval.

## 10. Open decisions and independent review readiness

| Open ID | Required decision / evidence owner | Status |
| --- | --- | --- |
| AT-OD-01 | Select and inspect actual storage adapter and candidate provider(s); Data/Infra | OPEN |
| AT-OD-02 | Agree conditional-create mechanism and namespace collision model; Data/Infra | OPEN |
| AT-OD-03 | Define provider-specific consistency/publication and timeout fault-injection suite; Infra | OPEN |
| AT-OD-04 | Approve object-first versus DB-first ordering and safe reference visibility; Application | OPEN |
| AT-OD-05 | Define guarded reconciliation, cleanup, compensation, retry and stream rules; Application/Infra | OPEN |
| AT-OD-06 | Confirm executable test names, CI job, disposable fixtures and JUnit XML paths; QA | OPEN |
| AT-OD-07 | Prove Helpdesk + Facility separate ACLs on same Core SHA; Application/Security | OPEN |
| AT-OD-08 | Independent evidence review and formal Gate decision; Independent reviewer | OPEN |

**Current result:** `G4-F23 DRAFT / NOT REVIEWED`. This file is a complete **acceptance specification draft**, not an acceptance test run, production storage verification, or G4 release approval.
