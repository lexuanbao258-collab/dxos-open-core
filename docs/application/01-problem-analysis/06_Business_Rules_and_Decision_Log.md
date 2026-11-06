# DX-OS — Business Rules and Decision Log

| Metadata | Value |
|---|---|
| Document ID | ISP-BA-06 |
| File | `06_Business_Rules_and_Decision_Log.md` |
| Version | 0.4 (consolidated draft for proposed Business Baseline v1.0) |
| Status | **DRAFT — PROPOSED; NOT APPROVED** |
| Application | Internal Service Portal (ISP): IT Support + Facility Request |
| Context | Hypothetical medium-sized multi-department organization |
| Source | DX-OS Checklist 1.1–1.8.4; prior proposed D-01–D-06, OD-01–OD-05, BR-01–BR-072 |
| Evidence | No real organization interviews, observed process, measured baseline or policy confirmation supplied |
| Owner / Approver | TRICORE — review and approval pending |

> **Provenance and integrity warning:** This is a reconciled **working reconstruction** of the 72 numbered rules discussed across earlier checklists, not a verbatim transcription of an approved source register. Exact wording for some early BR entries is not available as an authoritative signed-off register. Their text is normalized here for review; each rule remains **PROPOSED** until the team checks it against the original discussions and approves it. No real-world validation is implied.

## 1. Purpose, interpretation and status legend

This document consolidates decisions, proposed business rules, delegated authority, unresolved policy questions, conflict checks, and acceptance links for Checklist 1.8.4. It does **not** specify database tables, API contracts, technical state machines, or implementation details.

- **H** — hypothesis about the organization, not evidenced.
- **P / PROPOSED** — analyst/reviewer recommendation, not approved.
- **OPEN** — a decision or acceptance condition is still unresolved.
- **DEFERRED / SHOULD** — target behavior not required for the MVP.
- **APPROVED** — only after an explicit recorded TRICORE decision; none recorded here.
- **VALIDATED** — only after suitable external evidence; none claimed here.

A business **responsibility** is not automatically a separate job title or account. One person may hold multiple responsibilities only when the relevant authority is delegated. An accepted request must remain accountable throughout its lifecycle.

## 2. Decision Log — D-01 through D-06

| ID | Selected direction (all P, option B) | MVP | Key constraint | Review status |
|---|---|---|---|---|
| D-01 | Departmental Intake Owner; delegated Assignment Authority | MUST | Intake does not imply assignment or completion rights | PROPOSED |
| D-02 | IT Technician Resolve → Requester Confirm → authorized Close | MUST | Reject/no-response never implies successful confirmation | PROPOSED |
| D-03 | Technician reports unresolved work; Intake Owner coordinates authorized next decision | MUST | No false success; reassignment or justified unsuccessful closure | PROPOSED |
| D-04 | Facility result evaluated by authorized acceptance responsibility | MUST | Failed acceptance blocks successful closure | PROPOSED |
| D-05 | Trace significant business decisions | MUST | Visibility policy remains BL-03 OPEN | PROPOSED |
| D-06 | Authorized interdepartmental transfer with receiving acceptance | SHOULD | Target policy retained; not mandatory for MVP | PROPOSED / DEFERRED |

**Scope clarification for D-06:** TRICORE previously selected option B as the intended *business policy*. OD-04 proposes deferring the full transfer flow to SHOULD. This is a scope adjustment requiring explicit team acknowledgement; it is not an approved reversal of the original selection.

## 3. Open Decision Log — OD-01 through OD-05

| ID | Proposed resolution | MVP effect | Remaining condition | Status |
|---|---|---|---|---|
| OD-01 | Departmental Intake Owner **may** hold Completion Authority when delegated | MUST | Scope and closure guards must be explicit | PROPOSED |
| OD-02 | IT non-response stays pending; Intake Owner monitors; no automatic successful closure | MUST | Exceptional manual closure policy not specified; SHOULD/OPEN | PROPOSED with OPEN subcase |
| OD-03 | Authorized Facility evaluator accepts/rejects repair outcome | MUST | No-further-repair case BL-02 remains OPEN | PROPOSED |
| OD-04 | Wrong-scope rejection with reason MUST; full transfer SHOULD | MUST / SHOULD | Team must acknowledge D-06 delivery scope | PROPOSED |
| OD-05 | Authorized `Closed — Unable to Fulfill` for IT after reason, assessment and alternatives considered | MUST | Actual company policy not validated | PROPOSED |

