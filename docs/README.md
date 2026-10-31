# DX-OS Open-Core — Documentation Portal

> Status: RESTRUCTURE IN PROGRESS; navigation inventory of tracked `main` as of 2026-10-31. Document lifecycle and canonical identity require approved review/Document Register; folder presence does not establish acceptance.

## Start here

- [Project overview](00-project/project-overview.md)
- [Problem statement](00-project/problem-statement.md)
- [Product vision](00-project/product-vision.md)
- [Scope and MVP](00-project/scope-and-mvp.md)
- [System architecture](01-architecture/system-architecture.md)
- [Core/Application boundary](01-architecture/core-application-boundary.md)
- [Dependency rules](01-architecture/dependency-rules.md)
- [Repository layout and ownership](01-architecture/repository-layout-and-ownership.md)
- [Deployment prerequisites](06-deployment/prerequisites.md)

## Tracked Core capability architecture

- [Identity](02-core/identity/identity-architecture.md)
- [Workflow](02-core/workflow/workflow-architecture.md)
- [Data](02-core/data/data-architecture.md)
- [Storage adapters](02-core/data/storage-adapters.md)
- [Gateway](02-core/gateway/gateway-architecture.md)

## Application and downstream documentation

- `03-applications/{helpdesk,facility}/`: placeholders on main; draft business documentation in PR #76/#82 and Issue #53.
- `04-integration/`, `05-testing/`, `07-open-source/`, `09-evidence/`, `10-reviews/`, `11-poc/`: placeholders on current main; do not infer tested acceptance.
- [ADR-007 Data Storage](08-decisions/ADR-007-data-storage.md): historical/proposed status determined by the document itself, not this index.
- Supporting PR chain: [#82](https://github.com/lexuanbao258-collab/dxos-open-core/pull/82) → [#83](https://github.com/lexuanbao258-collab/dxos-open-core/pull/83) → [#84](https://github.com/lexuanbao258-collab/dxos-open-core/pull/84). Original import [#78](https://github.com/lexuanbao258-collab/dxos-open-core/pull/78) overlaps #82; Application docs [#76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) overlap. No duplicate PR was deleted or silently merged.

## Migration safeguards

1. Preserve historic content and source SHA pins; do not silently change a document ID or approved decision.
2. Do not move SQL fixtures referenced by test code or rewrite applied Flyway V1–V4.
3. For duplicates, first compare exact contents and file links, then select canonical/supplementary roles through an owner decision.
4. No drafted architecture, diagram, tests or source commentary proves deployed runtime.
5. Review links and selected integration SHA after any accepted documentation merge.
