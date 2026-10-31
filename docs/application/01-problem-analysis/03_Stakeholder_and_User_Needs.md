# DX-OS — Stakeholder and User Needs

> **Document ID:** ISP-BA-03  
> **Version:** 0.1 — consolidated draft for Checklist 1  
> **Status:** DRAFT — PROPOSED; not approved or externally validated  
> **Project:** DX-OS Open-Core — TRICORE / OLP PMNM 2026  
> **Application:** Internal Service Portal (ISP)  
> **Scope:** IT Support (primary Golden Path) and Facility Request (secondary reuse scenario)  
> **Owner:** TRICORE (proposed; document owner not assigned)  
> **Last updated:** 2026-10-21  
> **Related:** [01_Organizational_Context.md](01_Organizational_Context.md) · [02_Problem_Statement.md](02_Problem_Statement.md)

## 1. Purpose and evidence classification

This document identifies stakeholder interests, candidate Application actors, responsibilities, **23 proposed user needs (UN-01–UN-23)**, expected business value, conflicts, and validation needs for the hypothetical organization described in ISP-BA-01 and ISP-BA-02. It does **not** claim that real employees expressed these needs. The numbered UN register is a **new consolidated proposal** derived from the preceding Checklist 1 analysis, **not a verified historical requirements list**.

| Mark | Interpretation |
|---|---|
| H | Hypothesized real-world need or practice; evidence absent |
| P | Proposed product behavior, responsibility or evaluation criterion |
| O | Open question / policy requiring a decision |
| D | Formally approved TRICORE decision; none claimed in this document |
| V | Independently validated evidence; none currently available |

No interviews, surveys, observed workflow, usage metrics, organizational policy, or measured ROI have been supplied. **User needs below are H/P and must not be reported as customer-validated requirements.**

## 2. Stakeholders versus actors

A **stakeholder** has an interest in the problem, policy, outcome or system. An **actor** participates in a use-case interaction. An **authority** is a permission to make a specific business decision; it is not necessarily a separate job title or separate person. A **process owner** may set policies without performing every request operation.

| ID | Stakeholder | Interest / hypothesized concern | Application actor in proposed MVP? | Qualification |
|---|---|---|---|---|
| ST-01 | Requester / Employee | Submit, track and understand requests and outcomes | Yes | Confirms IT outcome when applicable |
| ST-02 | IT Technician | Understand assigned work and record progress/outcome | Yes | Cannot independently close unsuccessful requests |
| ST-03 | Facility Staff | Receive location/context and record repair result | Yes | Does not automatically hold acceptance authority |
| ST-04 | Departmental Intake Owner | Maintain accountability for received requests and exceptions | Yes | May hold other explicitly delegated authorities |
| ST-05 | Assignment Authority | Make legitimate assignment/reassignment decisions | Yes (responsibility) | Can coincide with Intake Owner |
| ST-06 | Completion Authority | Make justified closure decisions | Yes (responsibility) | Can coincide with authorized Intake Owner |
| ST-07 | Facility Acceptance Authority | Evaluate facility repair against acceptance condition | Yes (responsibility) | Not automatically a Manager |
| ST-08 | Manager / Process Owner | Set policy, resolve governance disputes, review outcomes | Not mandatory | Optional oversight, not a mandatory Golden Path step |
| ST-09 | Technical Administrator | Maintain appropriate access and configuration | Supporting actor if needed | Technical administration does not grant business closure power |

**Multiplicity:** These are nine stakeholder/responsibility categories, **not nine required employees or nine distinct system roles**. Proposed use-case actors may be grouped where authority and context permit; detailed actor generalization belongs in Checklist 2.

## 3. Actor responsibilities and boundaries

