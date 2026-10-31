# 04 — AS-IS / TO-BE Process Analysis

| Metadata | Value |
|---|---|
| Project | DX-OS Open-Core / TRICORE |
| Application | Internal Service Portal (ISP) |
| Document ID | ISP-BA-04 |
| Version | 0.1 |
| Status | **DRAFT — PROPOSED; NOT APPROVED** |
| Organizational context | Hypothetical medium-sized, multi-department enterprise |
| Primary process | IT Support |
| Secondary process | Facility Request |
| Source | TRICORE Checklist 1.1–1.8.4 discussions; proposed Decision Log v0.3 and OD-05 |
| Evidence | No verified interviews, observations, process logs, policies, or baseline measurements |

> **Evidence convention:** `H` = unvalidated organizational hypothesis; `P` = proposed future business rule/process; `O` = open decision; `V` = validated with evidence. Nothing in this document is marked `V`. A proposed process is not a description of an operating company.

## 1. Purpose and boundaries

This document distinguishes hypothetical current-state processes (**AS-IS**) from proposed future-state processes (**TO-BE**) for two internal service domains. It records actors, triggers, inputs, outputs, decision points, exceptions, and business ownership. It does **not** prescribe a database, API, software component, implementation workflow engine, or actual enterprise policy.

**Business objective (P):** Enable requests to be received, assigned, processed, tracked, and concluded with explicit accountability. **Evaluation objective (P):** Use IT Support and Facility Request to examine whether DX-OS can supply shared capabilities without absorbing domain-specific business decisions. Actual reuse remains unproven.

## 2. Actor and authority glossary

| Actor / responsibility | Proposed accountability | Constraint |
|---|---|---|
| Requester | Submit request, supply information, track outcome; confirm IT result | May see only authorized requests/history |
| IT Intake Owner | Receive and coordinate IT requests and exceptions | Intake authority does not automatically grant assignment or closure |
| Facility Intake Owner | Receive and coordinate Facility requests and exceptions | Same separation of authority |
| Assignment Authority | Assign/reassign staff within authorized scope | May be performed by an authorized Intake Owner |
| IT Technician | Diagnose, perform work, record resolution or inability to fulfill | Cannot unilaterally close unsuccessful work |
| Facility Staff | Inspect/repair and record result | Does not automatically have acceptance authority |
| Facility Acceptance Authority | Decide whether Facility result is acceptable | Authorized within Facility scope; independence requirements not validated |
| Completion Authority | Close a request when domain-specific conditions are met | May be an authorized Intake Owner; not an automatic right |
| Process Owner / Manager (optional) | Clarify policy and resolve exceptional business decisions | Not a mandatory approval step in MVP |
| Technical Administrator | Maintain authorized access and technical administration | Not automatically entitled to business decisions |

**Role versus person:** One person may hold multiple authorized responsibilities; the process keeps the responsibilities logically distinct.

## 3. AS-IS IT Support — organizational hypothesis (H)

**Trigger (H):** An employee encounters an IT issue or needs internal technical assistance.

**Potential channels (H):** Chat, email, verbal request, spreadsheets, or ad hoc contacts. These are *examples to validate*, not observed facts.

**Inputs (H):** Requester identity/contact, issue description, affected service/device, perceived urgency, and any supporting details.

**Expected output (H):** A response or attempted resolution, with possibly incomplete documentation of ownership and final outcome.

| Step | Actor (hypothetical) | Activity | Input | Output / decision |
|---|---|---|---|---|
| ASIT-01 | Employee | Reports an IT issue through an available channel | Issue description | Informal request |
| ASIT-02 | Available IT contact | Receives or forwards information | Informal request | Acknowledged or missed request |
| ASIT-03 | IT contact / coordinator | Clarifies incomplete details | Request details | Updated information or waiting |
| ASIT-04 | IT contact / lead | Determines who should handle it | Workload, expertise (unknown) | Informal assignment |
| ASIT-05 | IT staff | Diagnoses and attempts to resolve | Issue, access to resources | Result or inability to resolve |
| ASIT-06 | IT staff / employee | Communicates outcome | Work result | Feedback or no response |
| ASIT-07 | IT contact | Treats the case as finished or leaves it open | Informal feedback | Potentially unclear final status |

**Hypothesized decision points:** Is information sufficient? Is the issue IT's responsibility? Is there an available technician? Was the issue solved? Did the employee accept the outcome?

**Hypothesized exceptions:** Missing details; wrong department; unavailable technician; repeated handoffs; unresolved issue; requester does not respond; lost history. These are investigation hypotheses, not measured failure frequencies.

