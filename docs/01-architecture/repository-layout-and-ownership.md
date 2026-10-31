# DX-OS Repository Layout and Ownership — Migration-Safe Proposal

> Status: TEAM-SELECTED LAYOUT — 2026-10-31. Implementation migration and gate acceptance remain separate; not a source refactor or runtime acceptance.
> Source inventory: `main@f2063e5b70545e378729e8547d1d2d9ce217d3c9` (2026-10-31).
> Owners: Yến (Identity/Gateway); Bảo (Workflow/Data/backend integration); Ánh (Application/Frontend).
> Independent review: another team member for each area; architecture/contract changes require affected owners.
> Related: PR #77 (placeholder scaffold), PR #51/#71 (React Portal), PR #70 (BFF), PR #82/#83/#84 (draft documentation chain).
> Authority: Existing Core Java contracts and approved decisions override this proposed layout. The Document Register determines documentation lifecycle.

## 1. Purpose

Give contributors a stable navigation and ownership model without renaming Java packages, breaking imports, changing Flyway migrations, duplicating provider contracts, or disturbing stacked PRs. Distinguish **OBSERVED** files from **TARGET** locations; an unmerged PR is not part of `main`.

## 2. Verified current state on main

```text
dxos-open-core/
├── .github/workflows/
├── backend/
│   └── src/main/java/com/tricore/dxos/
│       ├── application/{authorization,identity}/
│       ├── core/{identity,workflow,data}/
│       ├── gateway/workflow/
│       ├── infrastructure/{identity,persistence,storage,workflow}/
│       ├── request/{controller,domain,dto,repository,service}/
│       └── common/{error,health}/
├── docs/{00-project,01-architecture,02-core,03-applications,
│         04-integration,05-testing,06-deployment,07-open-source,
│         08-decisions,09-evidence,10-reviews,11-poc}/
├── infra/
└── scripts/
```

- The existing `request/` module is source code, not a folder that may be silently renamed or removed.
- No tracked `frontend/` or `portal-bff/` exists on the inspected `main`. PR #51 has `frontend/`; PR #70 has `portal-bff/`.
- PR #77 proposes 75 placeholder `.gitkeep` paths, including `portal/frontend/` and `portal/bff/`. They are **not** a verified modular migration or permission to move provider adapters into `core`.
- The generic Workflow HTTP controller is not production-registered at this baseline. Do not enable it as a side effect of layout work.

## 3. Selected logical layout (target where explicitly labeled)

```text
dxos-open-core/
├── .github/
│   ├── workflows/                          # observed
│   ├── ISSUE_TEMPLATE/                     # target
│   └── PULL_REQUEST_TEMPLATE.md            # target
├── backend/
│   └── src/
│       ├── main/java/com/tricore/dxos/
│       │   ├── core/
│       │   │   ├── identity/               # provider-neutral types
│       │   │   ├── workflow/               # runtime/owned persistence port
│       │   │   └── data/                   # ObjectStoragePort and values
│       │   ├── application/
│       │   │   ├── identity/               # CurrentPrincipal port
│       │   │   ├── authorization/          # shared orchestration/policy ports
│       │   │   ├── helpdesk/               # business capability; TARGET migration
│       │   │   └── facility/               # second application; TARGET only
│       │   ├── gateway/                    # HTTP mapping/trust boundary
│       │   ├── infrastructure/             # provider adapters
│       │   │   ├── identity/
│       │   │   ├── persistence/
│       │   │   ├── storage/
│       │   │   └── workflow/
│       │   ├── request/                    # OBSERVED legacy/current request module
│       │   └── common/
│       └── test/                            # mirrors tested behavior
├── frontend/                                # target from PR #51/#71
├── portal-bff/                              # target from PR #70
├── infra/                                   # current assets plus reviewed setup
│   ├── keycloak/                            # target
│   ├── postgres/                            # target
│   ├── minio/                               # target
│   └── apisix/                              # PROPOSED only, not MVP runtime
├── docs/
│   ├── 00-project/
│   ├── 01-architecture/
│   ├── 02-core/{identity,workflow,data,gateway}/
│   ├── 03-applications/{helpdesk,facility}/
│   ├── 04-integration/
│   ├── 05-testing/
│   ├── 06-deployment/
│   ├── 07-open-source/
│   ├── 08-decisions/
│   ├── 09-evidence/
│   ├── 10-reviews/
│   └── 11-poc/
└── scripts/
```

The selected MVP path naming is `frontend/` and `portal-bff/` because PRs #51/#71 and #70 use those paths; consolidate under `portal/` only through a separate approved migration if desired. A different final naming scheme requires an approved migration ADR and coordinated updates; do not create duplicate runnable applications.

