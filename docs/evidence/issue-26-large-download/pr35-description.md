# Issue 26: Supabase streaming deadlines and large transport deliveries

> **Historical PR description draft.** Pre-commit and PostgreSQL-pending statements below describe the earlier review. For the final evidence at tested commit `14b35586128465d42534b0edc1e700178fed5406`, see the [current verification summary](../issue-26-postgres-17.11/verification-summary.md): 868 PASS, no failures/errors/skips; Linux/macOS verification remains **PENDING**. This local draft has not been published to PR #35.

Reviewed timeout remediation baseline: `0202ae87747fee558882c464f1198deb8abec3d3`. **PENDING VERIFICATION; not approved to merge.** This description is prepared for PR #35; the large-download follow-up remains uncommitted and these local results are not CI results.

The adapter enforces an absolute response-body deadline and cancels the HTTP subscription for stalled or idle lazy responses. A timed-out mutation may have committed remotely; no automatic mutation retry occurs. Version-pinned reads, length checks, sanitized errors and stream ownership are preserved.

The follow-up removes the 64 KiB transport-batch rejection. A single large ByteBuffer or a larger aggregate list is consumed directly through one delivery cursor. The application makes no batch-sized copy and requests the next batch only after draining the previous batch. Retention is limited to one publisher-sized delivery; there is no fixed byte cap on transport-owned memory or queue of deliveries.

Fresh Windows JDK 21 verification of the uncommitted follow-up:

- Focused: 66 passed; zero failures/errors/skips; BUILD SUCCESSFUL in 10s.
- Full test/build: 788 entries, 676 passed, 112 existing PostgreSQL skips, zero failures/errors; BUILD SUCCESSFUL in 28s.
- Hosted Storage: 19 passed, 0 failures, 0 errors, 0 skips; BUILD SUCCESSFUL in 1m 6s (user reported); sanitized capture verified.
- Large-delivery integrity, incremental 32 MiB streaming, backpressure, close, deadlines, provider errors, and a real socket stall after 300,000 bytes are covered. Existing timeout and snapshot/version consistency tests passed.

[Current evidence](../issue-26-storage.md), [fresh run record and artifacts](run-record.md). All 30 historical hash entries verify against `0202ae8`, accounting explicitly for CRLF checkout representation. Historical baseline counts (56 focused; 666 full passed plus 112 skipped; 19 user-supplied hosted passed) apply only to that baseline. The historical hosted manifest is read from baseline Git for verification; the current hosted manifest belongs to the latest Large Download capture. Future commit SHA: NOT YET ASSIGNED. The new Java changes have separate fresh hashes and require new verification if modified again.

Remaining gates: Linux JDK 21 worker verification PENDING; PostgreSQL regression PENDING approved disposable DB, roles and TLS fixtures; latest hosted Storage verification PASS 19/19; new CI verification NOT RUN. No shared PostgreSQL connection was made. No Core contract, Workflow, migration, MinIO or AWS SDK change is included.