## 4. AS-IS Facility Request — organizational hypothesis (H)

**Trigger (H):** An employee notices a facility defect or maintenance need at a location.

**Inputs (H):** Description, location, contact, optional photo, safety concern (if any).

**Expected output (H):** Inspection, repair attempt, or explanation of why service cannot proceed.

| Step | Actor (hypothetical) | Activity | Input | Output / decision |
|---|---|---|---|---|
| ASFAC-01 | Employee | Reports a facility problem | Problem, location | Informal facility request |
| ASFAC-02 | Facility contact | Receives and checks report | Report | Acknowledgement or clarification |
| ASFAC-03 | Facility contact | Considers scope and safety | Location, description | Normal handling or external safety response |
| ASFAC-04 | Facility lead/contact | Assigns repair/inspection | Request, staff availability | Staff assignment |
| ASFAC-05 | Facility Staff | Inspects and attempts work | Issue and site details | Repair outcome / unresolved issue |
| ASFAC-06 | Facility contact / affected user | Checks result informally | Repair outcome | Accepted, disputed, or not assessed |
| ASFAC-07 | Facility contact | Concludes or continues handling | Assessment | Potentially unclear completion |

**Hypothesized decision points:** Is location adequate? Is the issue within Facility scope? Is there a safety hazard? Is repair feasible? Is the outcome acceptable?

**Hypothesized exceptions:** Wrong location, insufficient detail, hazardous condition, missing resources, failed repair, repeated rework, requester unavailable, unclear acceptance responsibility.

**Safety boundary:** ISP is **not** designated as an emergency response channel. The organization's real safety escalation policy has not been supplied; the prototype must not imply that filing a portal request substitutes for emergency response.

## 5. AS-IS problems to validate, not asserted findings

| Hypothesis | Possible organizational consequence | Evidence needed |
|---|---|---|
| H-P01 Fragmented intake | Missed or duplicate requests | Interviews, intake artifacts, observation |
| H-P02 Unclear ownership | Delays, handoff confusion | Role interviews, request examples |
| H-P03 Inconsistent progress visibility | Repeated follow-up inquiries | User interviews, request histories |
| H-P04 Weak closure evidence | Disputes over whether work was completed | Closure examples, policy review |
| H-P05 Different IT and Facility completion criteria | A single generic closure rule may be unsuitable | Process-owner interviews, sample cases |
| H-P06 Repeated cross-domain mechanics | Potential, not proven, reusable capabilities | Domain comparison and independent reuse tests |

No occurrence counts, savings, SLA values, or ROI are claimed.

## 6. TO-BE common principles (P)

1. An accepted request has an identifiable accountable intake owner.
2. Assignment, acceptance, and completion are distinct **authorities**, even if held by the same person.
3. A request has an observable outcome; `Resolved` is not equivalent to `Closed`.
4. Unsuccessful completion is distinguished from successful fulfillment.
5. Important business decisions and ownership changes are traceable, with restricted visibility.
6. Domain-specific acceptance remains in the Application; shared mechanics are candidates for Core reuse, not yet evidence of reuse.
7. The MVP uses explicit manual decisions rather than inventing auto-close deadlines or complex escalation levels.

## 7. TO-BE IT Support — GP-IT-01 (P)

**Trigger:** An authorized Requester submits an IT Support request.

**Minimum business inputs (proposed):** Requester identity, description of issue, enough context to identify the affected service/problem, and contact/response context. Exact mandatory fields remain to be specified in requirements.

**Successful output:** `Closed — Fulfilled` with an accepted result and an authorized closure decision.

| Step | Actor | Activity | Input | Output / decision |
|---|---|---|---|---|
| IT-01 | Requester | Submit IT request | Issue and requester context | Submitted request |
| IT-02 | IT Intake Owner | Check completeness and IT scope; accept for handling | Submitted request | Accepted, clarification needed, or rejected with reason |
| IT-03 | Assignment Authority | Assign authorized Technician | Accepted request, assignment context | Responsible Technician recorded |
| IT-04 | IT Technician | Diagnose and perform work | Request, authorized context | Work outcome |
| IT-05 | IT Technician | Record resolution and outcome | Work outcome | `Resolved`, pending Requester confirmation |
| IT-06 | Requester | Confirm result is satisfactory | Resolution summary | Confirmed or rejected result |
| IT-07 | Completion Authority | Verify conditions and close | Confirmed outcome, authorization | `Closed — Fulfilled` with decision record |

### 7.1 IT decision points

