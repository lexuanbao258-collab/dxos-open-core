# Contributing to DX-OS Open-Core

DX-OS Open-Core is a TRICORE student-team project for the OLP 2026 open-source software contest. Contributions that improve its reusable Identity, Gateway, Workflow, Data, and internal-service application capabilities are welcome. A successful contribution is **reviewable, reproducible, and within an agreed scope**; a merged commit is not automatically end-to-end acceptance.

## Before opening a pull request

1. Read [README.md](README.md), [the architecture baseline](docs/architecture/README.md), and applicable [Core contracts](docs/contracts/).
2. Search existing GitHub Issues and PRs before creating a duplicate. Open or reference an Issue specifying the problem, user value, acceptance criteria, exclusions, and owner.
3. For security, license, database migrations, identity/authorization, or interface-contract changes, request review from the relevant area owner. Do not bypass a security gate merely to get a demo running.
4. Develop on a **separate branch** from the appropriate agreed base. When a PR deliberately depends on another unmerged PR, declare the stacked base and exact dependency HEAD; do not silently merge it into `main`.

## Scope and implementation boundaries

- **Core vs application:** reusable Workflow/Data/Identity mechanisms belong to Core; IT Helpdesk and Facility rules belong to application code. The Portal consumes an approved HTTP contract; it is not an authority engine or direct database client.
- **Trust boundary:** use verified identity and server-side resource/tenant authorization. Never accept `X-User`, `X-Role`, local demo roles or arbitrary client fields as proof of authority.
- **Transactions:** preserve documented Workflow state/history atomicity, policy audit invariants, resource visibility-before-403 behavior, optimistic versions, and fail-closed behavior. Do not automatically retry an unknown commit outcome.
- **Database:** do not modify deployed/applied Flyway V1–V4 bytes/checksums. Never use `flyway repair` or execute migrations, DDL, role changes, smoke writes, or cleanup against shared/staging/production databases without a separate explicit approval for that exact environment and operation.
- **MVP:** prefer small, testable IT Helpdesk end-to-end improvements; do not add a new framework or external provider without a reviewed need.

## Local development

**Backend (from `backend/`):** JDK 21 + Gradle Wrapper. See [backend/README.md](backend/README.md) and [installation guide](docs/installation.md).

```powershell
java -version
.\gradlew.bat test
.\gradlew.bat build
```

Some PostgreSQL/Keycloak integration tests require separately provisioned **disposable** infrastructure and opt-in environment variables; ordinary `test` success does not prove a live end-to-end flow.

**Frontend (unmerged candidate):** the React/TypeScript/Vite Portal currently lives in [Draft PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51), **not on `main`**. Consult its [frontend README](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/README.md) and [mock demo guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/DEMO_GUIDE.md) for branch-specific commands. Mock UI evidence is not real Backend/SSO evidence.

## Pull request checklist

- Link the Issue and identify the exact base branch, target HEAD, purpose, change scope, and exclusions.
- Explain APIs/contracts changed, affected trust boundaries, migrations, environmental assumptions, and backward-compatibility implications.
- Add focused tests for success and negative scenarios; include command, exact commit SHA, environment, PASS/FAIL/SKIP counts, and known limitations. Distinguish source inspection from independent execution.
- Confirm `git diff --check`, no secrets or test credentials in Git, and no unrelated files.
- Request review from the responsible area owners. Resolve actionable comments; do not self-certify another contributor's independent security review.
- Keep risky candidate features **Draft / LIVE_BLOCKED** until their explicit acceptance gates are met.

Recommended commit naming: `feat(scope): ...`, `fix(scope): ...`, `test(scope): ...`, `docs(scope): ...`. Keep commits narrowly scoped; squashing or rebasing someone else's reviewed history requires coordination.

## Reporting a security issue

Follow [SECURITY.md](SECURITY.md). Do **not** post exploit details, bearer tokens, passwords, private user data, or secret configuration in a public Issue or PR. Use appropriately redacted evidence.

## Licensing and attribution

See [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38). Apache-2.0 is the selected direction for materials the applicable copyright owners can license, but the root LICENSE, source-origin/notice inventory, and SPDX review are still a separate acceptance process. Do not rewrite third-party copyright notices or historical applied SQL solely to add license headers.

Thanks for keeping changes small, accurate and independently reviewable.
