# DX-OS — Organizational Context

> **Document ID:** ISP-BA-01  
> **Version:** 0.1 (consolidated draft for Checklist 1)  
> **Status:** DRAFT — PROPOSED; not approved or externally validated  
> **Project:** DX-OS Open-Core — TRICORE / OLP PMNM 2026  
> **Application:** Internal Service Portal (ISP)  
> **Scope:** IT Support (primary Golden Path) and Facility Request (secondary reuse scenario)  
> **Owner:** TRICORE (proposed; individual document owner not assigned)  
> **Last updated:** 2026-10-21

## 1. Purpose and document boundaries

This document establishes the **hypothetical organizational setting** used to analyze the ISP application. It identifies the organization model, stakeholders, responsibilities, boundaries, assumptions, and evidence gaps. It is an input to the Problem Statement, AS-IS/TO-BE process analysis, MVP Scope, Business Rules, and Use Case Specification.

**This is a scenario model, not a description of a surveyed company.** No real organization, interview, operational dataset, existing policy, baseline metric, or measured benefit has been supplied. Descriptions of current practices and user needs are hypotheses requiring validation. Team-selected design directions remain **PROPOSED** until recorded approval.

### 1.1 Evidence and decision labels

| Label | Meaning | Permitted interpretation |
|---|---|---|
| **H — Hypothesis** | Assumption about the organization, problem, or users | Requires validation; not a factual claim |
| **P — Proposed** | Proposed business design or scope | For team review, not yet approved |
| **O — Open** | Unresolved decision or evidence gap | Must be tracked and resolved or explicitly bounded |
| **D — Team decision** | Decision formally accepted and recorded by TRICORE | Only assign after team approval |
| **V — Validated** | Supported by documented evidence | Only assign when evidence is available |

At the time of this draft, **no organizational hypotheses are marked V**, and no proposed business policies are marked D.

## 2. Hypothetical organizational profile

### 2.1 Organizational model

**H-ORG-01.** Consider a **medium-sized enterprise with multiple functional departments**, employees using shared internal services, and designated personnel who may receive and resolve internal requests.

**H-ORG-02.** IT-related incidents or support requests and facility-related issues are two plausible categories of internal service demand.

**H-ORG-03.** Employees may need a way to submit requests and understand who is handling them, the current progress, and the final outcome.

**H-ORG-04.** IT and Facility may share generic coordination needs (intake, responsibility, progress, decision history), while having distinct acceptance conditions.

These hypotheses define a **testable illustrative context**. They do not imply a particular headcount, number of sites, reporting hierarchy, operating hours, budget, maturity level, or actual department structure.

### 2.2 Illustrative organizational units

| Unit / function | Role in scenario | Status / qualification |
|---|---|---|
| Employees / requesting departments | Initiate and track internal service requests | H |
| IT Support function | Intake and resolution of IT requests | H |
| Facility / maintenance function | Intake and handling of workplace facility issues | H |
| Departmental intake / assignment responsibility | Coordinates ownership and assignment | P; not necessarily a separate position |
| Organizational management / process owner | May set policies, authorize responsibilities, and review service outcomes | H; not mandatory in each MVP flow |
| Technical administration | Manages access and technical configuration where necessary | P; not automatically a business approval role |

**Important:** The presence of an organizational function does not establish that the organization has a dedicated department or employee with that exact title.

## 3. Business context and hypothesized challenges

The following are **candidate problems** for subsequent analysis, not verified AS-IS findings:

| ID | Hypothesized issue | Potential consequence to investigate | Evidence needed |
|---|---|---|---|
| H-P01 | Requests arrive through fragmented channels (chat, calls, email, spreadsheets, etc.) | Harder to maintain a single request history | Process observation, channel inventory, samples |
| H-P02 | Ownership is not consistently explicit | Requests may be delayed or overlooked | Assignment records, interviews |
| H-P03 | Requesters cannot reliably track status | Repeated follow-ups and uncertainty | User interviews, request journey |
| H-P04 | Decision and outcome history is incomplete | Difficult to reconstruct who decided what | Existing logs/forms, process review |
| H-P05 | IT and Facility require different handling/acceptance rules | A single rigid process may not fit both | Domain expert validation, scenario review |
| H-P06 | Repeated implementations of common capabilities may create maintenance overhead | Potential duplication across applications | Architecture/code review of comparable systems |