| Decision | Branch | Expected business result |
|---|---|---|
| IT-D01 Information complete? | No | Request clarification; do not process as complete |
| IT-D02 Within IT scope? | No | Reject/return with reason; full interdepartment transfer is SHOULD |
| IT-D03 Authorized assignment? | No | Deny assignment |
| IT-D04 Technician able to resolve? | No | Invoke EX-IT-UNRESOLVED; never mark successful Resolve |
| IT-D05 Requester confirms? | Reject | Do not close fulfilled; return for assessment/rework |
| IT-D05 Requester confirms? | No response | Remain pending confirmation with Intake Owner accountability; no invented automatic timeout |
| IT-D06 Authorized closure and conditions satisfied? | No | Do not close |

### 7.2 IT unresolved path — EX-IT-UNRESOLVED (P)

**Trigger:** Technician cannot complete the accepted IT request.

**Input:** Reason for inability, work performed/assessed, remaining constraints, potential next steps.

1. Technician records inability and supporting rationale; cannot self-close unsuccessful work merely because it is difficult.
2. IT Intake Owner reviews whether a **concrete feasible next action** exists.
3. If a justified alternative exists, authorized Assignment Authority reassigns or escalates with a reason and identified accountable party.
4. If no feasible in-scope option remains and closure criteria are documented, Completion Authority may decide `Closed — Unable to Fulfill`.
5. The outcome, reason that may be shared, responsible decision-maker, and any next guidance are made available to Requester; decision is traceable.

**Anti-loop rule:** A reassignment needs a distinct rationale and credible purpose; moving a request between the same people without new grounds is not a valid resolution. No arbitrary reassignment-count threshold is assumed.

**Boundary:** No response to an otherwise resolved IT request is **not** a valid reason for `Unable to Fulfill`. A request rejected as out-of-scope before acceptance is likewise not an unsuccessful fulfillment.

### 7.3 IT no-response exception — EX-IT-NORESPONSE (P / O)

If Requester has not confirmed the proposed resolution, the request stays awaiting confirmation and remains accountable to IT Intake Owner. It is not silently marked successful. **Open:** A final business policy for prolonged non-response (manual review trigger and permitted final disposition) is not validated or approved. For MVP, automatic closure is excluded; no finite closure outcome is falsely asserted for this branch.

## 8. TO-BE Facility Request — GP-FAC-01 (P)

**Trigger:** Authorized Requester reports a facility issue.

**Minimum business inputs (proposed):** Requester, problem description, sufficiently precise location, and information needed to identify any obvious safety concern. Mandatory fields and safety criteria require further agreement.

**Successful output:** `Closed — Fulfilled` after authorized Facility acceptance and authorized closure.

| Step | Actor | Activity | Input | Output / decision |
|---|---|---|---|---|
| FAC-01 | Requester | Submit facility issue and location | Issue, location | Submitted Facility request |
| FAC-02 | Facility Intake Owner | Check information, scope, safety considerations | Submitted request | Accepted, clarification needed, rejected, or safety guidance |
| FAC-03 | Assignment Authority | Assign Facility Staff | Accepted request | Responsible staff member |
| FAC-04 | Facility Staff | Inspect/repair and record outcome | Issue and site information | Work result |
| FAC-05 | Facility Acceptance Authority | Evaluate whether repair outcome is acceptable | Work result and assessment context | Accepted or rejected outcome |
| FAC-06 | Completion Authority | Close if acceptance and authority requirements hold | Accepted outcome | `Closed — Fulfilled` |

### 8.1 Facility decision points

| Decision | Branch | Expected business result |
|---|---|---|
| FAC-D01 Location/details sufficient? | No | Clarification requested |
| FAC-D02 In Facility scope? | No | Reject/return with reason |
| FAC-D03 Immediate safety concern? | Yes | Direct to applicable safety procedure outside ISP; actual policy unknown |
| FAC-D04 Authorized assignment? | No | Deny assignment |
| FAC-D05 Repair acceptable? | No | Record failure; rework/alternative review; no successful closure |
| FAC-D06 Closure authority and acceptance present? | No | Do not close fulfilled |

### 8.2 Facility failed repair — EX-FAC-FAILED (P / O)

Facility Staff records unsuccessful work and reasons. Facility Acceptance Authority records **not accepted**. Facility Intake Owner coordinates rework or a justified next step. The request must **not** be marked `Closed — Fulfilled` merely because work was attempted.

**Open blocker BL-02:** If no further repair is feasible, the authorized terminal disposition, evidence requirements, and notification obligations are **not yet agreed**. The IT `Closed — Unable to Fulfill` policy must not be silently copied into Facility. This exception remains open for business decision before Facility failure acceptance tests can be finalized.

### 8.3 Domain distinction

