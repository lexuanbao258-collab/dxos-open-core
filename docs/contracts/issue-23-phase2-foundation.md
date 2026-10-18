# Issue #23 Phase 2 — safe instance identity foundation

Status: **partial foundation, NOT production authorization**.

This change adds `WorkflowInstanceIdGenerator` under `application.workflow`. It
uses `SecureRandom` to generate opaque, unpadded base64url IDs with 192 random
bits. Its `wf_` prefix and URL-safe alphabet fit the existing instance ID
validation. No Workflow Core signature, persistence behavior, HTTP mapping,
authentication configuration or production route is changed.

## Security boundaries

- The current HTTP create DTO **still accepts client-supplied instanceId**.
  This class **must not be considered an oracle fix until create is rewired**
  to generate IDs on the trusted server side, reject supplied ID fields, and
  avoid disclosing collisions (including concurrent insert conflicts).
- The generator is not a resource/tenant authorization provider. A randomly
  generated ID does not imply caller ownership or visibility.
- On an internal ID collision with a **confirmed no-commit** result, a bounded
  explicit retry may be considered after reviewing application semantics.
  Never blindly retry `COMMIT_OUTCOME_UNKNOWN` or return the conflicting ID.
- Lost-response create requires an explicit idempotency design if the application
  wants replay recovery. A global instance ID is not a caller-owned lookup key.

## Phase 2 blockers (not implemented here)

1. Ánh + Bảo: PostgreSQL authoritative resource/tenant registry, membership,
   ACL, mutation locking protocol and final check on the **same JDBC connection
   and transaction** as create/activate/transition. An outer `@Transactional`
   wrapper alone is insufficient because the existing persistence adapter owns
   JDBC connections and commits.
2. Ánh + Bảo: authorize read and history using a coherent resource binding and
   read/materialization boundary; make hidden and missing indistinguishable.
3. Yến: merge corrected Identity PR #33 and supply verified
   `CurrentPrincipal.requireVerified()` to the application. No production
   fake caller, JWT parsing, or request-attribute proof is acceptable.
4. Bảo: construct `AuthorizedWorkflowService`, replace direct HTTP Runtime
   dependency, reject request-supplied `instanceId`, map sanitized 401/403/404/503,
   and retain `COMMIT_OUTCOME_UNKNOWN` without automatic retries.
5. All: prove negative/cross-tenant tests, revocation race, duplicate races,
   filter-chain + actual JWT, and real PostgreSQL integration before enabling
   production Workflow routes.

`WorkflowController` deliberately stays **unregistered**.
`WorkflowHttpExposureTest` must continue to pass. No test here claims to
prove production access control, durable commit or HTTP behavior.

## Focused tests

`./gradlew test --tests 'com.tricore.dxos.application.workflow.WorkflowInstanceIdGeneratorTest'`

Run with JDK 21. The unit tests validate ID shape, uniqueness in a sample and
required input; they do **not** mathematically guarantee uniqueness.