No frequency, cost, time saved, or ROI is claimed.

### 3.1 Alternatives to consider

Before claiming ISP or DX-OS is the best organizational solution, compare at least: (1) procedural changes/SOPs, (2) existing forms or shared sheets, (3) an existing helpdesk or service-management product, (4) a standalone custom application, and (5) a shared Core plus ISP demonstration. **The competition objective of demonstrating reusable Core capabilities is distinct from proving an organization's purchasing or adoption case.**

## 4. Stakeholders and responsibilities

| ID | Stakeholder / business role | Hypothesized need or interest | Responsibility in proposed MVP | Status |
|---|---|---|---|---|
| ST-01 | **Requester / Employee** | Submit a request, receive acknowledgement, track progress, understand outcome | Submit and view own requests; confirm IT result when applicable | H/P |
| ST-02 | **IT Technician** | Receive clear assignments and document work | Work on assigned IT requests; report progress, result, or inability to resolve | H/P |
| ST-03 | **Facility Staff** | Understand location, issue, and assigned repair work | Handle assigned Facility requests and record repair outcome | H/P |
| ST-04 | **Intake Owner** | Keep requests within an accountable intake process | Validate scope, receive requests, coordinate exceptions | P; may be combined with other authorized responsibilities |
| ST-05 | **Assignment Authority** | Ensure valid assignment and reassignment | Assign work within authorized departmental scope | P; may be same person as Intake Owner |
| ST-06 | **Completion Authority** | Ensure closure follows the applicable business conditions | Decide authorized closure, including unsuccessful closure when justified | P; may be assigned to Intake Owner |
| ST-07 | **Facility Acceptance Authority** | Judge whether repair outcome meets the defined requirement | Accept or reject Facility outcome | P; must be authorized, not automatically a Manager |
| ST-08 | **Manager / Process Owner** | Policy, oversight, and service accountability | Potential policy owner; **not mandatory in every Golden Path** | H/O |
| ST-09 | **Technical Administrator** | Appropriate access and operation | Technical access administration where needed; no inherent authority to close requests | P |

**Role vs. person:** Multiple business responsibilities may be held by one authorized individual. The table does not prescribe nine separate employees or nine technical roles.

### 4.1 Responsibility principles (proposed)

1. An accepted request must have an accountable intake owner.
2. Intake, assignment, completion, and acceptance are **distinct authorities**, even when one person holds more than one.
3. IT Technician/Facility Staff recording work is not the same as authorizing closure.
4. Requester may see their own request; visibility into other requests or histories requires separate authorization rules.
5. An unsuccessful result must not be reported as successful fulfillment.

## 5. Organizational scope and boundaries

### 5.1 In scope — hypothetical service context

- Internal employee service requests within the illustrative organization.
- IT Support requests as the **primary end-to-end business scenario**.
- Facility repair/service requests as a **secondary, minimal end-to-end scenario**.
- Business responsibilities for intake, assignment, progress, outcome, acceptance, closure, and significant decision traceability.
- Minimum handling of missing information, wrong service scope, failed resolution, non-acceptance, and unauthorized actions.

### 5.2 Out of scope — organizational and product ambitions

- ERP, HR/payroll, finance, purchasing, full inventory/asset management.
- Complex multi-tier approval or escalation structures, enterprise-wide routing, and sophisticated SLA automation.
- AI classification, RAG assistant, full workflow designer, native mobile application, or broad enterprise integrations.
- Emergency-response management. **Potentially hazardous Facility situations must not rely on the portal as a substitute for appropriate emergency/safety procedures.** Actual organizational procedures are not known.
- Claims of measured business improvements or validated organizational demand.

### 5.3 Application versus DX-OS Core

| Concern | ISP Application owns (business meaning) | Core may provide (generic capability) |
|---|---|---|
| Request category | IT vs. Facility classification and domain-specific information | Shared mechanisms, only where genuinely reusable |
| Responsibility | Who may intake, assign, accept, and close under departmental policy | Identity/authorization primitives, if implemented |
| Progress and completion | IT confirmation and Facility repair acceptance conditions | Generic workflow execution support, if implemented |
| Exceptions | Meaning of failed IT support, unsuccessful Facility repair, wrong scope | Generic orchestration and trace mechanisms |
| Traceability | Which business decisions matter and who may view them | Generic audit capability, if implemented |