**No decision above is marked APPROVED.**

## 4. Responsibility and authority matrix (proposed)

| Responsibility | May initiate / perform | May decide | Explicit limitation |
|---|---|---|---|
| Requester | Submit and track own request; confirm/reject IT resolution | IT result feedback | No default assignment/closure authority |
| Departmental Intake Owner | Review intake; coordinate unresolved cases; monitor pending feedback | Intake disposition within delegation | No automatic assignment/closure right |
| Assignment Authority | Assign or reassign eligible handlers | Assignment within delegated scope | Cannot override completion conditions |
| IT Technician | Work on assigned IT request; report resolution or inability | Propose work result | Cannot independently close unsuccessful request |
| Facility Staff | Inspect/repair; record result | Propose repair outcome | Cannot self-certify acceptance without explicit delegation |
| Facility Acceptance Authority | Evaluate Facility work | Accepted / not accepted within delegation | Failed acceptance cannot be marked Fulfilled |
| Completion Authority | Evaluate completion guards | Close — Fulfilled or authorized Unable to Fulfill | Departmental scope and outcome guards apply |
| Authorized history viewer | Inspect permitted business history | None solely from visibility | **Exact who/what scope is OPEN (BL-03)** |

This matrix describes **responsibilities**, not mandatory distinct staff members. The authority to close an IT request unsuccessfully does not automatically confer a Facility-specific terminal policy.

## 5. Business Rules Register — BR-01 through BR-072

**Register convention:** Every row is `PROPOSED` unless additionally marked `OPEN` or `SHOULD`. `MUST` means proposed MVP requirement, not an approved enterprise policy. The entries below preserve all 72 IDs with no gaps. Wording is normalized to reconcile overlapping drafts; the team must review each row before baseline approval.

### Intake and assignment (D-01)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-01 | An accepted request has an identifiable departmental Intake Owner. | MUST / P |
| BR-02 | Only a person authorized for the relevant department may accept or reject intake. | MUST / P |
| BR-03 | Assignment decisions require separately delegated Assignment Authority. | MUST / P |
| BR-04 | Intake Owner and Assignment Authority may be held by the same person, but their permissions are distinct. | MUST / P |
| BR-05 | An assigned request has an identifiable responsible handler. | MUST / P |
| BR-06 | Reassignment records the decision, reason and change of responsibility. | MUST / P |

### IT resolution and closure (D-02)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-07 | A Technician recording Resolved does not itself close the request. | MUST / P |
| BR-08 | Only the assigned or otherwise authorized Technician may propose a resolution. | MUST / P |
| BR-09 | IT Requester may confirm or reject the proposed resolution. | MUST / P |
| BR-10 | A rejected resolution cannot be closed as Fulfilled. | MUST / P |
| BR-11 | Only an authorized Completion Authority may close a request. | MUST / P |
| BR-12 | No-response does not authorize automatic successful closure without an approved policy. | MUST / P |

### Unresolved IT coordination (D-03)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-13 | Technician records inability to complete and a reason. | MUST / P |
| BR-14 | An inability to complete cannot be represented as a successful resolution. | MUST / P |
| BR-15 | Intake Owner coordinates review of unresolved IT work. | MUST / P |
| BR-16 | Reassignment, escalation and unsuccessful closure require the appropriate delegated authority. | MUST / P |
| BR-17 | An unsuccessful final outcome is distinguishable from fulfilled closure. | MUST / P |

### Facility acceptance (D-04)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-18 | Facility reports include a meaningful location when needed to identify the issue. | MUST / P |
| BR-19 | Facility Staff records the work outcome before acceptance. | MUST / P |
| BR-20 | Facility acceptance is performed by an authorized evaluator. | MUST / P |
| BR-21 | Failed Facility acceptance cannot lead directly to Fulfilled closure. | MUST / P |
| BR-22 | A separate Requester Confirm step is not automatically required for Facility. | MUST / P |
| BR-23 | Procurement, inventory and multi-level spending approvals are not mandatory in MVP. | MUST / P |

