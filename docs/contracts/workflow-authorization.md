# Workflow application authorization foundation — #23 Phase 1

## Implemented seam and limits

`application.authorization.WorkflowAccessPolicy` is an application-owned resource-policy
contract. `WorkflowAuthorization` combines its decision with the exact canonical operation
authority. It has no Spring registration, HTTP handler, Runtime or persistence dependency,
protected-data return value, cache, Identity implementation or default policy provider.

| Operation | Required authority | Resource-policy input |
| --- | --- | --- |
| Create | `workflow:create` | Exact authoritative definition and requested resource reference |
| Activate | `workflow:activate` | Authoritative persisted instance |
| Transition | `workflow:transition` | Authoritative persisted instance |
| Snapshot/history read | `workflow:read` | Authoritative persisted instance |

The policy must resolve the resource against authoritative data, check its visibility and
tenant membership, then check binding/action permission. A reference or token scope is not
evidence of ownership. Creation permits the exact reference/definition binding; instance
access uses the persisted binding, never client-supplied ownership. This seam covers
operation-level resource permissions; transition-specific ACLs need transition context
before integration if the application's policy distinguishes individual transitions.

`ALLOWED` still requires the operation authority. `FORBIDDEN` means a visible resource with
insufficient permission; `HIDDEN` covers both hidden and nonexistent resources;
`UNAVAILABLE` means no trustworthy policy decision. Null decisions and provider runtime
exceptions become sanitized `UNAVAILABLE` failures with no provider message/cause.
Visibility is evaluated before authority denial, so a hidden resource stays hidden even
when an operation authority is missing. No outcome is cached or automatically retried.

HTTP mapping is **not implemented** here. Later secured integration must map visible
denial to 403 and hidden/nonexistent resources to indistinguishable 404 bodies. Policy
unavailability must remain a safe non-success response. Missing/invalid authentication
and 401 handling belong to Yến, before policy evaluation or authoritative Workflow reads.

## Trusted inputs and integration blockers

`AuthenticatedPrincipal` is reused as data, not authentication proof. Its constructor and
`authenticated=true`, servlet attributes, raw `Principal.getName()`, `X-User` and `X-Role`
do not verify identity. Synthetic test principals only exercise authorization behavior.

Yến owns `application.identity.CurrentPrincipal.requireVerified()`, its Spring
Security-backed implementation, token-to-authority mapping and verified issuer/subject.
That interface is absent at this base. No substitute, fake verifier or
`AuthorizedWorkflowService` is implemented. No production ACL provider exists either.

Future orchestration must obtain Identity's verified caller first, then load the exact
definition using `WorkflowPersistencePort.loadDefinition(id, version)` for creation.
Never deserialize a definition from HTTP or select an arbitrary/latest version. For an
existing instance, use an authoritative persisted snapshot and its resource binding.
These inputs are provenance requirements; the collaborator cannot establish them from
structurally valid Java objects. Internal lookup needed for authorization must never
expose protected snapshots/history or existence details before visibility succeeds.

Only after access is granted may orchestration perform mutations or return protected
data. Reads and authorization lookup must be distinguished. Existing Workflow errors,
expected versions and `COMMIT_OUTCOME_UNKNOWN` must be preserved without blind retries.
Missing-instance exceptions must not produce an externally distinguishable 404 from
hidden resources. No-read/no-write orchestration evidence is blocked until this flow exists.

Yến supplies actor identity as
`oidc:<base64url(UTF8(issuer))>.<base64url(UTF8(subject))>` without padding or normalization.
Bảo does not parse JWTs or accept actor identity from the client. Ánh owns the production
Workflow adapter and exact actor persistence; this phase changes neither Core nor schema.
All `/api/v1/requests/**` routes will require authentication through Yến's shared Security
configuration; no anonymous compatibility exception is introduced in this phase.

## TOCTOU and production gate

A successful pre-check is not transactional authorization. Tenant ownership changes,
membership/ACL revocation and resource identity reuse can invalidate a decision before
execution. Immutable Workflow binding does not freeze the resource's owner or permissions.
Before enabling routes, choose enforcement at the application/provider write boundary
(for example coordinated checks or revisions) and document revocation semantics. Resource
identities must remain stable or include a generation that prevents reuse ambiguity.
Concurrent Workflow changes remain subject to authoritative commit validation; transition
expectedVersion does not protect an external ACL. Activation retains runtime version zero.

WorkflowController stays unregistered. Production integration requires verified Identity,
a real resource-policy provider, PostgreSQL/Clock wiring and real Security/filter-chain
tests for all five routes plus authenticated Request regression tests.

## Verification scope

Unit tests use a test-only policy fixture and synthetic principals. They cover canonical
authority allow/deny, resource decisions despite granted authorities, visibility priority,
uniform hidden/missing fixture failures, null/unavailable/throwing policy failures and
required inputs. They do not prove real tenant membership, ACL enforcement, OIDC verification,
Runtime no-read/no-write behavior, actor derivation, PostgreSQL or transactional security.
Those scenarios are **NOT TESTED / BLOCKED**, not simulated production acceptance.