**Reuse is a claim to test later, not evidence already established.** Two similar screens or two request categories alone do not prove an Open-Core platform.

## 6. Proposed service scenarios

### 6.1 IT Support — primary Golden Path (P)

1. Requester submits a sufficiently described IT request.
2. Authorized IT Intake Owner checks whether it is within scope and accepts it.
3. Assignment Authority assigns a Technician.
4. Technician handles the request and records an outcome.
5. For a proposed successful resolution, Requester confirms that the result is acceptable.
6. Completion Authority closes the request as **Fulfilled** when applicable conditions are satisfied.

**Required exception considerations:** missing information; unauthorized action; Requester rejects result; Requester does not respond (remains pending, not silently confirmed); Technician cannot resolve; authorized closure as **Unable to Fulfill** with documented reasons when justified. Non-response is **not** a valid reason by itself for Unable to Fulfill.

### 6.2 Facility Request — secondary Golden Path (P)

1. Requester reports a facility issue and location where needed.
2. Authorized Facility Intake Owner accepts the request if in scope.
3. Assignment Authority assigns Facility Staff.
4. Facility Staff records repair work and outcome.
5. Facility Acceptance Authority evaluates **Accepted / Not Accepted** under a proposed business criterion.
6. Completion Authority closes as **Fulfilled** only after applicable acceptance conditions are satisfied.

**Required exception considerations:** repair not accepted leads to rework or an authorized next decision; hazardous cases must follow appropriate external safety response; inability to repair further remains **an open policy question** (BL-02). Do not imply a complete failure-closure policy for Facility until it is agreed.

### 6.3 Wrong department / wrong scope (P)

**MUST for MVP:** Authorized intake may reject an out-of-scope request with a reason and appropriate guidance where possible. **SHOULD:** Direct IT↔Facility transfer with receiving-side acceptance and no gap in accountability. A full transfer workflow is not required to complete the initial MVP.

## 7. Assumption register

| ID | Assumption / proposition | Type | Validation approach | Status |
|---|---|---|---|---|
| A-01 | A medium-sized multi-department organization is a useful demonstration setting | H | Scenario review with mentor/domain representative | Unvalidated |
| A-02 | IT Support and Facility are relevant internal service categories | H | Stakeholder/domain interviews | Unvalidated |
| A-03 | Requests may be fragmented across channels | H | Observe intake process; collect channel examples | Unvalidated |
| A-04 | Unclear ownership may cause coordination problems | H | Review assignment practices and sample cases | Unvalidated |
| A-05 | Requesters value acknowledgement and status visibility | H | Interviews and task walkthroughs | Unvalidated |
| A-06 | IT results benefit from Requester confirmation | H/P | Review policy alternatives with intended users | Unvalidated |
| A-07 | Facility repair needs an explicit acceptance decision | H/P | Review repair acceptance scenarios with domain expert | Unvalidated |
| A-08 | Department-level intake/assignment authority is feasible | P | Review organizational authority model | Not approved |
| A-09 | Significant business decisions should be traceable | H/P | Review accountability/privacy needs | Unvalidated |
| A-10 | Common capabilities can be reused between IT and Facility | H | Independent reuse demonstration and tests | Not proven |
| A-11 | Existing simpler alternatives may be insufficient | H | Compare SOP/forms/existing tools against requirements | Unvalidated |
| A-12 | Users would adopt an internal portal | H | User feedback and pilot usability evidence | Unvalidated |

No assumption is silently promoted to an approved requirement or validated fact.

## 8. Evidence register and limitations

| Evidence ID | Evidence currently available | What it supports | What it does **not** support |
|---|---|---|---|
| E-01 | TRICORE's documented Checklist 1 discussion and draft decisions | Internal scenario design and traceable proposals | Real-world organizational prevalence or user approval |
| E-02 | Proposed IT and Facility process walkthroughs | Internal consistency review and candidate acceptance tests | Observation of an actual enterprise process |
| E-03 | Proposed Core/Application responsibility boundary | A hypothesis about separable responsibilities | Successful technical reuse |

