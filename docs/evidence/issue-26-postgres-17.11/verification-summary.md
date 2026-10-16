# PR 35 PostgreSQL verification summary

Reviewed: 2026-10-16. Issue #26; branch `feature/issue26-supabase-clean`.
Exact tested commit: **`14b35586128465d42534b0edc1e700178fed5406`**.
The read-only Git checks confirm that SHA and a clean worktree at `D:\olp\issue26-pgverify`.

Reported execution environment: Windows 11, JDK 21, disposable local Docker PostgreSQL **17.11**, separate runtime/migration database roles and TLS fixtures. The supplied run account records **BUILD SUCCESSFUL in 3m 3s**, **LOCAL_WORKFLOW_VERIFICATION=PASS**, and successful disposable-container cleanup. This review validated existing reports; it did not rerun Gradle or connect to a database. No shared Supabase PostgreSQL connection was made.

## Latest clean-checkout regression

**50 suites; 868 PASS; 0 FAIL; 0 ERROR; 0 SKIP.** All nine PostgreSQL integration suites executed: **192 PASS**, with no failures, errors or skips in any suite.

| PostgreSQL integration suite | Passed |
| --- | ---: |
| PostgresWorkflowPersistenceIntegrationTest | 56 |
| WorkflowApprovedMigrationIntegrationTest | 19 |
| WorkflowDevelopmentSmokeIntegrationTest | 12 |
| WorkflowEventTriggerGuardIntegrationTest | 28 |
| WorkflowEventTriggerReviewIntegrationTest | 8 |
| WorkflowIsolationIntegrationTest | 46 |
| WorkflowRuntimePrivilegeAuditIntegrationTest | 3 |
| WorkflowSchemaBootstrapIntegrationTest | 13 |
| WorkflowSecurityDefinerReviewIntegrationTest | 7 |
| Total | 192 |

Parsed suite counters and individual testcase outcomes agree. All 868 HTML labels/outcomes agree with XML. Recapturing the 50 raw Gradle XML files from the clean checkout in an isolated temporary local clone reproduces every sanitized XML element/attribute and the HTML content (normalizing CRLF/LF). Suite start timestamps span `2026-10-16T12:57:56.198Z` through `2026-10-16T13:00:44.479Z`.

## Artifacts and provenance

- [Sanitized XML](full.xml), [sanitized HTML](full.html), [empty skipped list](full-skipped.txt), [empty source-change manifest](full-source-sha256.txt), and [artifact SHA-256 hashes](artifact-sha256.txt).
- The original supplied XML/HTML/skip list are unchanged. The source-change manifest was completed after fixing capture: zero bytes means no modified/untracked backend Java/Gradle files relative to the tested commit; it is not a complete source-tree hash. Git establishes the commit identity; reports contain outcomes/timestamps rather than a commit identifier.
- Historical [large-download full results](../issue-26-large-download/full.xml) remain **676 PASS / 112 SKIP / 0 FAIL / 0 ERROR** (788 entries, 50 suites), with the original [112-name skipped list](../issue-26-large-download/full-skipped.txt). Earlier remediation baseline results also remain unchanged. The 112 historical gated entries expand to 192 executed PostgreSQL cases when enabled, giving `676 + 192 = 868`; skips are never counted as passes.
- Historical [overview](../issue-26-storage.md), [run record](../issue-26-large-download/run-record.md), and [PR description draft](../issue-26-large-download/pr35-description.md) now point here while retaining their earlier records. Separate historical hosted Storage results are not added to this backend total.

## Capture fixes and review checks

[capture.ps1](../issue-26-remediation/capture.ps1) resolves an omitted/empty/whitespace `OutputDirectory` to `$PSScriptRoot` after parameter binding, and wraps manifest generation in `@(...)` so `WriteAllLines` receives an empty array instead of null. Verified on **Windows PowerShell 5.1.26100.9711** with omitted, empty, whitespace and explicit output paths (including spaces); all four captures passed with empty source manifests and zero-byte skip lists. No Java or build changes were needed.

Secret scan of all files under `docs/evidence/` found no credential-bearing URLs, JWTs, private keys, provider keys, AWS/GitHub tokens, long bearer tokens or secret assignments. Latest artifacts contain no provider hostnames, logs, properties, failure messages or stack traces. Existing provider-host matches in historical reports were traced to source test labels; older Issue #25 documentation also contains endpoint references. Those existing records are preserved, not newly staged credentials. No raw Gradle report, environment file, credential or TLS key is included in the review set.

`git diff --check`: **PASS**, including separate whitespace checks for new evidence files. Historical report artifacts are unchanged. Java, Workflow, Flyway and MinIO are unchanged. No commit, push, merge, rebase or reset was performed; the Git index remains unchanged.

Staging review scope: this directory's six files; `docs/evidence/issue-26-remediation/capture.ps1`; `docs/evidence/issue-26-storage.md`; and the two historical Markdown files linked above. All changes are left unstaged for review.

**Ready to commit this evidence/script update: YES.** Linux/macOS worker verification remains **PENDING**. No new CI or merge approval is claimed.
