# DX-OS Open-Core — Documentation

Status: DOCUMENTATION RESTRUCTURE IN PROGRESS

## Documentation Structure

| Section | Purpose |
|---|---|
| 00-project | Project context, scope and product vision |
| 01-architecture | System architecture and dependency rules |
| 02-core | Identity, Workflow, Data and API Gateway capabilities |
| 03-applications | IT Helpdesk and Facility applications |
| 04-integration | Cross-module and full-stack integration |
| 05-testing | Test strategy, specifications and acceptance |
| 06-deployment | Installation, configuration and operations |
| 07-open-source | Licensing, attribution and release readiness |
| 08-decisions | Architecture Decision Records |
| 09-evidence | Verification evidence |
| 10-reviews | Independent reviews and findings |
| 11-poc | Proof-of-concept documents |

## Existing Canonical Documentation

The following existing directories remain authoritative
during the migration:

- [Architecture](architecture/README.md)
- [Core Contracts](contracts/README.md)
- [Evidence](evidence/README.md)
- [Proof of Concept](poc/README.md)

## Migration Rules

1. Do not delete or rewrite historical evidence.
2. Do not move SQL fixtures referenced by Gradle or tests.
3. Do not change public contracts without approval.
4. Preserve existing Issue and PR traceability.
5. Architectural proposals are not implemented features.
6. Acceptance requires tests on a pinned commit.

## Current State

The numbered documentation directories are being prepared.
Existing canonical documents remain at their original paths
until a reviewed migration explicitly replaces them.
