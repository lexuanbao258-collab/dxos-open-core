# Facility Maintenance — Candidate Application API Contract

> **Status:** DRAFT / POLICY OPEN / LIVE_BLOCKED
> **Owner:** Application (Ánh); independent reviewer and approval pending
> **References:** Issue #53, PR #60, PR #76, PR #78; [requirements](requirements.md), [use cases](use-cases.md), [business rules](business-rules.md)

## Purpose and scope

Define proposed HTTP consumer requirements for Facility Maintenance without asserting that a Facility Backend is available. IT Helpdesk is the priority for initial LIVE MVP; Facility's complete business journey is deferred. A second Workflow definition reuse demonstration is a separate acceptance target.

## Current availability

| Capability | Current classification |
| --- | --- |
| Portal Facility screens | MOCK / DEVELOPMENT |
| Facility Backend list, detail and business mutations | NOT APPROVED / NOT VERIFIED |
| Facility Workflow definition, transitions and HTTP mapping | PROPOSED / POLICY OPEN |
| Attachment upload, metadata and evidence | NO APPROVED FACILITY API |
| Keycloak/BFF security and real E2E | LIVE_BLOCKED |

**Do not reuse the nine IT Helpdesk routes as Facility endpoints without a reviewed contract.** In particular, IT assignment is a separate Application write; the Facility candidate may model `ASSIGNED` as Workflow state. This is an unresolved decision.

## Proposed capabilities (no URLs assigned)

| Consumer capability | Required contract decision |
| --- | --- |
| Submit request | Allowed creation authority, tenant, validated title/description/location, initial state and definition binding |
| Authorized list/detail | Resource visibility, pagination, field redaction and history policy |
| Intake/accept | Exact eligibility, wrong-scope handling and approved transition |
| Assign technician | Current eligibility, write version semantics, independent business/Workflow ownership |
| Begin and report repair | Required outcome, concurrency and transactional consistency |
| Acceptance and closure | Authorized acceptance actor; whether acceptance and closing are separate |
| Review history and evidence | Event schema, disclosed actors and redaction rules |
| Attachment integration | Separate authorized object-storage API and safe failure handling |

## Security and failure rules

- Backend must derive actor identity from verified OIDC, never from browser role or spoofable user headers.
- Enforce exact authority and resource/tenant visibility on each protected read and write.
- Preserve 401 unauthenticated; 403 visible-but-forbidden; 404 hidden, cross-tenant or nonexistent; 409 version conflict, subject to final approved API conventions.
- No blind mutation retry on ambiguous `COMMIT_OUTCOME_UNKNOWN`; no unauthorized field exposure.
- The Portal consumes published Backend contracts through reviewed BFF HTTPS and CSRF boundary; never direct database or provider-key access.

## Open contract decisions

No Facility route path, request DTO, response schema, state transition key, assignment version, acceptance/closure actor, rejection or rework path is ratified by this draft. Record these in Issue #53 / approved ADRs before implementation.

**Disposition:** CANDIDATE DESIGN ONLY. No LIVE claim, production endpoint registration or E2E approval.
