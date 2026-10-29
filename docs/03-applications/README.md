# Applications — Documentation Index

> **Status:** DRAFT / REVIEW REQUIRED. This index does not approve business policies or LIVE functionality.
> **Owner:** Application (Ánh); reviewer assignments must be confirmed in the Document Register.

## IT Helpdesk — initial MVP priority

- [Requirements](helpdesk/requirements.md)
- [Use Cases](helpdesk/use-cases.md)
- [Business Rules](helpdesk/business-rules.md)
- [Application API Contract](helpdesk/api-contract.md)
- [Data Model](helpdesk/data-model.md)
- [Candidate Workflow Definition](helpdesk/workflow-definition.md)
- [Acceptance Test Plan](helpdesk/acceptance-tests.md)

**Status:** Proposed candidate; Portal MOCK and fake-adapter tests do not establish real Portal → BFF → Backend → PostgreSQL E2E. The Helpdesk candidate ends at `CONFIRMED`, not production `CLOSED`. `assign` is an independent Application command, not a Workflow transition.

## Facility Maintenance — deferred full journey

- [Requirements](facility/requirements.md)
- [Use Cases](facility/use-cases.md)
- [Business Rules — PR #78 draft](https://github.com/lexuanbao258-collab/dxos-open-core/blob/docs/import-workspace/docs/03-applications/facility/business-rules.md)
- [Candidate API Contract](facility/api-contract.md)
- [Conceptual Data Model](facility/data-model.md)
- [Candidate Workflow Definition](facility/workflow-definition.md)
- [Acceptance Test Plan](facility/acceptance-tests.md)

**Status:** Full Facility business journey DEFERRED / POLICY OPEN / LIVE_BLOCKED. The linked Facility business-rules file is owned by the separate [Draft PR #78](https://github.com/lexuanbao258-collab/dxos-open-core/pull/78) and is **not yet on this branch**. Do not merge this index while the cross-PR dependency is unresolved. The second-definition Core reuse proof has a separate acceptance gate.

## Cross-cutting contracts and reviews

- [MVP scope](../00-project/scope-and-mvp.md)
- [System architecture](../01-architecture/system-architecture.md)
- [Canonical contracts](../contracts/)
- [Business contract review — Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53)
- [Portal implementation — Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39)
- [Application documentation PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76)

The document inventory, named owners and independent reviewers must be reconciled in `docs/00-project/document-register.md`. Documentation review and runtime test acceptance are independent.
