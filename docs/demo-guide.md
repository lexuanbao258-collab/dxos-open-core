# DX-OS Demonstration Guide — OLP 2026

> **Two demonstrations, two evidence levels:** (A) a **MOCK/DEVELOPMENT** React Portal in unmerged [Draft PR #51](https://github.com/lexuanbao258-collab/dxos-open-core/pull/51); (B) a **REAL-PROVIDER E2E TEST CANDIDATE** for the Helpdesk Backend with Keycloak and PostgreSQL in unmerged [Draft PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63). Do **not** represent (A) as proof of (B). Neither PR had merged into `main` at the October 25, 2026 documentation checkpoint.

## 1. Demonstration objectives

Explain how DX-OS provides reusable **Identity, Gateway, Workflow, and Data** capabilities for internal service applications.

The primary Backend acceptance scenario is **IT Helpdesk**: a requester submits a ticket, a coordinator triages and assigns it, an assigned worker processes it, and the requester confirms resolution. State versions and history must stay consistent.

**Facility Request** currently illustrates **UI component reuse through mock fixtures** in the Portal branch. It does **not** yet have equivalent real Backend E2E evidence.

Keep these responsibilities distinct:

- **Core:** generic capabilities, not IT/Facility-specific business rules.
- **Application:** domain roles, actor eligibility, business states, and transition conditions.
- **Portal:** displays information and invokes reviewed HTTP contracts; it does not decide real authorization.
- **Evidence:** a PASS claim requires an exact commit SHA, recorded environment, executed tests, and no relevant skipped assertions.

## 2. Demo A: Standalone Portal — mock/development

Follow [Installation Guide, section 3](installation.md) to check out Draft PR #51 and start the Portal. Use the branch's [Frontend Demo Guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue39-portal-ui/frontend/DEMO_GUIDE.md) as the source for exact UI flows:

1. Open `/session` and enter a **simulated employee session**, which is **not real SSO**.
2. Open Dashboard, Requests, and New Request. Submit an empty form to demonstrate validation, then create an **IT Support** mock request.
3. Switch to the **simulated handler** role. Open the same request and demonstrate review/start/resolve; switch back to the requester role for confirmation.
4. Display the timeline, search/filter, pagination, empty/loading states, mock network errors, mock 401/403/404 states, and the simulated `COMMIT_OUTCOME_UNKNOWN` behavior according to the Frontend guide.
5. Create a **Facility Request** using the shared UI components and view model. Do not claim this proves shared Backend Workflow execution.
6. Open `/integration` and show the **LIVE_BLOCKED** state. Mock behavior is **not** proof of real ACL, JWT authentication, database transactions, or provider integration.

Screenshots from the Portal branch should reference `docs/evidence/issue39-portal/` and the exact PR #51 HEAD. Demo fixture names are not verified real identities.

## 3. Demo B: IT Helpdesk Backend candidate — focused B06 verified, full integration gated

[Draft PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63) proposes **nine HTTP route families** under `/api/v1/mvp/requests`:

| Step | HTTP | Authorized actor | Expected result |
| --- | --- | --- | --- |
| 1. Create | `POST /` | Requester | `SUBMITTED`, canonical `requesterId` |
| 2. List | `GET /` | Caller authorized to read | `{items,nextCursor}` filtered by ACL |
| 3. Detail | `GET /{id}` | Caller authorized to read | `state`, versions, `allowedActions` |
| 4. Triage | `POST /{id}/triage` | Coordinator | `TRIAGED` |
| 5. Assign | `POST /{id}/assign` | Coordinator | Updated `assigneeId` and `assignmentVersion` |
| 6. Start | `POST /{id}/start` | Assigned worker | `IN_PROGRESS` |
| 7. Resolve | `POST /{id}/resolve` | Assigned worker | `RESOLVED` |
| 8. Confirm | `POST /{id}/confirm` | Requester | `CONFIRMED` |
| 9. History | `GET /{id}/history` | Caller authorized to read | Four Workflow transition records, consistent versions |

Do **not** invent an implemented `CLOSED` step: the current IT Helpdesk candidate ends at **CONFIRMED**. Do not confuse the older Request prototype under `/api/v1/requests` with the Helpdesk candidate contract. Do not fabricate `displayName`, actor labels, timeline notes, or attachment support in the Portal.

Essential negative-security evidence includes: anonymous or signature-tampered tokens returning `401`; prohibited operations `403`; hidden/cross-tenant resource responses `404`; stale assignment or Workflow versions `409`; eligibility revocation; and no automatic retry after an unknown commit outcome. Report a case as **PASS** only when its test has actually run on the relevant HEAD.

The [B06 candidate guide](https://github.com/lexuanbao258-collab/dxos-open-core/blob/e12d105c89400a5fd69dd6cc1fca670ff1f04f51/docs/evidence/issue53-b06-real-keycloak-helpdesk-e2e.md) documents real Keycloak access tokens, canonical JWT issuer+subject identity, nine HTTP routes, and disposable PostgreSQL. **Independent focused B06 PASS is now reported** at exact PR #63 HEAD `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`: [Yến's October 25, 2026 report](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935) records 2 Configuration + 31 Helpdesk HTTP/PostgreSQL tests (**33 PASS / 0 FAIL / 0 SKIP**) with real Keycloak 26.8.0 + PKCE, Java 21.0.12.1, Gradle 8.14.3, and disposable PostgreSQL 18.6. `BUILD SUCCESSFUL` and cleanup were reported. **This is not Portal integration, full SQL privilege acceptance or production release verification.**

## 4. Requirements before presenting real Portal-to-Backend integration

All evidence must refer to the **same accepted integration HEAD**:

- **Yến — Identity/Security:** focused B06 at PR #63 HEAD `e12d105c` has an independent PASS report; **still independently review** [PR #58](https://github.com/lexuanbao258-collab/dxos-open-core/pull/58) guarded SQL, production-aligned provisioning/runtime privileges, and other security gates.
- **Ánh — Portal/Data:** review APP-11 and HTTP Adapter against the actual DTO, keep `version` distinct from `assignmentVersion`, show `eligibleAssignees` only where authorized, and do not use client-generated actor identity as an authority.
- **Bảo — Backend/integration:** reconcile test evidence across branches and exact SHAs, verify Workflow/history consistency, ensure repeatable safe setup, and do not enable staging/LIVE without accepted gates.
- **License/compliance:** evaluate [Issue #38](https://github.com/lexuanbao258-collab/dxos-open-core/issues/38) separately; do not claim license or copyright acceptance before the relevant review is complete.

**Current checkpoint: `DRAFT / LIVE_BLOCKED`.** Do not label README screenshots, videos, or release notes as “E2E complete”, “production-ready”, or “released” before actual independently checked evidence and sign-off.

## 5. B06 verified evidence and remaining Portal acceptance checklist

| Field | Evidence to record |
| --- | --- |
| Focused Backend B06 PR / exact reviewed SHA | PR #63 / `e12d105c89400a5fd69dd6cc1fca670ff1f04f51` |
| B06 environment reported by Yến | October 25, 2026; Java 21.0.12.1; Gradle 8.14.3; PostgreSQL 18.6 disposable; Keycloak 26.8.0 with PKCE |
| B06 execution evidence | [Independent reviewer comment](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935); `BUILD SUCCESSFUL`, cleanup reported. See linked report for execution details |
| Focused B06 JUnit PASS / FAIL / SKIP | **33 / 0 / 0** (2 Configuration + 31 Helpdesk HTTP/PostgreSQL), exact HEAD only |
| Golden Path and key negative authorization cases | Covered in the focused B06 test report; **full Portal-to-Backend acceptance pending** |
| Real Portal HTTP integration and captured screenshots | Pending |
| Runtime privilege, provisioning, and migration gates | Pending |
| Independent reviewer decision | LIVE_BLOCKED |

Update the remaining rows only after their tests have run, linking redacted logs/PR comments, the reviewer, and the exact SHA. The B06 figures above are taken from Yến's linked independent report, **not** newly executed in this documentation PR. **Never enter estimated PASS counts.**