## 4. Module ownership and import rules

| Area | Accountable owner | Interfaces with | Dependency rule |
| --- | --- | --- | --- |
| `core/identity` and trusted principal adapter | Yến | Bảo / Ánh | Core must not import Keycloak or Spring Security implementation types. |
| `gateway/` and HTTP security boundary | Yến | Bảo Backend / Ánh Portal | Never trust caller headers or replace Application resource ACL with edge-only checks. |
| `core/workflow` and Workflow persistence semantics | Bảo | Yến / Ánh | Application must consume reviewed contracts; Flowable is a proposed adapter, not the Core runtime definition. |
| `core/data` and `infrastructure/{storage,persistence}` | Bảo | Ánh (PR #34 handoff), Yến | ObjectStoragePort is provider-neutral; application business data ownership is distinct. |
| `application/helpdesk/`, `application/facility/`, observed `request/` | Ánh business owner, Bảo Backend implementer | Yến security | Domain rules and resource eligibility belong to Application; preserve current `request/` until an approved bounded migration. No separate top-level Java `modules/` package is selected. |
| `frontend/` | Ánh | Yến session, Bảo API | No direct database, Keycloak admin, MinIO SDK or unregistered Workflow HTTP dependency. |
| `portal-bff/` | Yến security, Ánh consumer integration | Bảo Backend | Server-side OAuth session, CSRF and trusted token handoff; verify real E2E separately. |
| `infra/`, reproducible release scripts | Bảo integration lead | Yến / Ánh | Private local credentials, no destructive shared database operations. |
| `docs/` | Corresponding domain owner, independent reviewer | All | Registered document role/lifecycle and pinned provenance prevail over folder naming. |

Provider-specific adapters belong under `infrastructure/` (or an explicitly reviewed external adapter module), **not** under `core/*/adapter/`. Four logical capabilities do not require four services or a fabricated `core/gateway` Java package.

## 5. Migration and Pull Request policy

1. **Inventory** the actual `main` tree and every affected open PR base/head SHA.
2. **Record** a migration decision listing paths, owners, imports, tests, Docker mounts, Gradle paths, docs, API URLs and dependency impact.
3. **Choose one canonical frontend/BFF path**, after reviewing #51/#70/#71 and the unused placeholder paths from #77.
4. **Do not move production source or applied Flyway V1–V4** as part of a documentation or placeholder-only PR.
5. **Keep each migration bounded**: one coherent module, mechanical renames separately from logic changes; preserve history and run compile/test/link validation.
6. **Verify** Java packages/imports, Gradle tasks, frontend build, Docker Compose paths, scripts, README links, GitHub Actions and E2E on a pinned integration SHA.
7. **Review independently**, reconcile conflicts and select the approved merge order. No blanket merge of stacked PRs.

## 6. Acceptance checklist for this layout proposal

- [ ] Confirm current vs target paths with the reviewed Git tree.
- [ ] Architecture owners approve the provider-neutral Core vs infrastructure split.
- [x] Team layout direction selects Application children `application/helpdesk/` and `application/facility/` rather than `modules/`.
- [ ] Confirm `frontend/` + `portal-bff/` as the effective integration paths when their PRs are brought into the baseline.
- [ ] Bảo confirms no impact on `request/`, build graph or Flyway V1–V4.
- [ ] Review PR #77: accept only useful placeholders, or supersede its unused scaffold with recorded rationale.
- [ ] Agree how docs canonical ownership and Document Register relate to #82 → #83 → #84.
- [ ] Run link/lint checks on the final documentation PR.
- [ ] Record independent PASS / CONDITIONAL / BLOCKED at exact reviewed SHA.

## 7. Clear capability taxonomy

- `core/identity`: stable principal/authority value contracts, not an authentication-server implementation.
- `application/identity`: trusted-principal consumer port (`CurrentPrincipal`) rather than a duplicate identity provider.
- `application/authorization`: reusable authorization orchestration at application boundary; ticket-specific resource rules belong to `application/helpdesk`.
- `application/helpdesk`: owns ticket model, rules, use cases and application-specific ports. `application/facility` is a candidate second consumer of unchanged Core contracts.
- `infrastructure/*`: concrete OIDC, PostgreSQL, object storage and Workflow adapters; do not manufacture vendor packages before implementation.
- `gateway`: HTTP/security boundary; APISIX remains a proposed edge adapter and is not a prerequisite to local MVP.

## 8. Deferred / not authorized here

No code moves, mass file deletion, migration rewrite, new Java modules, opening Workflow HTTP, APISIX/Flowable runtime claims, provider switches, auto-merge or release approval. A successful documentation review confirms navigation guidance only, not architectural enforcement or runtime correctness.