| Actor / authority | Can initiate or decide | Must not be assumed to do |
|---|---|---|
| Requester | Submit own IT/Facility request; view own request; confirm or reject IT resolution | Assign staff; close without delegated authority; see others' private history |
| IT Technician | View assigned work; report progress, resolution or inability to fulfill | Treat own failure as successful resolution; unilaterally close failure |
| Facility Staff | Handle assigned repair; document outcome | Self-accept repair unless separately authorized |
| Intake Owner | Receive, check scope, coordinate exceptions, follow pending confirmation | Automatically possess assignment, acceptance or completion power |
| Assignment Authority | Assign/reassign within delegated departmental scope | Transfer responsibility outside authorized scope without agreed process |
| Completion Authority | Close fulfilled or unable-to-fulfill when relevant conditions hold | Close successfully without required IT confirmation or Facility acceptance |
| Facility Acceptance Authority | Decide whether repair outcome passes or fails | Treat an unsuccessful repair as accepted |
| Manager / Process Owner | Potentially authorize policies and delegated powers | Be inserted into every MVP request as a compulsory approver |
| Technical Administrator | Support access administration | Override business approval or closure decisions by technical role alone |

### 3.1 Proposed authority constraints

- **AUTH-01:** Requester access is scoped to own requests, subject to further approved exceptions.
- **AUTH-02:** Intake, assignment, completion and Facility acceptance are separate business authorities even when held by the same person.
- **AUTH-03:** A departmental authority is scoped to its delegated domain; cross-department transfer remains **SHOULD**, not a hidden MUST.
- **AUTH-04:** Viewing decision history requires a separate visibility policy; **BL-03 remains open**.
- **AUTH-05:** Every accepted request retains an accountable owner until a valid handoff or closure.

## 4. Stakeholder goals and potential conflicts

| Conflict ID | Parties / tension | Why it matters | Proposed resolution / remaining gap |
|---|---|---|---|
| SC-01 | Requester wants fast completion; Technician needs adequate evidence | Premature resolution or closure | Separate work outcome, confirmation and closure (D-02) |
| SC-02 | Technician reports inability; Intake Owner must preserve accountability | Failure may be hidden or passed around | Document reasons and evaluate alternatives before unable-to-fulfill close (D-03/OD-05) |
| SC-03 | Intake Owner wants throughput; Requester has not confirmed | Indefinite pending versus unsupported auto-close | Do not auto-confirm; manual monitoring (OD-02); no invented deadline |
| SC-04 | Facility Staff reports repair done; evaluator finds it inadequate | Work completion differs from acceptance | Separate repair result and Facility acceptance (D-04/OD-03) |
| SC-05 | Department wants to reject wrong-domain work; Requester wants continuity | Requests may lose accountable ownership | MUST reject with reason; full accepted transfer SHOULD (D-06/OD-04) |
| SC-06 | Staff need decision history; users require confidentiality | Over-broad visibility | BL-03: define actor-by-scope history permissions before approval |
| SC-07 | Competition wants Core reuse; users want a working service | Generic platform features may displace essential tasks | Keep Application acceptance separate from Core reuse proof |

## 5. Proposed User Needs Register — UN-01 to UN-23

**Status convention:** Every item is **H/P (unvalidated)**. `MUST`, `SHOULD` and `COULD` indicate *proposed MVP prioritization*, not a confirmed contractual obligation. Need statements describe user outcomes rather than technical mechanisms.

