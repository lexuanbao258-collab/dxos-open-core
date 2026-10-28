# ADR-007 - MVP object storage provider

**Status:** ACCEPTED architecture direction; implementation and integration acceptance PENDING
**Decision date:** 2026-10-28

## Context and decision

DX-OS is a Java 21 / Spring Boot modular monolith with Hexagonal Architecture. Data Core owns the provider-neutral ObjectStoragePort and its value/error semantics; Infrastructure owns technical adapters. Workflow and each Application retain their own data and authorization responsibilities.

The selected MVP runtime uses **private PostgreSQL 17** for relational data, **local Docker MinIO** for object content, and **Keycloak** for identity. The MinIO adapter implements the existing Data Core port in Infrastructure. MinIO uses an S3-compatible protocol; this decision does **not** select AWS S3 deployment or assert tested AWS compatibility. Supabase Storage and filesystem-only object storage are excluded from the new implementation scope. Historical PRs remain historical.

At the PR #73 base (e06fa429cfd92b032c166eb3ebf32803d13191c2), source contains the Core contract and an in-memory test fake; infrastructure.storage has no production adapter. [PR #34](https://github.com/lexuanbao258-collab/dxos-open-core/pull/34) is an unmerged Draft candidate at 90558efc9dc356b7e94db09fcc77b19625ea97cc. Its description reports compilation and unit/configuration tests but **real MinIO integration tests NOT VERIFIED**.

## Consequences and acceptance

Applications authorize attachment access and own business metadata; they do not expose arbitrary object keys or provider credentials. The adapter must preserve existing stream, length, metadata, CREATE_ONLY, OVERWRITE, delete and normalized-error semantics. Unknown writes require reconciliation, never blind replay. PostgreSQL and MinIO do not share an ACID transaction; ordering, orphan recovery and cleanup remain explicit Application concerns.

Implementation acceptance requires a pinned commit, disposable local MinIO, private configuration, real-provider contract/concurrency and failure/restart tests, and reviewed Application integration. This architecture decision is not a runtime or release PASS.

See [storage adapters](../02-core/data/storage-adapters.md), [Data architecture](../02-core/data/data-architecture.md), and the [canonical Data contract](../contracts/data-core.md).