### Business decision history (D-05)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-24 | Significant business decisions are traceable to an actor and request. | MUST / P |
| BR-25 | Decision history covers assignment, resolution, confirmation, rejection, acceptance, closure, failure and transfer where implemented. | MUST / P |
| BR-26 | History records the decision outcome and relevant time; a reason is recorded when required. | MUST / P |
| BR-27 | Only authorized actors may access history within an approved scope; the exact matrix is OPEN (BL-03). | OPEN dependency / P |
| BR-28 | Recording every interface click is not required. | MUST / P |

### Cross-department transfer target policy (D-06; SHOULD)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-29 | A full transfer may be initiated only by an authorized actor. | SHOULD / P |
| BR-30 | The receiving department accepts or rejects the transfer with a reason when required. | SHOULD / P |
| BR-31 | Ownership changes only when the receiving side accepts. | SHOULD / P |
| BR-32 | If transfer is rejected, the sending side remains accountable. | SHOULD / P |
| BR-33 | Transfer initiation, acceptance and rejection are traceable. | SHOULD / P |

### Completion authority and non-response (OD-01, OD-02)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-34 | Only a delegated Completion Authority may Close. | MUST / P |
| BR-35 | Completion Authority acts only within the authorized departmental scope. | MUST / P |
| BR-36 | A Technician work outcome is not itself a Close decision. | MUST / P |
| BR-37 | Close requires the applicable domain-specific completion guard. | MUST / P |
| BR-38 | Closure records decision-maker and final outcome. | MUST / P |
| BR-39 | An IT request may remain Pending Requester Confirmation after Resolve. | MUST / P |
| BR-40 | Requester silence is not confirmation. | MUST / P |
| BR-41 | Elapsed time alone cannot justify successful closure without an approved policy. | MUST / P |
| BR-42 | Intake Owner remains accountable for monitoring pending confirmations. | MUST / P |
| BR-43 | Any manual exceptional closure for non-response requires a separately approved rule; currently OPEN/SHOULD. | OPEN dependency / P |
| BR-44 | Automatic time-based closure is excluded from MVP. | MUST / P |

### Facility evaluation authority (OD-03)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-45 | Facility Staff records the repair or intervention result. | MUST / P |
| BR-46 | Authorized Facility Acceptance Authority decides accepted or not accepted. | MUST / P |
| BR-47 | Acceptance authority is restricted to the delegated scope. | MUST / P |
| BR-48 | A not-accepted result cannot be closed as Fulfilled. | MUST / P |
| BR-49 | A failed acceptance needs a next action or authorized disposition; no-further-repair outcome remains OPEN (BL-02). | OPEN dependency / P |
| BR-50 | Acceptance can satisfy the Facility-specific prerequisite for authorized Fulfilled closure. | MUST / P |
| BR-51 | Facility does not automatically require a duplicate requester confirmation. | MUST / P |

### Wrong-department intake and deferred transfer (OD-04)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-52 | Intake Owner checks whether a request is in departmental scope. | MUST / P |
| BR-53 | Wrong-scope intake is rejected or returned with an appropriate direction, not silently lost. | MUST / P |
| BR-54 | Wrong-scope rejection records a reason. | MUST / P |
| BR-55 | Full cross-department transfer is not mandatory in MVP. | MUST scope boundary / P |
| BR-56 | If full transfer is implemented, initiation requires authorization. | SHOULD / P |
| BR-57 | If full transfer is implemented, responsibility transfers only after receiving-side acceptance. | SHOULD / P |
| BR-58 | Wrong-scope rejection and any implemented transfer decision are traceable. | SHOULD / P |

### IT unable-to-fulfill policy (OD-05)