| ID | Stakeholder | Proposed user need | Priority | Trace to problem / decision |
|---|---|---|---|---|
| UN-01 | Requester | Submit an IT support request with enough context for intake | MUST | P-01; GP-IT-01 |
| UN-02 | Requester | Submit a Facility issue with location and relevant context | MUST | P-01/P-06; GP-FAC-01 |
| UN-03 | Requester | Know whether a request has been recorded and accepted for handling | MUST | P-01/P-02 |
| UN-04 | Requester | View the progress and outcome of their own requests | MUST | P-03; AUTH-01 |
| UN-05 | Requester | Confirm an IT resolution as satisfactory or unsatisfactory | MUST | P-04; D-02 |
| UN-06 | Requester | Understand why a request was rejected as out of scope | MUST | P-02; OD-04 |
| UN-07 | Requester | Know when an IT request ends unsuccessfully and the shareable reason | MUST | P-05; OD-05 |
| UN-08 | Requester | Know what happens while their IT confirmation is pending | MUST | P-04; OD-02 |
| UN-09 | IT Technician | See which IT requests are assigned to them | MUST | P-02; D-01 |
| UN-10 | IT Technician | Access relevant request details and record work progress | MUST | P-03; GP-IT-01 |
| UN-11 | IT Technician | Report a proposed resolution with an understandable outcome | MUST | P-04; D-02 |
| UN-12 | IT Technician | Report inability to fulfill with reasons and work already considered | MUST | P-05; D-03/OD-05 |
| UN-13 | Facility Staff | See assigned Facility issues, including required location details | MUST | P-06; GP-FAC-01 |
| UN-14 | Facility Staff | Record a repair result so it can be evaluated | MUST | P-06; D-04 |
| UN-15 | Intake Owner | Receive and determine whether a request is in departmental scope | MUST | P-02; D-01/OD-04 |
| UN-16 | Intake Owner | Maintain an accountable owner and coordinate unresolved exceptions | MUST | P-02/P-05; D-03 |
| UN-17 | Assignment Authority | Assign work to an authorized handler within delegated scope | MUST | P-02; D-01 |
| UN-18 | Assignment Authority | Reassign an unresolved case for a documented, purposeful reason | MUST for unresolved IT branch | P-05; OD-05 |
| UN-19 | Completion Authority | Close fulfilled requests only when domain-specific completion conditions hold | MUST | P-04/P-06; OD-01 |
| UN-20 | Completion Authority | Close an IT request as unable to fulfill only with justified evidence and authority | MUST | P-05; OD-05 |
| UN-21 | Facility Acceptance Authority | Accept or reject repair outcome and identify a failed result | MUST | P-06; D-04/OD-03 |
| UN-22 | Authorized participants / oversight | Review relevant important business decisions without unnecessary disclosure | MUST, **visibility rule open** | P-07; D-05; BL-03 |
| UN-23 | Requester and departmental coordinators | Have a controlled interdepartmental handoff when the request belongs elsewhere | SHOULD | P-02; D-06/OD-04 |

### 5.1 Need interpretation and boundaries

- **UN-03** is an acknowledgment/ownership need; it does not require a separate notification integration.
- **UN-08** means the request remains pending and visible, not that silence automatically becomes approval. A deadline/exception-close policy is **not established**.
- **UN-18** is MUST only for a legitimate unresolved-IT continuation scenario. Generic convenience reassignment remains SHOULD; no arbitrary numeric reassignment limit is assumed.
- **UN-19** means **IT** requires requester confirmation for successful closure; **Facility** requires authorized acceptance. They must not be collapsed into a single business rule.
- **UN-21** covers acceptance failure and rework direction; the **Facility permanently unable-to-repair closure policy remains BL-02**.
- **UN-22** cannot be marked acceptance-ready until the business visibility matrix is decided. Audit is not blanket visibility.
- **UN-23** is SHOULD for the full transfer-and-accept workflow. The separate MUST requirement to reject out-of-scope requests with a reason is represented by **UN-06 and UN-15**.

## 6. Expected business value — not measured results

| Value ID | Hypothesized benefit | Who benefits | Possible evidence to collect | Current evidence |
|---|---|---|---|---|
| BV-01 | More understandable request acknowledgment and tracking | Requester | User walkthrough; success in locating own requests | None from real organization |
| BV-02 | Clearer accountability at intake and assignment | Intake Owner, staff | Sample workflow reviews; ownership exceptions | None |
| BV-03 | Better explanation of successful and unsuccessful outcomes | Requester, process owner | Case review; outcome classification checks | None |
| BV-04 | More appropriate Facility-specific repair evaluation | Facility stakeholders | Domain expert feedback; accept/reject scenarios | None |
| BV-05 | Better reconstruction of significant decisions | Authorized reviewers | Decision-history scenario evaluation | None |
| BV-06 | Potential reduction in repeated coordination implementation | DX-OS maintainers | Independent Core reuse demonstrations in IT and Facility | Not yet demonstrated |