**Missing:** Real interviews, process observation, policy documents, baseline metrics, user validation, and technical reuse evidence. If no external evidence is obtained before competition submission, all claims must remain explicitly framed as **hypothetical demonstration** and limitations disclosed.

## 9. Open issues and dependencies

| ID | Issue | Impact | Suggested next action |
|---|---|---|---|
| BL-01 | TRICORE has not approved the business baseline | Cannot mark baseline APPROVED | Team review and recorded decision |
| BL-02 | Facility cannot be repaired further: final business outcome unclear | Facility exception not deterministic | Decide a bounded unsuccessful-completion policy or explicit exclusion |
| BL-03 | Rights to view activity history not yet defined by stakeholder and scope | Privacy/authorization acceptance unclear | Prepare business visibility matrix |
| BL-04 | Business Rules BR-01–BR-072 not yet consolidated and reconciled | Risk of contradictions/duplicate rules | Produce complete rule register with status |
| BL-05 | Real organizational demand not validated | Limits business-value claims | Gather evidence or disclose limitation |
| BL-06 | No operational baseline for effectiveness | Cannot claim quantified improvement | Define metrics; do not fabricate measurements |
| BL-07 | Core reuse not technically demonstrated | Cannot claim successful reuse | Define independent reuse acceptance tests later |
| BL-08 | Post-baseline change control not approved | Scope may drift | Agree review and change procedure |

**Additional open policy:** IT requests awaiting Requester confirmation remain pending without automatic closure. The team has not approved a time-based timeout or manual closure exception for silence; this is an explicit MVP limitation, not a solved closure path.

## 10. Organizational acceptance criteria for this document

| ID | Criterion | Draft assessment |
|---|---|---|
| OC-AC-01 | Hypothetical organizational model stated and bounded | Drafted |
| OC-AC-02 | Stakeholders and business responsibilities identified without inventing job titles | Drafted |
| OC-AC-03 | IT and Facility service contexts differentiated | Drafted |
| OC-AC-04 | In-scope/out-of-scope business boundaries explicit | Drafted |
| OC-AC-05 | Assumptions separated from evidence and decisions | Drafted |
| OC-AC-06 | Open policies and unresolved blockers recorded | Drafted |
| OC-AC-07 | Consistent with Problem Statement, TO-BE, MVP and Decision Log | Pending cross-document review |
| OC-AC-08 | TRICORE reviewed and approved this document | Not met |
| OC-AC-09 | Real organization validated key claims | Not met; not prerequisite for a clearly labeled hypothetical scenario |

**Review verdict:** Suitable as **DRAFT input** to Checklist 1's Business Analysis Package; **not an approved or validated organizational baseline**.

## 11. Traceability to next documents

| This document | Downstream artifact | Intended connection |
|---|---|---|
| H-P01–H-P06 | `02_Problem_Statement.md` | Problem hypotheses, alternatives, evidence gaps |
| ST-01–ST-09 and A-01–A-12 | `03_Stakeholder_and_User_Needs.md` | Stakeholder needs and validation plan |
| IT/Facility scenarios | `04_ASIS_TOBE_Process_Analysis.md` | Process steps, business decisions, exceptions |
| Scope and scenarios | `05_MVP_Scope_and_Golden_Paths.md` | MoSCoW and end-to-end demonstration |
| Proposed responsibilities and open issues | `06_Business_Rules_and_Decision_Log.md` | D-01–D-06, OD-01–OD-05 and rule register |
| Assumptions and criteria | `07_Traceability_and_Review_Report.md` | Acceptance gate and blocker review |

## 12. Review and approval record

| Field | Value |
|---|---|
| Prepared for | TRICORE |
| Reviewers | To be assigned by the team |
| Review date | Not recorded |
| Approval decision | **Not approved** |
| Evidence of approval | None provided |
| Next review | Resolve BL-02, BL-03, BL-04; cross-check other Checklist 1 documents |

---

*Document governance note: Update version and change log when TRICORE makes an explicit decision. Do not change an H/P/O label to D or V without a recorded approval or evidence reference.*
