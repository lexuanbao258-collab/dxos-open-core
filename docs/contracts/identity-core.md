# Identity Core contracts

`com.tricore.dxos.core.identity` defines provider-independent identity,
authentication-context, authority, authorization-decision and normalized-error
contracts. It is a pure contract layer in the Spring Boot modular monolith; it
does not select or implement an identity provider or deployment topology.

## Responsibility and dependency direction

Identity Core owns provider-independent identity representation,
authentication-context representation, authority facts, authorization decisions
and normalized Identity failures. It does not own Workflow execution, business
data, API routing, another capability's persistence, Request/IT Support roles or
policies, or business-specific authorization rules.

The intended runtime dependency direction is:

```text
External/Application -> Gateway -> Identity
                           |
                           +-> Workflow
```

Gateway is the primary future runtime consumer of Identity.
Workflow must not directly depend on Identity.
A trusted identity must originate from a verified authentication boundary.
The current OIDC runtime verification is implemented outside Core; trusted-context
propagation through Gateway remains separate from this contract.
These are logical capability boundaries within the existing modular monolith,
not a new microservice or deployment design.

## Public contract types

| Type | Responsibility |
| --- | --- |
| `Authority` | String-backed authority held by an authenticated principal |
| `RequiredAuthority` | String-backed authority required for an authorization check |
| `AuthenticatedPrincipal` | Immutable principal representation with an opaque subject identifier, optional display name, authorities and the current authenticated invariant |
| `AuthenticationContext` | Provider-independent opaque authentication reference; possession or construction does not prove authentication |
| `AuthorizationDecision` | Immutable result of an authorization check, binding a grant/deny result to a required authority and reason |
| `AuthorizationReason` | Current reasons `AUTHORITY_GRANTED` and `MISSING_AUTHORITY` |
| `IdentityErrorCode` | Stable normalized Identity capability error codes |
| `IdentityException` | Sanitized capability failure containing an `IdentityErrorCode` |

These types do not provide an authentication verifier, identity resolver,
authorization service, provider adapter, endpoint or Gateway integration.

## Trust boundary

Constructing `new AuthenticationContext(...)` does not prove that authentication
occurred. Likewise, constructing or observing an `AuthenticatedPrincipal` whose
`authenticated` value is `true` does not prove a real login or independently show
that an authentication provider verified the caller. These objects represent
facts only after a trusted authentication boundary has legitimately established
them.

A trusted identity must originate from a verified authentication boundary.
Identity claims supplied by an external caller must not automatically become
trusted authentication state. In particular, externally supplied headers such as
`X-User`, `X-User-Id`, `X-Role` or `X-Authorities` are not trusted identity
inputs. The current Spring Security filter-chain test confirms that `X-User` and
`X-Role` cannot authenticate an anonymous request.

## AuthenticationContext semantics

`AuthenticationContext` has one field named `authenticationReference`. The value
must be non-null and nonblank and is preserved as supplied. It is a
provider-independent opaque authentication reference. It is not defined as a raw
JWT, access token, password, credential, Keycloak session object, Spring Security
object, or proof of authentication by itself.

The following runtime concepts are distinct:

- **Missing context:** no authentication context/reference was supplied.
- **Invalid context:** a context/reference exists but cannot be accepted or
  resolved as valid.
- **Expired context:** a previously valid authentication context is no longer
  valid because its authentication lifetime expired.

The current codes include `INVALID_AUTHENTICATION_CONTEXT` and
`AUTHENTICATION_EXPIRED`, but there is no dedicated missing-context code.
**REVIEW REQUIRED:** the normalized error mapping for missing context is not
established by the current repository and must be decided by the Identity owner
and Gateway consumer without inventing a new code in this contract.

Constructor validation is structural only: null, empty and blank references throw
`IllegalArgumentException`. Future runtime/provider validation would determine
whether a structurally valid opaque reference is recognized, authentic and
unexpired. Object construction performs none of those checks.

## AuthenticatedPrincipal semantics

`subjectId` is a non-null, nonblank, provider-independent opaque subject
identifier. It must not automatically be interpreted as a username, email,
Keycloak username, database user ID or other provider-specific identifier.
`displayName` is optional and may be null.

The current OIDC infrastructure adapter represents a verified JWT identity as
`oidc:<base64url(UTF8(issuer))>.<base64url(UTF8(subject))>`. It uses URL-safe
Base64 without padding and preserves the issuer and subject exactly: no trimming,
lowercasing or Unicode normalization. A missing or blank issuer or subject fails
closed. This canonical `subjectId` is not a username, email, database user ID or
raw JWT `sub`. This adapter mapping does not make the Core Identity contract
depend on OIDC or Keycloak.