**Do not claim:** percentage improvement, cost savings, reduced mean resolution time, ROI, real user satisfaction, or superiority over existing helpdesk solutions. A working demo validates scenario behavior, **not actual business impact**.

## 7. Stakeholder influence, participation and validation plan

| Stakeholder group | Proposed validation question | Acceptable future evidence | Status |
|---|---|---|---|
| Requesters | Which information is essential to submit and track? Is IT confirmation useful? | Consented interviews, scenario walkthroughs | Not performed |
| IT Technician | What distinguishes temporary inability from genuinely unfulfillable work? | Domain walkthrough, example cases | Not performed |
| Facility Staff / evaluator | Who evaluates repair results and what counts as pass/fail? | Facility expert review | Not performed |
| Intake / Assignment Authority | Who can accept, reject, assign and close in the real organization? | Approved role/policy review | Not performed |
| Process owner | Is requester nonresponse allowed to remain pending? How is unsuccessful closure authorized? | Approved procedure / review | Not performed |
| Technical administration / privacy stakeholder | Which actors may view which histories and reasons? | Access-policy review | Not performed |
| TRICORE reviewers | Is the hypothetical baseline coherent and feasible for three students? | Recorded review and decision log | Not yet approved |

If access to real stakeholders is unavailable, label the output **scenario validation by the project team**, not user research. Do not fabricate interviewees, quotes, sample sizes or company policies.

## 8. Application needs versus Core reuse hypothesis

| Concern | Application-level user need | Core-level hypothesis / evidence requirement |
|---|---|---|
| Identity and authorization | People act only within delegated responsibilities | Shared authorization capability must remain independent of IT/Facility business-role names |
| Request coordination | Users submit, receive, assign and track domain requests | Common coordination behavior must be reusable without absorbing domain-specific decisions |
| Workflow | IT Confirm and Facility Acceptance differ | Shared workflow mechanism must support both distinct paths, with demonstrable separation |
| Audit | Authorized people review important decisions | Generic decision traceability must not reveal all domain data to everyone |
| Data boundaries | Each request contains relevant domain context | Reuse must not force IT and Facility to share all domain-specific information |

**Competition-specific warning:** Two forms, a shared interface, or two visually similar flows are not sufficient proof of a reusable Core. That claim requires separate technical acceptance evidence in later checkpoints.

## 9. Traceability from problems to needs and proposed acceptance

| Problem from ISP-BA-02 | Needs | Example future business acceptance |
|---|---|---|
| P-01 — fragmented request intake / visibility | UN-01, UN-02, UN-03, UN-04 | Valid request is submitted, acknowledged and trackable by its requester |
| P-02 — ambiguous ownership and scope | UN-06, UN-09, UN-15, UN-16, UN-17, UN-23 | Authorized assignment succeeds; wrong-domain request is rejected with reason |
| P-03 — weak progress visibility | UN-04, UN-10, UN-13 | Authorized actor sees relevant progress without unrelated request exposure |
| P-04 — unclear completion | UN-05, UN-08, UN-11, UN-19 | IT resolution cannot be closed fulfilled without required confirmation |
| P-05 — unresolved outcomes | UN-07, UN-12, UN-16, UN-18, UN-20 | Failure includes reason, authorized decision, distinct unsuccessful outcome |
| P-06 — Facility-specific handling | UN-02, UN-13, UN-14, UN-21 | Failed Facility acceptance cannot close fulfilled |
| P-07 — weak decision accountability | UN-22 | Authorized reviewer can trace significant decisions; unauthorized viewer cannot |
| P-08 / PS-02 — potential common capability reuse | Cross-cutting needs; no direct end-user UN assigned | Separate technical reuse evidence is required, not inferred from Application tests |