IT's normal successful outcome requires **Requester confirmation**. Facility's normal successful outcome requires **Facility Acceptance Authority assessment**. Facility does not automatically add a second mandatory Requester confirmation. These distinct business rules are a proposed domain differentiation, not proof of Core reuse.

## 9. Consolidated exception catalogue

| ID | Domain | Trigger | Authorized handling / outcome | MVP |
|---|---|---|---|---|
| EX-01 | Both | Unauthorized request/history access | Deny; access rules still require detailed matrix | MUST |
| EX-02 | Both | Unauthorized assignment or closure | Reject business action | MUST |
| EX-03 | Both | Missing minimum information | Seek clarification; no invalid acceptance | MUST |
| EX-04 | IT | Technician unable to fulfill | Reasoned reassignment or authorized unsuccessful close | MUST |
| EX-05 | IT | Requester rejects resolution | Not closed fulfilled; review/rework | MUST |
| EX-06 | IT | Requester does not respond | Remain pending with owner; no automatic close | MUST |
| EX-07 | Facility | Acceptance fails | Not closed fulfilled; rework/next decision; final failure policy open | MUST |
| EX-08 | Both | Wrong department / out of scope | Reject or return with reason | MUST |
| EX-09 | Both | Interdepartment transfer | Sender retains accountability until receiver accepts | SHOULD |
| EX-10 | Both | Duplicate request | Optional future policy; no fabricated merge rule | COULD |
| EX-11 | Facility | Potential safety hazard | Refer to applicable safety channel; do not claim emergency response | MUST boundary |

## 10. Cross-department transfer — deferred target process (SHOULD)

If implemented: an authorized sender proposes transfer with reason; receiving Intake Owner accepts or rejects; ownership changes **only after acceptance**; a rejected transfer leaves sender accountable; all decisions are traceable. This does not replace MVP's MUST out-of-scope rejection rule. Do not present transfer as already part of the minimum Golden Paths.

## 11. Proposed decision traceability

| Decision | Process coverage | Status |
|---|---|---|
| D-01 Departmental intake and authorized assignment | IT-02/03; FAC-02/03 | Proposed |
| D-02 IT Resolve → Requester Confirm → Close | IT-05/06/07 | Proposed |
| D-03 Unresolved IT handling | EX-IT-UNRESOLVED | Proposed |
| D-04 Facility acceptance | FAC-05; EX-FAC-FAILED | Proposed |
| D-05 Trace important business decisions | Both, including rejected decisions | Proposed |
| D-06 Controlled cross-department transfer | Section 10, SHOULD | Proposed |
| OD-01 Authorized Completion Authority | IT-07; FAC-06 | Proposed |
| OD-02 No-response not auto-close | EX-IT-NORESPONSE | Proposed; final exception disposition open |
| OD-03 Facility Acceptance Authority | FAC-05 | Proposed |
| OD-04 Wrong-scope rejection MUST, full transfer SHOULD | EX-08/09 | Proposed |
| OD-05 Authorized unsuccessful IT closure | EX-IT-UNRESOLVED | Proposed |

## 12. Business rule references

This process document **references**, but does not reissue or silently amend, the draft Business Rules Register BR-01–BR-072 described in Checklist 1.8.1–1.8.3. Particularly relevant groups:

- **Intake/assignment:** BR-01–BR-06; BR-034–BR-038 for completion authority.
- **IT resolution/confirmation:** BR-07–BR-12; BR-039–BR-044 for no response.
- **IT unsuccessful handling:** BR-13–BR-17; BR-059–BR-072.
- **Facility acceptance:** BR-18–BR-23; BR-045–BR-051.
- **Decision history:** BR-24–BR-28.
- **Wrong-scope/transfer:** BR-29–BR-33; BR-052–BR-058.

**Register integrity blocker BL-04:** The complete rule-by-rule consolidated register still requires verification. Number ranges are cross-references, not proof of a reconciled approved ruleset.

## 13. Acceptance scenarios (proposed; not executed)

