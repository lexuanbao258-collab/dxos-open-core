# Applications — Documentation Index

> **Status:** DRAFT / REVIEW REQUIRED. This index does not approve business policies or LIVE functionality.
> **Owner:** Application (Ánh); reviewer assignments must be confirmed in the Document Register.

- [A02 source reconciliation and owner review handoff](business-contract-review.md)
- [Open business decisions](business-decisions.md)

The current Helpdesk scope is acknowledged **with conditions** by the Application Owner in [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6551136261). Final contract ratification and runtime acceptance remain pending.

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
- [Business Rules — PR #78 draft](https://github.com/lexuanbao258-collab/dxos-open-core/blob/91bcbe63083558723b1e418ee8d0ef538ecfcdd3/docs/03-applications/facility/business-rules.md)
- [Candidate API Contract](facility/api-contract.md)
- [Conceptual Data Model](facility/data-model.md)
- [Candidate Workflow Definition](facility/workflow-definition.md)
- [Acceptance Test Plan](facility/acceptance-tests.md)

**Status:** Full Facility business journey DEFERRED / POLICY OPEN / LIVE_BLOCKED. Current Facility rules are recorded in requirements, use cases and the decision register above. The linked `facility/business-rules.md` belongs to separate Draft PR #78 at the pinned snapshot and is absent on this branch. It is retained as an unapproved external reference; current A02 rules and decisions do not require its merge. Reconcile any future adoption separately. The second-definition Core reuse proof has a separate acceptance gate.

## Cross-cutting contracts and reviews

- [MVP scope](../00-project/scope-and-mvp.md)
- [System architecture](../01-architecture/system-architecture.md)
- [Canonical contracts](../contracts/)
- [Original ISP-BA-01–07 analysis — PR #68, preserved source](https://github.com/lexuanbao258-collab/dxos-open-core/tree/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis)
- [Business contract review — Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53)
- [Portal implementation — Issue #39](https://github.com/lexuanbao258-collab/dxos-open-core/issues/39)
- [Application documentation PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76)

The document inventory, named owners and independent reviewers must be reconciled in `docs/00-project/document-register.md`. Documentation review and runtime test acceptance are independent.

That register is absent at this branch HEAD. Owner/reviewer registration remains a governance follow-up; the A02 handoff records proposed review responsibilities without creating a project-level register.