| Rule | Proposed business rule | Scope / qualification |
|---|---|---|
| BR-59 | A Technician cannot independently Close an unsuccessful request merely by reporting failure. | MUST / P |
| BR-60 | Technician records the reason and relevant work or assessment. | MUST / P |
| BR-61 | Intake Owner reviews whether further handling is viable. | MUST / P |
| BR-62 | Only delegated Assignment Authority may reassign. | MUST / P |
| BR-63 | Reassignment requires a reason and a credible new handling purpose. | MUST / P |
| BR-64 | Escalated is not equivalent to Resolved. | MUST / P |
| BR-65 | Only delegated Completion Authority may decide Closed — Unable to Fulfill. | MUST / P |
| BR-66 | Unsuccessful closure requires a documented rationale and supporting assessment. | MUST / P |
| BR-67 | Viable further options must be considered before unsuccessful closure. | MUST / P |
| BR-68 | Closed — Unable to Fulfill and Closed — Fulfilled are distinct business outcomes. | MUST / P |
| BR-69 | Requester can learn the unsuccessful outcome and a shareable explanation. | MUST / P |
| BR-70 | Unsuccessful closure decision is traceable. | MUST / P |
| BR-71 | Requester feedback on unsuccessful closure is SHOULD, not a mandatory appeal workflow. | SHOULD / P |
| BR-72 | Multi-tier escalation and automated loop detection are not mandatory in MVP. | SHOULD / P |

## 6. Consolidated lifecycle guards (business meaning only)

| Outcome | Required business condition | Explicitly prohibited |
|---|---|---|
| IT `Resolved` | Authorized Technician proposes a documented result | Treat as final successful closure |
| IT `Pending Requester Confirmation` | Resolution proposed; feedback pending | Interpret silence as confirmation |
| IT `Closed — Fulfilled` | Requester confirmed acceptable result and authorized Completion Authority closed | Technician self-close; close after rejection |
| IT `Unresolved / Escalated` | Inability and reason documented; accountable Intake Owner; next handling decision | Mark Escalated as Resolved; responsibility-free handoff |
| IT `Closed — Unable to Fulfill` | Legitimate request, documented inability, relevant work/assessment, viable alternatives considered, authorized closer, reason and requester-facing outcome | Close solely because work is difficult, requester silent or request was wrong-scope at intake |
| Facility `Accepted` | Authorized Facility evaluator accepts recorded work result | Imply acceptance merely because staff recorded repair |
| Facility `Closed — Fulfilled` | Accepted Facility result and authorized Close | Close after failed acceptance |
| Facility cannot be repaired further | **OPEN BL-02** — no approved terminal policy, authority, evidence or notification | Invent IT failure policy as Facility policy |
| Wrong-scope intake | Authorized rejection/return with reason | Mislabel as work attempted and failed |

## 7. Cross-rule consistency review

| Conflict ID | Affected decisions / rules | Risk | Reconciliation in this draft | Remaining status |
|---|---|---|---|---|
| X-01 | D-01, D-03, OD-01; BR-02–06, BR-34–38, BR-61–65 | Intake Owner implicitly holds all powers | Distinct delegated intake, assignment, completion authority | P; team to approve |
| X-02 | D-02, D-04, OD-03; BR-09, BR-20–22, BR-46–51 | Duplicate IT requester confirmation and Facility acceptance | IT uses requester feedback; Facility uses authorized evaluator; no mandatory duplicate step | P |
| X-03 | D-03, OD-05; BR-13–17, BR-59–72 | Infinite reassignment or false success | Every reassignment needs reason/new purpose; viable alternatives reviewed; justified unsuccessful outcome | P; no arbitrary reassignment limit |
| X-04 | D-06, OD-04; BR-29–33, BR-52–58 | Ownerless cross-department transfer | Receiving acceptance precedes ownership change; full transfer SHOULD | P / DEFERRED |
| X-05 | D-05; BR-24–28, BR-70 | Missing significant decision events | Include failure, acceptance, rejection, closure and transfer if implemented | P; access BL-03 OPEN |
| X-06 | OD-02 vs OD-05; BR-39–44, BR-65–69 | Non-response misclassified as Unable to Fulfill | Silence remains pending; no automatic closure | P; exceptional disposition OPEN |
| X-07 | D-04, OD-03 vs OD-05; BR-49, BR-65–68 | Apply IT failure closure to Facility without policy | Keep Facility no-further-repair outcome explicitly OPEN | **BL-02 OPEN** |
| X-08 | BR-06 vs BR-63 and scope SHOULD | Routine reassignment potentially omitted despite unresolved IT MUST | Unresolved-case reassignment is MUST when selected as next action; optional general reassignment enhancements SHOULD | P; clarify in Scope doc |
| X-09 | BR-27, M-01, M-12 | Sensitive history visible across departments | Limit visibility until an approved role/scope matrix is set | **BL-03 OPEN** |