| ID | Given / When | Then |
|---|---|---|
| AT-P01 | Valid IT request submitted | Request is available for authorized intake and tracking |
| AT-P02 | IT request accepted and authorized assignment occurs | Accountable Technician is identified |
| AT-P03 | Technician records resolution; Requester confirms; authorized closer acts | `Closed — Fulfilled` with decision history |
| AT-P04 | Requester rejects proposed IT resolution | No successful close; further review required |
| AT-P05 | Requester has not responded | No automatic confirmation or successful close |
| AT-P06 | Technician cannot fulfill and provides reason | Intake Owner reviews feasible alternatives |
| AT-P07 | No feasible in-scope alternative; authorized closer has documented grounds | `Closed — Unable to Fulfill`; Requester can see conclusion |
| AT-P08 | Unauthorized actor attempts assignment/close | Action is denied |
| AT-P09 | Valid Facility request with location is accepted and handled | Facility result available for acceptance decision |
| AT-P10 | Facility evaluator accepts and authorized closer acts | `Closed — Fulfilled` |
| AT-P11 | Facility evaluator rejects result | No successful close; rework/next review required |
| AT-P12 | Wrong-department request arrives | Reasoned rejection/return without requiring full transfer |
| AT-P13 | Business decision is reviewed | Authorized viewer can trace actor, action, result and rationale |
| AT-P14 | Facility work cannot proceed and no alternative is feasible | **BLOCKED:** terminal business disposition not yet agreed |
| AT-P15 | Requester asks to see another person's request history | **BLOCKED:** role-based visibility detail not yet agreed |

## 14. Core / Application boundary

| Concern | Application-specific business decision | Candidate shared Core capability (not yet proven) |
|---|---|---|
| Identity and authority | Who may assign, accept, confirm, close within IT/Facility | Provider-independent identity/authorization primitives |
| Request lifecycle | IT confirmation vs Facility acceptance; unsuccessful completion policy | Common request/workflow coordination primitives |
| Decision history | Which events matter and who may see them | Shared audit-recording mechanism |
| Data | Domain-required issue/location/repair details | General data or storage contracts where justified |

**Reuse evidence still required:** Demonstrate two genuinely distinct end-to-end domain paths and verify that a shared capability is consumed without embedding IT- or Facility-specific policy in that capability. Merely showing two request types or shared screens is insufficient.

## 15. Assumptions, evidence and open blockers

| ID | Classification | Statement / required follow-up |
|---|---|---|
| A-01 | H | A medium-sized multi-department organization is the reference setting; no named organization validated |
| A-02 | H | IT and Facility requests may currently use fragmented channels; verify before claiming |
| A-03 | P | Departmental Intake Owner and scoped authorities are suitable for MVP |
| A-04 | P | IT requester confirmation and Facility acceptance are distinct completion conditions |
| A-05 | P | Manual decision paths suffice without automatic timeout or multi-level escalation |
| O-01 | O | Long-term IT non-response final policy, if required beyond pending state |
| BL-02 | O / P0 | Facility impossible-to-complete final outcome and authorization |
| BL-03 | O / P0 for access specification | Who can view which request and audit details |
| BL-04 | O / P0 for baseline approval | Reconcile and review full BR-01–BR-072 register |
| E-01 | Evidence gap | No interviews, workflow observations, or enterprise policy supplied |
| E-02 | Evidence gap | No measured response time, completion rate, rework rate, or ROI |
| E-03 | Evidence gap | No executed acceptance or Core reuse tests |

## 16. Document acceptance checklist

- [x] Two AS-IS processes explicitly marked as hypotheses
- [x] Two proposed TO-BE processes with actors, triggers, inputs, outputs
- [x] Named decision points and normal/exception branches
- [x] IT unresolved and no-response cases distinguished
- [x] Facility acceptance and failed repair distinguished
- [x] MUST out-of-scope rejection separated from SHOULD transfer
- [x] Core/Application responsibilities distinguished at business level
- [x] Assumptions and unresolved decisions disclosed
- [ ] BL-02 Facility terminal failure policy resolved
- [ ] BL-03 Access/history visibility policy resolved
- [ ] BL-04 Consolidated Business Rules Register reviewed
- [ ] TRICORE review and approval recorded

**Reviewer verdict:** **CONDITIONAL ACCEPT as a draft; NOT FROZEN.** Suitable for internal review and for deriving draft use cases, not for claims of validated organizational needs or approved policies.

## 17. Related documents and approval

- `01_Organizational_Context.md` — hypothetical organizational context.
- `02_Problem_Statement.md` — problems, alternative solutions, and hypothesis/evidence distinction.
- `03_Stakeholder_and_User_Needs.md` — stakeholder and user-needs register.
- `05_MVP_Scope_and_Golden_Paths.md` — planned scope and minimum demonstrations.
- `06_Business_Rules_and_Decision_Log.md` — authoritative proposed rules and decisions once reconciled.
- `07_Traceability_and_Review_Report.md` — cross-document traceability and review blockers.

| Review item | Status |
|---|---|
| Prepared for TRICORE review | Yes — draft |
| Reviewed by TRICORE | Not recorded |
| Approved by TRICORE | **No** |
| Validated with real enterprise | **No** |
| Baseline frozen | **No** |

