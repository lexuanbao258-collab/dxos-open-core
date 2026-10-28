# MVP deployment prerequisites

**Status:** TARGET PREREQUISITES - no verified clean-install runbook or integrated release claim

The selected local environment requires Java 21 for the Spring Boot modular monolith, private PostgreSQL 17 for relational state, local Docker MinIO for object content, and Keycloak for identity. Keep credentials outside Git and use private endpoints and reviewed network bindings. MinIO is an S3-compatible server, not an AWS S3 deployment.

Before executable setup instructions, verify the exact integration commit, accepted MinIO adapter, database migration and privilege evidence, Keycloak client/issuer setup, approved ingress, and real-provider tests. PR #34 remains Draft and reports real MinIO integration **NOT VERIFIED**. This page does not authorize starting shared or production services.

See the [selected topology](../01-architecture/deployment-topology.md), [ADR-007](../08-decisions/ADR-007-data-storage.md), and [storage adapter evidence gates](../02-core/data/storage-adapters.md).