**Reviewer finding:** This document has **no knowingly unacknowledged direct contradiction** among the proposed statements after the above interpretations, but it **does not establish conflict-free approval**. X-07 and X-09 are unresolved, and source wording still requires team reconciliation. Avoid claiming that all business rules are finalized.

## 8. Open Policy and Blocker Register

| ID | Priority | Open question / blocker | Required decision or evidence | Owner / status |
|---|---|---|---|---|
| BL-01 | P0 baseline | Team has not approved baseline | Explicit recorded TRICORE review/decision | TRICORE / OPEN |
| BL-02 | P0 | Facility repair cannot proceed / no viable repair remains | Decide permissible terminal result, decision authority, supporting reason, requester notice and rework/stop condition; or approve bounded exclusion | TRICORE / OPEN |
| BL-03 | P0 for history acceptance | Who can view which business history? | Actor × own/assigned/departmental/other-department scope; confidentiality and exception policy | TRICORE / OPEN |
| BL-04 | P0 baseline | BR-01–BR-072 lacked a reconciled register | **This draft supplies complete ID coverage; still requires original-source comparison, per-rule approval and conflict sign-off** | PARTIALLY ADDRESSED |
| BL-05 | P1 evidence | Organizational need not validated | Interview/observation or continue explicit hypothetical framing | OPEN |
| BL-06 | P1 evidence | No observed performance baseline | Define metrics and evidence collection; no invented results | OPEN |
| BL-07 | P1 Core proof | Core reuse not demonstrated | Independent reuse acceptance and evidence | OPEN |
| BL-08 | P1 governance | Change control not formally agreed | Approve how baseline revisions are proposed, reviewed and versioned | OPEN |
| OP-01 | SHOULD | Requester non-response exceptional manual closure | Explicit grounds, authority, trigger and notice if brought into MVP | OPEN / DEFERRED |
| OP-02 | SHOULD | Full interdepartmental transfer | Confirm target design vs MVP delivery scope | OPEN acknowledgment |

### BL-03 — permission matrix requiring a decision (do not treat as approved)

| History category | Requester (own) | Assigned handler | Departmental Intake Owner | Other department |
|---|---|---|---|---|
| Public progress and shareable outcome | Candidate allow | Candidate allow | Candidate allow | Candidate deny |
| Assignment / status decisions | Candidate limited | Candidate limited | Candidate allow | Candidate deny |
| Internal failure assessment / private rationale | **OPEN** | **OPEN** | **OPEN** | Candidate deny |
| Cross-department transfer history (SHOULD) | **OPEN** | **OPEN** | **OPEN** | **OPEN** |

Candidate permissions are **illustrative alternatives only**; they are not BR approvals. In particular, sharing an explanation with Requester does not mean sharing all internal notes.

## 9. Business acceptance scenarios (not executed)

| Test ID | Given / When | Then | Rule links | Status |
|---|---|---|---|---|
| AT-BR-01 | Authorized Requester submits valid IT request | Request is accepted/trackable with accountable intake | BR-01–03 | P |
| AT-BR-02 | Unauthorized actor tries to assign | Assignment is refused | BR-03–04 | P |
| AT-BR-03 | Technician proposes successful IT resolution | Request remains distinct from Closed | BR-07–11 | P |
| AT-BR-04 | IT Requester rejects resolution | Fulfilled closure is blocked | BR-09–10 | P |
| AT-BR-05 | IT Requester does not respond | Request stays pending; no time-based automatic close | BR-12, BR-39–44 | P |
| AT-BR-06 | Technician reports inability with reason | Intake Owner reviews alternatives, Technician cannot self-close | BR-13–17, BR-59–61 | P |
| AT-BR-07 | New viable handler exists | Authorized reassignment includes reason and new purpose | BR-62–64 | P |
| AT-BR-08 | No viable IT option, guards met | Authorized unsuccessful closure and requester-facing outcome | BR-65–70 | P |
| AT-BR-09 | Wrong-department request at intake | Reject/return with reason, not false unsuccessful closure | BR-52–55 | P |
| AT-BR-10 | Facility repair accepted by authorized evaluator | Authorized successful closure may follow | BR-45–50 | P |
| AT-BR-11 | Facility repair rejected | Fulfilled closure is blocked; rework/next decision needed | BR-48–49 | P |
| AT-BR-12 | Facility cannot be repaired further | Terminal result and authority must be specified | BR-49 | **BLOCKED BL-02** |
| AT-BR-13 | Unauthorized actor requests internal decision history | Visibility must be limited per approved matrix | BR-27 | **BLOCKED BL-03** |
| AT-BR-14 | Full transfer implemented (SHOULD) | Sender accountable until receiver accepts; decisions traced | BR-29–33, BR-56–58 | DEFERRED |
| AT-BR-15 | Request is closed | Authorized closer, result and significant decision are traceable | BR-24–28, BR-34–38 | P / BL-03 visibility open |

