# A02 — Business Contract Source Reconciliation and Review Handoff

**Review date:** 2026-11-01 (Asia/Bangkok)<br>
**Disposition:** CONDITIONAL — prepared for independent owner review; final Business Contract ratification pending; LIVE_BLOCKED.<br>
**Owners:** Nguyễn Ngọc Ánh — Application; Bảo — Backend/API; Yến — Identity/Security.

This is a source and documentation handoff, not an independent runtime test report, owner approval, PR approval or implementation instruction. No AI/Codex check is entered as official test evidence. The acceptance plans retain NOT RUN / POLICY OPEN until verified external evidence and an independent decision are recorded.

## Audited baseline and current source status

Workspace `D:\olp\dxos-docs-a02`; branch `docs/application-anh-docs`; initial HEAD `78727a40866c2a25b8f9b324e3fcfc6cc9610d14`. The earlier A02 audit recorded an initially clean tree; this preservation pass began with 14 modified tracked Application documents and the two untracked A02 review/decision documents. Origin fetch/push URL is `https://github.com/lexuanbao258-collab/dxos-open-core.git`. The earlier read-only remote ref verification recorded that same Application head and `main@2a94a46579c4d3d9999b0da831d9c3bb505cc3e2`; the continuation rechecked PR #76/#95/#97 metadata and the relevant owner discussions. Local `origin/main` is only a cached reference; the pinned remote main above is the earlier comparison reference. No branch synchronization or history rewrite is part of A02.

