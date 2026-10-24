# Proposed PR #35 description — owner approval required

This file is proposed text only. Do not apply it to remote PR metadata before owner approval. The tested implementation SHA is pinned below; a later documentation/evidence-only commit may advance the PR tip. Check the [PR commit list](https://github.com/lexuanbao258-collab/dxos-open-core/pull/35/commits) for that tip. All evidence links target the PR branch.

---

## Summary

Implements the backend Supabase REST `ObjectStoragePort` adapter for Issue #26. This remediation rejects oversized metadata before sorting, UTF-16/Base64 transformation or JSON-tree allocation, bounds serialization, and avoids materializing provider JSON merely to check its size. Nine codec regressions and an upload EOF/drain regression were added.

The adapter preserves CREATE_ONLY provider enforcement, exact-version reads without current-version fallback, absolute response-body deadlines/cancellation, sanitized errors and no automatic application mutation retry. Core's consume-to-EOF upload behavior is unchanged. Gateway/deployment must provide inbound byte limits, upload/read timeouts, upload concurrency limits, staging quotas and reconciliation after unknown mutation outcomes. Keycloak remains the identity provider; user/tenant Gateway ACL is separate work.

**Fetched PR HEAD before this remediation:** `1997d96bdea50c288f74daee28d0ae4b56afc72f`.
**Fresh tested Java commit:** `be605c803e978ab89be533deccd37b653cb58c9d`.
The delta from the preceding tested Java commit `14b35586128465d42534b0edc1e700178fed5406` to fetched HEAD `1997d96` contained only documentation/evidence/capture-script changes, with no Java or build modifications. Current results below verify the later metadata fix separately.

## Current local verification

Windows 11, Eclipse Temurin JDK 21.0.12.1, Gradle 8.14.3. All figures are actual local executions, not GitHub CI runs. Full results use the repository's disposable local PostgreSQL 17.11 fixture, separate runtime/migration roles and trusted/wrong-CA TLS fixtures; no shared Supabase PostgreSQL was used.

| Verification | Suites | PASS | FAIL | ERROR | SKIP |
| --- | ---: | ---: | ---: | ---: | ---: |
| Focused Supabase/Core Data | 7 | 76 | 0 | 0 | 0 |
| Full backend tests and build | 51 | 878 | 0 | 0 | 0 |
| PostgreSQL integration subset | 9 | 192 | 0 | 0 | 0 |

See the [final review record and exact commands](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-final-review/review.md#fresh-execution), [focused XML](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-final-review/focused.xml) and [full backend XML](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-final-review/full.xml). PostgreSQL totals are a subset of the full backend total.

Fresh hosted Supabase: **NOT RUN**, because private authorized configuration was unavailable. The historical 19-pass hosted result below is not counted as fresh verification of this Java change.

## Historical verification (not new executions)

At prior tested Java source `14b35586128465d42534b0edc1e700178fed5406`:

- **Backend: 50 suites / 868 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, including **9 PostgreSQL suites / 192 PASS**. [Verification summary](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-postgres-17.11/verification-summary.md), [XML](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-postgres-17.11/full.xml).
- **Hosted Supabase: 19 PASS / 0 FAIL / 0 ERROR / 0 SKIP**, separate from the backend total. [Hosted XML](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-remediation/hosted.xml), [source provenance](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-remediation/hosted-source-sha256.txt).
- Earlier large-download backend evidence **676 PASS / 112 SKIP / 0 FAIL / 0 ERROR** was superseded by the 868-pass disposable PostgreSQL run at the same Java source. [Historical XML](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-large-download/full.xml). Historical reports were preserved.

## Review and remaining gates

See the [security assessment and remaining risks](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/evidence/issue-26-final-review/review.md), [architecture and staging protections](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/architecture/supabase-object-storage.md), and [PoC instructions](https://github.com/lexuanbao258-collab/dxos-open-core/blob/feature/issue26-supabase-clean/docs/poc/supabase-object-storage.md).

- Fresh hosted verification of the metadata change remains pending authorized private configuration; historical private-bucket/anonymous-denial evidence does not establish today's deployment state.
- Linux/macOS runtime verification remains **PENDING / NOT RUN**.
- **No successful GitHub CI run is claimed.** This review did not start GitHub CI.
- Untrusted-input staging can still wait indefinitely for EOF outside the provider deadline. Gateway/deployment protections and user-level authorization require separate verification before exposure.
- Independent security review and owner merge decision remain required. No Core/Workflow behavior, Gateway ACL, Flyway V1-V4 or additional public HTTP route changes are included.

Related to #26. This description does not close the issue or approve a merge.

**READY FOR INDEPENDENT REVIEW; not approved to merge.**