**Acceptance is not test execution:** No tests or stakeholder validation are claimed to have passed.

## 10. Application vs Core boundary

| Concern | Application owns | Core may provide reusable capability | Proof still needed |
|---|---|---|---|
| IT success and requester confirmation | Business meaning and conditions | Generic workflow/authorization primitives | Demonstrate not hard-coded to IT |
| Facility acceptance | Facility-specific evaluation and rework policy | Reusable process/decision capabilities | Show different domain conditions without Core-specific changes |
| Unable to Fulfill | IT-specific disposition grounds | Generic decision tracking and access enforcement | Separate domain rule from shared capability |
| Assignment | Which department/person is eligible | Generic identity/authorization or assignment support if in approved Core scope | Contract/boundary tests |
| Audit/history | What events matter, what users may see | Shared audit recording if Core scope supports it | Policy enforcement and reuse tests |

Two request types or visually similar flows alone **do not prove** reusable Core capabilities.

## 11. Change control and approval record

Before baseline freeze, TRICORE must: (1) reconcile all 72 entries with the earlier source drafts; (2) resolve or explicitly bound BL-02; (3) approve BL-03 visibility; (4) acknowledge D-06 / OD-04 MUST–SHOULD distinction; (5) verify traceability with `04_ASIS_TOBE_Process_Analysis.md` and `05_MVP_Scope_and_Golden_Paths.md`; (6) record the team decision and date. Do not invent names/signatures.

| Review field | Value |
|---|---|
| Prepared for | TRICORE internal review |
| Reviewers | To be assigned/confirmed |
| Review date | Not recorded |
| Approval decision | **NOT APPROVED** |
| Approved baseline version | None |
| Known unresolved conditions | BL-01, BL-02, BL-03, BL-04 review; BL-05–BL-08; OP-01, OP-02 |

### Acceptance gate

- [ ] All D-01–D-06 and OD-01–OD-05 reviewed with explicit team disposition.
- [x] BR-01–BR-072 identifiers present without gaps in this **draft**.
- [ ] All BR wordings checked against original analysis and approved.
- [ ] BL-02 Facility no-further-repair policy resolved or explicitly scoped out.
- [ ] BL-03 actor × history visibility matrix decided.
- [ ] MUST / SHOULD boundaries reflected consistently in other documents.
- [ ] Every MUST exception has a deterministic business result or a recorded approved limitation.
- [ ] Acceptance cases linked to rules and process steps.
- [ ] Evidence limitations and hypothetical assumptions retained.
- [ ] TRICORE approval recorded.

## 12. Related documents

- [`01_Organizational_Context.md`](01_Organizational_Context.md)
- [`02_Problem_Statement.md`](02_Problem_Statement.md)
- [`03_Stakeholder_and_User_Needs.md`](03_Stakeholder_and_User_Needs.md)
- [`04_ASIS_TOBE_Process_Analysis.md`](04_ASIS_TOBE_Process_Analysis.md)
- [`05_MVP_Scope_and_Golden_Paths.md`](05_MVP_Scope_and_Golden_Paths.md)
- `07_Traceability_and_Review_Report.md` — planned, not yet included in this package.

---

**Reviewer verdict:** **CONDITIONAL ACCEPT AS DRAFT; NO-GO FOR APPROVED BASELINE.** Full numeric BR coverage is now documented, but consistency review, unresolved Facility terminal policy, history visibility and formal team approval remain outstanding.