This matrix is a **draft traceability bridge**. It does not replace use-case specifications, authorization scenarios, or test results.

## 10. Assumptions, unresolved decisions and risks

| ID | Item | Status | Implication |
|---|---|---|---|
| A-01 | A medium-sized multi-department organization is an appropriate illustrative context | H | Must not be presented as a surveyed company |
| A-02 | Employees would benefit from visible service requests | H | Requires stakeholder validation |
| A-03 | Departmental Intake Owner can coordinate exception decisions | P | Requires organizational authorization |
| A-04 | One individual may hold multiple authorized responsibilities | P | Role separation must remain explicit |
| A-05 | IT requester confirmation is desirable for fulfilled closure | P | May not fit every real service type |
| A-06 | Facility repair should be accepted by an authorized evaluator | P | Domain validation needed |
| A-07 | Significant decisions should be traceable with limited visibility | P | BL-03 open |
| A-08 | No fixed nonresponse deadline is justified yet | O | Pending cases may persist |
| A-09 | Full cross-department transfer is not essential to first MVP | P | Reject-with-reason remains MUST |
| A-10 | Unsuccessful IT closure is permitted when justified | P | OD-05 proposed, not approved |
| A-11 | Facility permanently unable to repair needs a separate decision | O | BL-02 blocker |
| A-12 | A shared DX-OS Core can support both domains | H/P | Must be proven separately |

### Blocking items before business baseline approval

- **BL-02:** Define the Facility path when repair cannot proceed or repeatedly fails; do not silently classify it as fulfilled.
- **BL-03:** Approve a business-level history-visibility matrix by actor, ownership and departmental scope.
- **BL-04:** Reconcile the complete BR-01–BR-072 register against D-01–D-06 and OD-01–OD-05, including MUST/SHOULD conditions.
- **Approval:** TRICORE must explicitly review and accept/revise the proposed decisions and needs. Approval is not inferred from a Git commit or merged PR.

## 11. Document acceptance checklist

- [ ] All ST-01–ST-09 stakeholders and proposed actor responsibilities reviewed.
- [ ] UN-01–UN-23 are understandable user outcomes rather than implementation instructions.
- [ ] Every MUST need has a proposed scenario and trace to a problem/decision.
- [ ] IT and Facility completion conditions remain distinct.
- [ ] Authorization is separated from job title and technical administration.
- [ ] No unvalidated demand, statistics or policy is presented as fact.
- [ ] BL-02, BL-03 and BL-04 are resolved or explicitly recorded as blockers.
- [ ] TRICORE records review comments and a formal approval decision.

**Current gate:** Suitable for repository review as `DRAFT — PROPOSED`; **not approved** for a frozen Requirements Baseline.

## 12. Related documents and next steps

- [01_Organizational_Context.md](01_Organizational_Context.md) — hypothetical organization and ST-01–ST-09 context.
- [02_Problem_Statement.md](02_Problem_Statement.md) — problem hypotheses, alternatives and P-01–P-08 traceability.
- `04_ASIS_TOBE_Process_Analysis.md` — actor actions, decisions and exception flows.
- `05_MVP_Scope_and_Golden_Paths.md` — proposed MoSCoW and GP-IT-01 / GP-FAC-01.
- `06_Business_Rules_and_Decision_Log.md` — full proposed decision and business-rule register.
- `07_Traceability_and_Review_Report.md` — acceptance coverage, blockers and review gate.

## 13. Review record

| Field | Value |
|---|---|
| Prepared for | TRICORE |
| Prepared from | Checklist 1.1–1.8.4 analysis and ISP-BA-01/02 |
| Reviewers | Not assigned / not recorded |
| External stakeholder validation | Not performed |
| TRICORE approval | Not recorded |
| Next action | Review UN-01–UN-23; reconcile BL-02/03/04; capture team decision |