`authorities` must be non-null. `Set.copyOf` defensively copies it, rejects any
null element and exposes an unmodifiable set from the consumer's perspective. An
empty set is valid. The constructor rejects null, empty or blank `subjectId`, null
authorities and `authenticated == false`. A collection containing null is rejected
by `Set.copyOf` with `NullPointerException`.

Every valid current `AuthenticatedPrincipal` therefore has `authenticated == true`.
This is an invariant of the value object, not independent evidence that a provider
verified the caller. **REVIEW REQUIRED:** because the current constructor rejects
`false`, the semantic value of retaining the boolean is potentially redundant and
should be reviewed by the Identity owner and Gateway consumer. This document does
not remove or redesign the field.

## Authority semantics

`Authority` represents an authority held by an authenticated principal.
`RequiredAuthority` represents the authority required for an authorization check.
Both are distinct string-backed record types and reject null, empty and blank
values with `IllegalArgumentException`.

The current contract uses exact authority-identifier matching:
`Authority.satisfies(requiredAuthority)` returns true if and only if the two
stored string values are exactly equal. Matching is case-sensitive and whitespace
is significant. Constructors and matching do not trim, case-fold or normalize
values. There is no wildcard, prefix or hierarchical matching. For example,
`"WORKFLOW_APPROVE"` matches only `"WORKFLOW_APPROVE"`; it does not match
`"workflow_approve"`, `" WORKFLOW_APPROVE"`, `"WORKFLOW_APPROVE "`,
`"WORKFLOW_*"` or `"WORKFLOW_APPROVE_EXTRA"`.

This rule defines only whether one held authority identifier satisfies one
required authority identifier. Business/resource authorization policy, ownership
checks, workflow-state checks, authority hierarchy, wildcard policy, authority
freshness/revocation and other future authorization concerns remain out of scope.
Exact identifier matching is not the entire authorization policy.

## Authentication and authorization

Authentication determines whether an identity has been validly established.
Authorization evaluates whether a legitimately established authenticated
principal has the required authority. `AuthorizationDecision` is only the result
of an authorization check; a granted decision is not proof that authentication
occurred. Authorization must operate on an `AuthenticatedPrincipal` originating
from the trusted boundary described above.

`AuthorizationDecision` requires non-null `requiredAuthority` and `reason`. Its
current valid combinations are:

| `granted` | `reason` | Meaning |
| --- | --- | --- |
| `true` | `AUTHORITY_GRANTED` | The required authority was granted |
| `false` | `MISSING_AUTHORITY` | The required authority was missing |

A granted decision with `MISSING_AUTHORITY`, or a denied decision with
`AUTHORITY_GRANTED`, throws `IllegalArgumentException`. Null authority or reason
throws `NullPointerException`. These two reasons are the complete current contract,
not a claim that they form a complete future policy model.

## Normalized Identity errors

| Code | Capability-level meaning |
| --- | --- |
| `AUTHENTICATION_FAILED` | Authentication could not be established |
| `INVALID_AUTHENTICATION_CONTEXT` | Supplied authentication context cannot be accepted as valid |
| `AUTHENTICATION_EXPIRED` | A previously valid authentication context has expired |
| `ACCESS_DENIED` | Access was denied |
| `IDENTITY_NOT_FOUND` | The requested identity was not found |
| `IDENTITY_PROVIDER_UNAVAILABLE` | The identity provider is unavailable |

`IdentityException` is a normalized capability failure. It requires a non-null
code, exposes it through `code()`, and uses only the enum name as its message. It
has no cause, suppression or stack trace. Provider-specific exceptions,
credentials, raw tokens, provider internals, stack traces and sensitive
authentication information must not cross the public Core contract. HTTP status
mapping belongs outside Identity Core.

## Provider independence

The public contract must not expose Keycloak SDK types, Spring Security types, JWT
library types, provider session objects, provider-specific exceptions, raw
credentials or raw tokens. Provider implementations must remain behind an
adapter/verified boundary and translate its behavior into these provider-neutral
types and normalized errors.

This Core contract does not select an Identity provider. The current Week-2 OIDC
runtime uses Keycloak; that implementation does not make Keycloak part of the Core
contract or settle future provider replacement.

## Current OIDC runtime adapter

The infrastructure adapter uses Spring Security OAuth2 Resource Server with a
Bearer JWT access token. The production `JwtDecoder` uses issuer discovery and
the provider's JWKS for signature verification. Its validator chain checks issuer,
timestamp/expiry (and `nbf` when present), an audience containing `tricore-api`,
and the payload claim `typ` exactly equal to `"Bearer"`. It does not trim or
case-fold that claim; missing or other values fail validation.

The observed local Keycloak token has three distinct type values:

| Location | Observed value |
| --- | --- |
| OAuth token response `token_type` | `Bearer` |
| JWT JOSE header `typ` | `JWT` |
| JWT payload claim `typ` | `Bearer` |

The payload claim, not the JOSE header, is the input to
`AccessTokenTypeValidator`.

`SpringSecurityCurrentPrincipal` accepts only an authenticated
`JwtAuthenticationToken` from the Spring Security context. In production, the
filter chain verifies the JWT before establishing that authentication. The
adapter maps its issuer and subject to the canonical `subjectId` described
above. It reads Workflow authorities only from
`resource_access.tricore-api.roles`, exposing the
exact values `workflow:create`, `workflow:activate`, `workflow:transition` and
`workflow:read`; unknown roles are ignored. Constructing an
`AuthenticatedPrincipal` manually is not evidence of production authentication.

The API authenticates with Bearer JWT, uses `SessionCreationPolicy.STATELESS` and
has its request cache disabled. HTTP session/JSESSIONID, form login and OAuth2
login are not authentication mechanisms for this API runtime. Missing or invalid
Bearer credentials return HTTP 401 with a safe `ApiError` containing
`AUTHENTICATION_REQUIRED`; client-supplied identity headers do not bypass this
boundary. A Bearer-authenticated state-changing POST was tested through the
Spring Security filter chain without a CSRF token and returned 201. That test
does not establish a general CSRF policy. Application authorization responses,
including Workflow/Gateway 403/404 behavior, belong to those boundaries rather
than an Identity filter-level authority rule.

Local configuration uses `OIDC_ISSUER_URI` and `OIDC_AUDIENCE`. The verified
local values are `http://localhost:8180/realms/tricore` and `tricore-api`,
respectively; no credential or token value belongs in this contract.

## Gateway consumer expectations

The future conceptual consumer flow is:

```text
External request
    -> Gateway
    -> Identity verification or resolution
    -> trusted principal/context
    -> authorization check
    -> downstream processing
```

A trusted principal/context must originate from a verified authentication
boundary. The OIDC runtime adapter provides the current verified JWT boundary;
this flow does not establish complete Gateway integration. The Gateway consumer
must not infer authentication from successful Java
object construction or copy unverified external identity claims into trusted
downstream state.

## Tests and evidence

The Core unit tests are pure Java JUnit tests. They prove current value-object
and exception behavior:

- valid principals and optional null `displayName`;
- rejection of null, empty and blank `subjectId`;
- rejection of null authority sets and sets containing null;
- defensive copying and an unmodifiable exposed authority set;
- rejection of `authenticated == false`;
- structural validation of `authenticationReference`;
- validation of `Authority` and `RequiredAuthority` values;
- valid and invalid `AuthorizationDecision` combinations; and
- availability of all normalized error codes and sanitized `IdentityException`
  message/cause behavior.

These Core tests do not prove runtime authentication, JWT verification, Keycloak
integration, Gateway integration or provider replacement.

Separately, `SpringSecurityCurrentPrincipalTest` uses synthetic authenticated
JWT tokens to verify deterministic identity for the same issuer and subject,
separation across different issuers or subjects, exact Base64URL encoding without
padding, UTF-8/Unicode behavior, preservation without normalization, and
rejection of missing or blank issuer or subject. Its verified-JWT Workflow
authority mapping tests remain separate from the identity-ID assertions. These
unit/security tests do not prove real Keycloak/JWKS integration on their own.

Additional unit and filter-chain results are `TokenTypeValidatorTest` 4/4,
`AudienceValidatorTest` 4/4, `IssuerAndTimestampValidatorTest` 2/2,
`SecurityConfigTest` 5/5 and `SpringSecurityCurrentPrincipalTest` 19/19. The
filter-chain tests cover safe 401 responses, rejection of spoofed identity
headers, no session on an anonymous protected request, and a Bearer POST without
a CSRF token. These use test fixtures or a mocked decoder, not a real IdP token.

A real local Keycloak run of `OidcKeycloakIntegrationTest` recorded `tests=3`,
`skipped=0`, `failures=0`, `errors=0`. It exercised the production decoder with a
valid Keycloak access token through discovery/JWKS, mapped the canonical
principal and four Workflow authorities, and rejected a forged signature. This
is local integration evidence, not proof of full-system end-to-end security.

## Limitations and deferred work

The current OIDC runtime and local Keycloak verification do not complete Gateway
authorization or its 403/404 integration, authority freshness and revocation,
provider replacement, or full backend regression. Issue #24 still needs PR/cross-review/merge. The
current evidence does not establish production readiness or full-system
end-to-end security.

The architecture baseline remains a modular monolith. This Core contract does not
turn a local provider integration into a permanent provider or deployment
decision.