| Source | Verified snapshot | Interpretation for A02 |
| --- | --- | --- |
| [Issue #53](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53) | OPEN; body updated 2026-11-01; original description and 16 comments inspected | Business/API ratification and exact integration baseline remain outstanding; preserve historical decisions with their scope |
| [PR #60](https://github.com/lexuanbao258-collab/dxos-open-core/pull/60) | OPEN / DRAFT; `9f1c4972324798c3cf776d2ee79615b9942b3078` | Original business/API candidate; not approved LIVE contract |
| [PR #68](https://github.com/lexuanbao258-collab/dxos-open-core/pull/68) | OPEN / non-draft; `c31b6509a6e27e24c0305582a79562a480454594`; seven original-analysis files | Historical analysis/provenance, all DRAFT; no business rule ratification |
| [PR #73](https://github.com/lexuanbao258-collab/dxos-open-core/pull/73) | MERGED 2026-10-28; head `2bda552126cbe5850dd17b88c733c6e42ad6a2dd`; merge `0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed` | Numbered documentation layout accepted into main; runtime/API approval is not implied |
| [PR #74](https://github.com/lexuanbao258-collab/dxos-open-core/pull/74) | OPEN / DRAFT; `f943d33c7b7c003fc44d0234e4c912f24dcee847` | Backend lineage; not merged merely because #95 selects its source |
| [PR #76](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76) | OPEN / non-draft; base `main`, base SHA `0f0c3ba0c3eecda0a5c4a798a3cc14b808351bed`; head `78727a40866c2a25b8f9b324e3fcfc6cc9610d14`; 15 files before A02 | Local branch matches PR head; PR body still cites older `59be411…`, so actual metadata/file diff governs |
| [PR #95](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95) | OPEN / DRAFT; `fe55347908eed6a351ca9724be899138f7683081`; 25 files | Review-only composite Backend source; nine-route DTO comparison reference, not selected LIVE baseline |
| [PR #97](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97) | OPEN / DRAFT; `22bc977b894f91d44a836cb4c39bf4f0da566247`; stacked on #95; five files | Proposed scoped 404 compatibility patch; not incorporated into #95 or implementation-accepted |

Remote PRs and decisions can change. Recheck their heads and reviews before any subsequent integration decision; these pins identify this handoff's source snapshot.

## Existing decisions and independent review boundaries

| Evidence | Recorded decision | What remains outstanding |
| --- | --- | --- |
| [Bảo's product direction](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6386250475) | Product scope aligned: prioritize IT path; second distinct definition for Core reuse; complete Facility journey not mandatory in first MVP | Detailed business/API/security acceptance; exact Facility reuse case and evidence |
| [Ánh's A02 owner review](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6551136261) | CONDITIONAL candidate acknowledgement; four actors, current five-state Helpdesk flow, separate assignment, nine routes | Final contract ratification, OPEN decisions and Backend/Security review |
| [Bảo's BFF decision](https://github.com/lexuanbao258-collab/dxos-open-core/issues/53#issuecomment-6431061966) | Same-origin BFF with server-side OAuth/OIDC accepted for architecture/candidate development | Verified session/CSRF/real-provider composition and independent LIVE acceptance |
| [Yến's #76 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/76#pullrequestreview-5682986501) | COMMENTED / CONDITIONAL ACCEPT at documentation-structure level, on the earlier nine-file diff | Current diff re-review; not approval of this later head. #73 is now merged and #76 targets main; final dependency/diff integration still needs review |
| [Ánh's #95 source review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95#pullrequestreview-5726515815) | Nine-route source alignment; CHANGES_REQUESTED for APP-P0-01 | Generic public 404 regression, legacy flag impact, Application seam and BFF transport composition |
| [Ánh's scoped disposition](https://github.com/lexuanbao258-collab/dxos-open-core/pull/95#issuecomment-6554255746) | Conditional compatibility direction and legacy isolation for disposable local candidate only | Bảo rollout confirmation, flag regressions, actual patch re-review and security; existing CHANGES_REQUESTED remains in force |
| [Yến's #97 review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#pullrequestreview-5726696099) | ACCEPT WITH CONDITIONS for security contract/source direction only | SEC-97-01 per-operation Generic HIDDEN/CREATE/ACTIVATE/binding decision and provider/no-write tests; implementation acceptance BLOCKED |
| [Bảo's initial #97 operation gate](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6554344227) | No further implementation until explicit Ánh/Yến operation matrix | Historical gate; latest decisions below permit a minimal corrected-patch handoff, not implementation acceptance |
| Ánh's separate [CREATE](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#pullrequestreview-5727851665) / [ACTIVATE](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#pullrequestreview-5727855334) reviews | AMEND P0 for each operation; blanket HIDDEN → INSTANCE_NOT_FOUND is unacceptable | Trustworthy denial provenance and missing-definition/hidden-binding nondisclosure semantics remain unresolved |
| [Yến's later nondisclosure direction](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6557395612) and [Bảo's reconciliation](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6557521952) | Historical conditional direction; design not fully ratified / implementation BLOCKED at that point | Followed by the latest conditional design decisions below; corrected new SHA and independent evidence still required; BD-08 remains OPEN |
| [Yến's consolidated SEC-97-01 decision](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6558227330), 2026-11-01 | ACCEPT WITH CONDITIONS — DESIGN/CONTRACT ONLY | Trusted operation/denial provenance, actual authorization-path parity, no-write/transaction and filter-chain/flag evidence at a corrected exact SHA; current five-file #97 patch is not approved |
| [Ánh's latest SEC-97-01 API decision](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6558938294), 2026-11-01, following [AMEND REQUIRED](https://github.com/lexuanbao258-collab/dxos-open-core/pull/97#issuecomment-6558807578) | ACCEPT WITH CONDITIONS — API DESIGN ONLY; current #97 implementation CHANGES REQUIRED / NOT ACCEPTED | Operation-specific equivalence/parity conditions below; implementation acceptance BLOCKED, not PASS |

None of these source/design decisions supplies independent Backend, E2E or LIVE PASS at the A02 baseline. Local test counts reported in PR descriptions are not imported as official verification results here. [Existing S16 evidence mapping](../reviews/issue-53/s16-authorization-evidence-mapping.md) remains unchanged and distinguishes source coverage, reported execution and acceptance.

### SEC-97-01 — latest conditional API design boundaries

The 2026-11-01 Ánh/Yến decisions above preserve Generic `DEFINITION_NOT_FOUND` / `Workflow definition version not found` and `INSTANCE_NOT_FOUND` / `Workflow instance not found`. Trusted Application/Policy denial scope is required; handler names, URLs, headers and client payloads cannot establish it.

| Operation / trusted scope | Conditional design / remaining blocker |
| --- | --- |
| CREATE — verified definition-unavailable | Preserve 404 `DEFINITION_NOT_FOUND`; hidden/inactive exact binding and genuinely unavailable definition/version require equivalent public responses when the caller may not distinguish them. No global untyped HIDDEN mapping. |
| CREATE — RESOURCE/TENANT HIDDEN | Sanitized 404 `NOT_FOUND` is conditional on a safe missing-resource comparator in the same CREATE/caller/security context; otherwise BLOCKED. Do not classify this as definition-unavailable. |
| READ/HISTORY/TRANSITION and ACTIVATE — authoritative instance HIDDEN | Preserve 404 `INSTANCE_NOT_FOUND`, equivalent to genuinely missing instance; no state/version disclosure or denied write. RESOURCE/TENANT denial on an instance-ID operation must not create a distinguishable `NOT_FOUND` response. |
| ACTIVATE — binding/activation-policy HIDDEN | BLOCKED pending trusted provenance and approved public equivalence class. |
| UNRESOLVED/AMBIGUOUS HIDDEN | BLOCKED; no approved default 404/500/503 mapping. |
| Helpdesk detail/history and triage/assign/start/resolve/confirm | Hidden/nonexistent parity: `{"status":404,"code":"NOT_FOUND","message":"Workflow resource not found","errors":{}}`; denied mutations write nothing. |

Parity requires matching status, code, message, JSON envelope, Content-Type and serialized bytes on the actual Application authorization path; mock-only evidence is insufficient. Preserve 401 authentication, visible-forbidden 403, authorized stale-version 409 after authorization, genuine provider-failure 503, and no automatic retry of `COMMIT_OUTCOME_UNKNOWN`. A corrected published exact SHA, operation/error matrix, PostgreSQL no-write snapshots and absent/false/true filter-chain evidence still need independent API/security re-review. Keep BD-08 OPEN and Generic production HTTP disabled; this records conditional design only, not approval of current PR #97.

### Separate independent evidence — PR #98 Phase 2A

[Yến's independent security re-review](https://github.com/lexuanbao258-collab/dxos-open-core/pull/98#issuecomment-6562451308) at **`25f73d10b51556b446af12e636b9e19cd53ad030`** records **331/331 focused PASS** and **687/687 single-worker PASS** (18/18 files, 0 FAIL / 0 SKIP). The initial default-parallel run recorded **685 PASS / 2 FAIL**, both 5000 ms timeouts; later independent passes of the affected files do not resolve their root cause or erase those failures. Disposition: **ACCEPT WITH CONDITIONS — Phase 2A frontend source/test only**. This separate evidence does not execute A02 acceptance designs or approve #95/#97, merge or integration. Browser OIDC/logout, actual server session/CSRF enforcement, HTTPS/cookies, agreed composite SHA and real provider/browser E2E remain unverified; **Phase 2B/LIVE BLOCKED**.

## Original analysis provenance — #68 versus #76

The seven source documents remain at their exact [PR #68 tree](https://github.com/lexuanbao258-collab/dxos-open-core/tree/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis). They are not copied, moved, renumbered or replaced. ISP-BA-01–05 and 07 are v0.1; ISP-BA-06 is v0.4. Their organizational context, needs and value claims are hypotheses, not validated customer research. Document 06 explicitly describes a working reconstruction of the proposed rule register, not a verbatim approved source.

| Original file / ID | Source purpose and relationship to current Application docs |
| --- | --- |
| [01_Organizational_Context.md — ISP-BA-01](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/01_Organizational_Context.md) | Hypothetical setting and stakeholders; input to both requirements documents; no invented company/ROI evidence |
| [02_Problem_Statement.md — ISP-BA-02](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/02_Problem_Statement.md) | Problem/response hypotheses; current requirements describe candidate capability rather than claim measured benefits |
| [03_Stakeholder_and_User_Needs.md — ISP-BA-03](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/03_Stakeholder_and_User_Needs.md) | Proposed UN-01–UN-23 and delegated responsibilities; current actor matrices narrow the implementation candidate without granting roles automatic ACL |
| [04_ASIS_TOBE_Process_Analysis.md — ISP-BA-04](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/04_ASIS_TOBE_Process_Analysis.md) | GP-IT-01, GP-FAC-01 and exceptional paths; current use cases identify supported candidate versus open paths |
| [05_MVP_Scope_and_Golden_Paths.md — ISP-BA-05](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/05_MVP_Scope_and_Golden_Paths.md) | Broader proposed MUST/SHOULD and acceptance IDs; BD-01/02/03/06/07 retain delivery-scope differences |
| [06_Business_Rules_and_Decision_Log.md — ISP-BA-06](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/06_Business_Rules_and_Decision_Log.md) | D-01–06, OD-01–05, BR-01–072; original IDs retained; current business rules are a candidate action contract, not approval of all 72 proposed rules |
| [07_Traceability_and_Review_Report.md — ISP-BA-07](https://github.com/lexuanbao258-collab/dxos-open-core/blob/c31b6509a6e27e24c0305582a79562a480454594/docs/application/01-problem-analysis/07_Traceability_and_Review_Report.md) | Documentary traceability, BL-01–08, TG gaps and AT-MVP cases; coverage is not execution. Facility no-further-repair AT-MVP-12 / BL-02 remains blocked |

Current requirements/use cases, business rules, DTO inventory, data ownership and candidate definitions stay under `docs/03-applications/` introduced by #73. The [decision register](business-decisions.md) is the single current A02 list of proposed choices and owners. Historical proposed MUSTs are not silently deleted or converted to approved exclusions.

## Reconciliation findings and remaining contract conflicts

| Finding | Current resolution or outstanding action |
| --- | --- |
| Original IT `CLOSED` proposal versus current `CONFIRMED` endpoint | Preserve historical proposal and current five-state candidate separately. Future closure remains BD-03 OPEN; no new transition/API |
| Wrong-scope rejection MUST in original analysis versus absent route | Retained as BD-01 scope/acceptance gap. Owners must explicitly decide exclusion or future guarded contract; unsupported-route rejection is not fulfillment of the business rejection requirement |
| Requester disagreement/unresolved outcomes versus no API | BD-02 OPEN; no false resolution/confirmation or invented rework/escalation |
| Significant actor/assignment/rejection history proposed versus minimal HistoryView | Actual DTO inventory recorded in business/API contract; audit schema and field/event visibility remain BD-04 OPEN |
| Generic/Helpdesk shared 404 projection | #95 regresses Generic public codes; latest Ánh/Yến SEC-97-01 decisions conditionally accept API design only, with ACTIVATE binding/policy and ambiguous HIDDEN still BLOCKED. Current #97 patch NOT ACCEPTED; see the operation boundaries above. No mapper or wire API changed by A02 |
| Legacy namespace under Helpdesk flag | Application selected isolated disposable-local scope only; Bảo confirmation, consumer impact, exposure review and absent/false/true regression evidence remain conditions |
| #70 BFF prefix versus #71 client paths / historical #63 pin | Reviewed transport/session/CSRF mapping and exact composite consumer pins still required; no runtime hookup in A02 |
| Facility assignment `ASSIGNED` and accept/close versus IT assignment/confirm | Remain Facility proposals; no inheritance of IT endpoints, version semantics or requester confirmation |
| Full Facility journey versus limited reuse proof | Full journey DEFERRED per product direction; distinct definition proof remains a separate unaccepted gate |
| `facility/business-rules.md` missing; index previously linked mutable #78 branch | Preserve the external draft reference at #78 SHA `91bcbe63083558723b1e418ee8d0ef538ecfcdd3`, without making A02 depend on its merge. Current Facility requirements and decision register remain local review sources. Candidate API's broken local business-rules link corrected; no duplicate Facility rules file created |
| PR #73 integration lineage | #73 head `2bda552…` is an ancestor of this branch; its merge commit `0f0c3ba…` is not. Contents/structure align, but a common scaffold ancestor is not evidence this branch contains every later main change. Final integration diff needs owner review; no rebase/merge performed |
| Documentation entry points | `docs/README.md` correctly links Applications and retains legacy canonical directories; unchanged in A02. Existing #76 three-dot diff against pinned remote main is Application docs plus that prior index link. Do not migrate historical contracts/evidence |
| Missing document register | `docs/00-project/document-register.md` is absent locally; responsibility registration is a governance follow-up, not an excuse to invent approval or modify project-level docs |

## Owner review handoff

### Preservation against PR #76 HEAD

The preservation baseline is `HEAD@78727a40866c2a25b8f9b324e3fcfc6cc9610d14`, compared directly with the final working tree. Helpdesk business rules retain §§1–11.8, BR-IT-01–21, all original tables, request-field examples and review/acceptance lists. The API contract retains §§1–13, nine routes, per-operation payload descriptions, RequestView/HistoryView field lists, pagination, Portal mapping and all seven integration gates. Only local corrections to stale source attribution and the misleading approved-exclusion/authority wording replace original text; additional source, validation, visibility, version and recovery detail sits beside the original content.

The original PR #71 author-reported evidence paragraph is retained in the Helpdesk acceptance plan with its original limits. Use-case Acceptance Criteria and References keep their original section numbers. Facility requirements, use cases, tables and Acceptance Criteria remain intact; its object-storage test gate is conditional on approved attachment scope. No Backend, Frontend, Core, schema, runtime flag, historical evidence or original-analysis file is changed. These are documentation preservation checks, not executed business/security acceptance tests.

Preservation validation passed: 1,200 original nonblank lines retained in order, 144 original headings, 264 unchanged table rows (the assignment row is a local correction), four fenced examples/diagrams and nine acceptance/gate sections retained verbatim; 70 local links/anchors resolve. The eight replaced original lines are limited to the #78 reference/dependency note, broken Facility rules link, conditional Facility storage gate, Helpdesk exclusion/authority wording, inspected data-model source pin and assignment-state row. `git diff --check` passed; all eight decisions remain OPEN. Historical Git-status snapshot from the earlier correction pass: HEAD was unchanged; that pass began with 16 staged Markdown files. At that point, their staged contents were preserved and the corrections were unstaged; this is not a current Git-status assertion. Runtime, provider and E2E tests remain NOT RUN for this documentation-only pass.

**Ánh:** Confirm action/resource eligibility, information needs, wrong-scope/disagreement/exclusion handling and Facility candidate boundaries. Acknowledge the updated docs as a new review scope; the existing conditional review is not an approval of these uncommitted edits.

**Bảo:** Confirm the exact nine-route DTO/status/version/allowedActions contract against the final selected Backend SHA; confirm create grants/binding/provisioning, assignment grant lifecycle and one PostgreSQL transaction; approve a concrete unknown-commit recovery procedure and accountable operator; reconcile APP-P0-01, legacy rollout/consumer compatibility and Issue #90 Application seam through their own PRs; decide which business exclusions must be closed before MVP. Supply reviewable exact-SHA evidence separately.

**Yến:** Confirm verified actor/exact-authority mapping, tenant/resource/action authorization, no implicit administrator access, final field/event/history policy and provisioning/revocation; ratify operation-scoped 401/403/hidden-404/409/503 behavior with Ánh; review BFF session/CSRF, real OIDC and provider-backed denial/race/rollback/unknown-outcome gates at the selected SHA. Conditional design review does not establish those runtime properties.

All eight [decision rows](business-decisions.md) remain OPEN. Before unqualified contract acceptance, either resolve each required case or record an explicit, source-backed owner exclusion with its impact; preserve exact definition/API/document versions and independent verdict. Runtime and release acceptance remain separate.

**Review readiness: CONDITIONAL.** Documentation is prepared for independent review; Business Contract freeze, Backend implementation acceptance, E2E and LIVE readiness are not established. No commit, push, merge or deployment is requested by this handoff.
