# DX-OS OLP 2026 — Docker packaging and AWS staging (DRAFT)

**Tracking:** Issue #40. **Status:** partial deployment scaffold, not a verified end-to-end OLP environment.
The repository's currently merged `main` has not integrated the new Identity/SSO
(#24), protected Workflow HTTP/Gateway ACL (#22/#23), Portal (#39), or local
Object Storage adapter (Draft PR #43). **Do not expose this application publicly**,
claim OLP E2E acceptance, or deploy against the team's shared Supabase database
while these gates remain open.

## Supported scope of this draft

- `backend/Dockerfile`: a Java 21, multi-stage build using the repository's
  pinned Gradle wrapper and a non-root JRE runtime, without embedding secrets.
- `infra/compose.staging.yml`: one **fresh, isolated PostgreSQL 17.11** with a
  persistent named volume. Optional `app` profile builds a Backend image and
  mounts a separate private storage volume **after prerequisites are approved**.
  Database and application ports bind to `127.0.0.1` on the Docker host;
  nothing is published by this scaffold to public interfaces.
- `infra/staging.env.example`: non-secret template. The local copy
  `infra/.env.staging` is gitignored. Credentials must not appear in commits,
  shell history, screenshots, support logs, or client-side JavaScript.

**Not included / not yet proven:** Keycloak deployment, frontend container,
domain/HTTPS proxy, approved Workflow/Request schema initialization, local
Storage provider merge, independent Linux Docker build/boot, tests/CI, user
authorization/tenant ACL, automated backups, and AWS full-stack E2E.

## 1. Prepare secrets on a capable development machine

From the repository root (PowerShell):

```powershell
Copy-Item infra/staging.env.example infra/.env.staging
# Open infra/.env.staging with your editor and set strong, UNIQUE secrets for:
# STAGING_PG_PASSWORD and STAGING_DB_RUNTIME_PASSWORD.
# Do not paste secrets into this document, tickets, chat, or git.
```

On Linux, restrict the file: `chmod 600 infra/.env.staging`; on Windows use
appropriate NTFS ACLs. The example's database owner and runtime account are
**different**. The runtime account will **not** be automatically created.
The same password placed in the template file does not grant any database access
until an authorized operator provisions the matching role.

Compose interpolates variables even for inactive profiles, so both passwords
must be configured to validate/start the PostgreSQL-only baseline. When using
the eventual application container, Docker Compose environment values can be
visible to users authorized to inspect Docker; restrict Docker/host access.

## 2. Validate the Compose file (no cloud access)

```powershell
docker compose --env-file infra/.env.staging -f infra/compose.staging.yml config --quiet
```

This checks Compose syntax and required variable interpolation. It does **not**
prove Docker build, application boot, migrations, permissions, or security.
Run `git diff --check` from the repository root if reviewing the changes.

## 3. Start ONLY the isolated development PostgreSQL

```powershell
docker compose --env-file infra/.env.staging -f infra/compose.staging.yml up -d postgres
docker compose --env-file infra/.env.staging -f infra/compose.staging.yml ps
```

This creates a fresh named volume. The database listens through
`127.0.0.1:55426` on the Docker host by default, not the public IP.
An existing PostgreSQL service on that port is a reason to change
`STAGING_PG_LOOPBACK_PORT` in your private env file.

**This Compose intentionally creates only the PostgreSQL instance and its
default database/owner.** It does *not* run Flyway V1–V4, alter shared Supabase
roles, create a Workflow runtime role, seed definitions, or initialize Portal
data. A `pg_isready` healthy result is not proof that the app schema exists.
It is also not a database backup.

### Controlled first-time Workflow setup (separate, manual gate)

Use the established local workflow setup/runbook in
[`backend/README.md`](../../backend/README.md) to provision a **dedicated
Workflow schema**, migration owner, restricted runtime role, the approved V3/V4
bundle, and the precise grants required by `WorkflowPersistencePort`.
Follow the documented operator checks before applying migrations.
The legacy Request prototype V1/V2 uses `public` and requires **separate
approved initialization** if it is used; V3/V4 alone do not create Request tables.

Before enabling the `app` profile, confirm ALL of the following:

1. The target is an **isolated disposable/local database** with documented
   credentials, roles/schema and controlled one-time migrations — **never
   automatically migrate the shared Supabase project**.
2. The PostgreSQL runtime role specified in `infra/.env.staging` exists,
   has its separately provisioned password and only the necessary grants.
   Never use `STAGING_PG_OWNER` as the long-lived backend runtime login.
3. `DXOS_WORKFLOW_SCHEMA` agrees with the preapproved schema and the
   JDBC `currentSchema`; `SPRING_FLYWAY_ENABLED=false` is intentional.
4. Identity/Keycloak verified principal and fail-closed Gateway ACL (#22/#23/#24)
   are integrated and tested; no `permitAll` for Workflow/storage routes.
5. The selected `ObjectStoragePort` implementation (PR #43 if local) has been
   merged/reviewed, with the persistent storage mount and OS ACL verified.
   The existing `main` does **not** yet include PR #43.

## 4. Backend build/start — BLOCKED until the above gates

On a machine with **sufficient RAM and free disk** (not the current 1 GiB AWS EC2):

```powershell
# Packaging/build smoke only; does NOT assert app runtime works:
docker compose --env-file infra/.env.staging -f infra/compose.staging.yml --profile app build backend
```

Once the dependencies and explicit database/security approvals are ready:

```powershell
docker compose --env-file infra/.env.staging -f infra/compose.staging.yml --profile app up -d
```

`backend` is opt-in by design. A successful image build proves **packaging**,
not a successfully authenticated API or passing integration tests.
The Backend binds host `127.0.0.1:18080` by default; remote teammates cannot
connect to this port directly. For later shared use, introduce reviewed
HTTPS termination and secure Gateway routing, with only 443 public.

No Keycloak or Portal service is implicitly started by this draft; configure
them in separate reviewed integration work after #24/#39 respectively.

## 5. Stop/restart and protect volumes

```powershell
docker compose --env-file infra/.env.staging -f infra/compose.staging.yml down
```

`down` leaves the named database/storage volumes intact. **Never use
`down -v`** on a dataset you want to retain. Do not replace volumes or change
the database owner/password after initialization expecting an existing
PostgreSQL cluster to be reconfigured automatically. Confirm data with a
fresh-process read, and rehearse backup/restore to a separate isolated location.

Before touching important data: take a PostgreSQL logical backup
(e.g. `pg_dump`) using an authorized role, and a consistent backup of the
Storage volume while writers are paused. Keep backups encrypted and outside
the live volume; verify a restore into a disposable database/volume. AWS EBS
snapshots may be useful but must not be treated as a substitute for tested
application-consistent backup and recovery procedures.

## 6. AWS Free Plan `tricore-olp` constraints (observed 2026-10-17)

At the last operator check the EC2 Ubuntu 24.04 `t3.micro` had **911 MiB
total RAM**, **~504 MiB available**, **no swap**, and only **~4.4 GiB free on
root**. Docker 29.1.3 and Compose 2.40.3 were present; Git checkout of `main`
was `2115de3`. This is **not enough to claim** PostgreSQL + Keycloak + Java
Backend + Portal can run reliably together or to build the Java image there.
Do **not** run this full Compose stack on that EC2 without a separately
reviewed resource plan. AWS Free Plan still consumes finite credits/eligibility.

Keep AWS security groups limited to SSH from an approved source; do not open
5432, 18080, 8080 or Keycloak admin ports to the Internet. Restrict EC2 disk,
Docker group and private keys. If an approved proxy/HTTPS topology becomes
available, publish only the intended TLS endpoint, after tenant/ACL checks.

## Acceptance needed before closing #40

- [ ] Local and Linux `docker compose ... config --quiet` PASS on same SHA.
- [ ] Backend image builds from fresh clone with Java 21, verified by operator.
- [ ] Approved isolated DB migration + runtime roles; app boots securely, with
      tested 401/403/404 and no public unreviewed Workflow/file routes.
- [ ] Independent Keycloak (#24) and Portal (#39) integrated through HTTP;
      two real Workflow definitions shown with PostgreSQL history/restart.
- [ ] Approved Storage provider, tested persistent-volume upload/read/metadata
      and restart without granting frontend storage admin credentials.
- [ ] HTTPS, resource budget, backup/restore, secrets handling, docs,
      OpenAPI #37 and License/SPDX #38 checks reviewed.
- [ ] The team records exact source SHA, image tags, test commands and results;
      obtains independent approval before enabling shared AWS Staging.

**Not a production architecture or a claim of OLP acceptance.**
