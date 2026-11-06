# DX-OS — Problem Statement

> **Document ID:** ISP-BA-02  
> **Version:** 0.1 (consolidated draft for Checklist 1)  
> **Status:** DRAFT — PROPOSED; not approved or externally validated  
> **Project:** DX-OS Open-Core — TRICORE / OLP PMNM 2026  
> **Application:** Internal Service Portal (ISP)  
> **Scope:** IT Support (primary Golden Path) and Facility Request (secondary reuse scenario)  
> **Owner:** TRICORE (proposed; individual document owner not assigned)  
> **Last updated:** 2026-10-21  
> **Related document:** [01_Organizational_Context.md](01_Organizational_Context.md)

## 1. Purpose and evidentiary status

This document defines the **hypothesized organizational problem** motivating an Internal Service Portal, explores possible root causes and consequences, compares solution alternatives, and distinguishes the **organizational problem**, the **Application's proposed response**, and the **DX-OS Core reuse hypothesis**. It supports subsequent stakeholder analysis, AS-IS/TO-BE process analysis, MVP scoping, business rules, and acceptance criteria.

**Evidence warning:** The organization is a hypothetical medium-sized, multi-department enterprise. No company interview, survey, observation, internal procedure, operational dataset, cost estimate, measured incident rate, or benchmark has been supplied. The current-state descriptions below are **hypotheses**, not findings. This document does not claim that DX-OS is the optimal commercial solution.

| Label | Meaning |
|---|---|
| **H** | Hypothesis about current practices, causes, impacts, or needs; requires evidence |
| **P** | Proposed product response, requirement, evaluation method, or decision |
| **O** | Open issue or unverified assumption |
| **D** | Formally recorded TRICORE decision; none claimed here |
| **V** | Validated by documented evidence; none claimed here |

## 2. Organizational setting and problem boundary

**H-ORG-01–04 (referenced from ISP-BA-01):** Assume employees of a medium-sized enterprise interact with multiple internal service functions, including IT Support and Facility. The departments may share coordination needs while applying different business acceptance rules. No headcount, ticket volume, geography, service hours, SLA, or formal organizational hierarchy is assumed.

**Problem owner (P):** The relevant departmental process owners or authorized service managers, not the software platform itself. **Affected parties (H):** employees submitting requests, IT/Facility service staff, intake/assignment authorities, and personnel responsible for process oversight.

**In-scope business interaction (P):** submit → receive/check scope → assign → work → determine outcome → confirm/accept when applicable → close, with accountable exceptions and decision history.

**Out-of-scope business transformation (P):** enterprise-wide ERP replacement, procurement, inventory, full asset management, organization-wide process redesign, multi-tier approval bureaucracy, and emergency-response operations.

## 3. Problem statement

### 3.1 Primary problem — organizational level

**PS-01 (H):** In the hypothetical organization, internal IT and Facility service requests **may** be initiated and coordinated through fragmented channels (for example, chat, email, verbal contact, or spreadsheets) without consistently defined ownership, progress visibility, and recorded outcomes. If this is true, requesters and service teams may struggle to know **who is responsible, what is happening, and whether a request has been completed appropriately**.

This is a **coordination and accountability problem**, not inherently a software problem. It might be mitigated through clearer procedures, existing software, or a new application.

### 3.2 Secondary problem — cross-domain process consistency

**PS-02 (H):** IT and Facility services may need common request-coordination capabilities but differ in how successful completion is judged. Inconsistent or duplicated handling of these common steps may complicate service visibility and maintenance. This claim is **not validated** and does not establish a requirement to build a shared platform.

### 3.3 Proposed opportunity — Application level

**PS-APP-01 (P):** Evaluate whether a focused **Internal Service Portal** can make the two request lifecycles observable and accountable: submit, intake, assign, track, resolve/repair, confirm/accept, and close, including relevant failures. IT Support is the primary end-to-end demonstration; Facility Request is a minimal, genuinely distinct second service scenario.

### 3.4 Separate technical hypothesis — Core level

**PS-CORE-01 (H/P):** Some cross-cutting capabilities used by both service domains **may** be reusable through DX-OS Core, with domain-specific rules remaining in the Application. This is an **engineering hypothesis to test**, not the organizational pain point itself. Two forms or two workflows alone do not prove reuse.

## 4. Symptoms, root-cause hypotheses, and consequences

The following is a **causal hypothesis map**, not a measured diagnosis.

| ID | Hypothesized symptom (H) | Possible root cause (H) | Potential consequence (H) | Evidence needed |
|---|---|---|---|---|
| P-01 | Requests appear across several channels | No agreed intake channel or procedure | Missing or duplicate requests; hard to reconstruct history | Channel inventory, sample anonymized requests, interviews |
| P-02 | Employees cannot identify who owns a request | Intake and assignment responsibilities are ambiguous | Repeated follow-ups, unresolved handoffs | Responsibility matrix, observed handoff cases |
| P-03 | Progress updates are inconsistent | No shared lifecycle vocabulary or update responsibility | Low visibility for requesters and coordinators | Current process artifacts, request timelines |
| P-04 | Completion is reported inconsistently | “Work done”, requester confirmation, and authorized closure are conflated | Premature closure or unresolved dissatisfaction | Actual closure policy and sample cases |
| P-05 | Failed requests circulate without a decision | No explicit exception authority or failure outcome | Reassignment loops or silent abandonment | Escalation policies and unresolved-case records |
| P-06 | Facility repair may be considered finished without an appropriate assessment | Acceptance responsibility is undefined | Rework or disagreement about outcome | Facility process owner interview and examples |
| P-07 | It is hard to explain who changed ownership or closed a request | Important decisions are not consistently recorded | Weak accountability and difficult review | Current logs, governance expectations |
| P-08 | IT and Facility repeat similar coordination work | Generic steps are independently defined or implemented | Inconsistent practices or duplicated effort | Existing tools/process comparison, engineering assessment |

**Causal caution:** Fragmented tools are a possible contributing factor, **not a proven root cause**. Poorly defined roles, incentives, policies, or training could persist even after introducing a portal. Likewise, a unified portal does not guarantee reduced delays or better service quality.

## 5. Impact analysis — no fabricated metrics

| Stakeholder | Possible impact if hypotheses hold | Nature of claim | Candidate indicator (not a baseline) |
|---|---|---|---|
| Requester | Uncertainty about receipt, ownership, progress, or outcome | H | Share of requests with visible owner and status |
| IT Technician | Repeated clarification or unclear priorities and handoffs | H | Clarification and reassignment events per request |
| Facility Staff | Incomplete location/problem descriptions; unclear repair acceptance | H | Share of requests with sufficient location and acceptance result |
| Intake Owner | Manual follow-up and disputed responsibility | H | Requests lacking accountable owner |
| Assignment/Completion Authority | Inconsistent or unjustified decisions | H | Decisions with recorded actor and reason |
| Process Owner | Limited ability to assess bottlenecks and failed outcomes | H | Outcome categories and exception frequencies |
| Engineering team | Duplicate coordination behavior across service domains | H | Cross-domain reuse tests and domain-specific change isolation |

**No numerical baseline, percentage improvement, ROI, labor saving, incident volume, or target SLA is asserted.** Any later target must be explicitly labeled as a proposed acceptance threshold or supported by evidence.

## 6. Three layers that must not be conflated

| Layer | Central question | Problem or objective | Appropriate proof | Invalid shortcut |
|---|---|---|---|---|
| **Organization** | Are service requests handled with clear responsibility and outcomes? | Hypothesized coordination/accountability gaps | Interviews, policy/process review, observed cases, baseline data | “We built an app, therefore the business problem existed” |
| **Application — ISP** | Can a small portal represent and support the proposed business flows? | Proposed intake, assignment, tracking, IT confirmation, Facility acceptance, exception closure | End-to-end scenarios, authorization and exception acceptance tests | “CRUD pages exist, therefore the process is solved” |
| **DX-OS Core** | Can genuinely common capabilities be reused without embedding IT/Facility rules? | Engineering reuse, separation of concerns, stable contracts | Two independent consumers or domain scenarios, reuse/independence/change-isolation tests | “Two request types share a screen, therefore Core is reusable” |

**Boundary principle (P):** The Application owns IT/Facility-specific business semantics (who confirms an IT resolution, what qualifies as Facility acceptance, when a failed request may close). Core may provide reusable identity, workflow coordination, data, or audit capabilities **only if reuse and independence are demonstrable**. This is a conceptual boundary, not an architecture specification.

## 7. Alternative responses — do not presuppose DX-OS

| Alternative | What it changes | Potential advantages | Limitations/risks | Evidence required to choose |
|---|---|---|---|---|
| **ALT-01 — Procedure/SOP only** | Clarify intake, ownership, closure and handoff rules without new software | Lowest technical burden; addresses policy ambiguity directly | Manual visibility and traceability may remain limited | Current tools and compliance with procedures |
| **ALT-02 — Forms + spreadsheet/shared tracker** | Standardized submission and lightweight tracking | Fast and inexpensive pilot; useful for validation | Manual coordination, permissions and exception handling may become difficult | Request volume, confidentiality, process complexity |
| **ALT-03 — Existing helpdesk/service platform** | Configure an established product for internal services | Potentially mature capabilities; lower build burden | Licensing, configuration, fit, extensibility, and vendor constraints vary | Product comparison, costs, fit to two scenarios |
| **ALT-04 — Single custom portal** | Build only the ISP Application | Tight MVP focus; easier to demonstrate one product | Common capabilities may remain coupled to ISP | Team capacity, expected reuse requirements |
| **ALT-05 — DX-OS Core + ISP (proposed competition direction)** | Separate reusable capabilities from IT/Facility rules and build ISP on top | Opportunity to demonstrate cross-domain reuse and open-core engineering | Higher architectural/testing cost; risk of overengineering and superficial reuse | Reuse/independence proof, time budget, competition objectives |

**Preliminary assessment (P):** ALT-05 may be appropriate as an **OLP open-core demonstration strategy**, but there is no basis to claim it is the most cost-effective choice for an actual enterprise. ALT-01 through ALT-04 remain legitimate alternatives and should not be dismissed without evidence.

## 8. Proposed business response and scope guardrails

### 8.1 Primary IT Support flow (P)

Requester submits a sufficiently described IT request → authorized IT intake checks scope → authorized assignment → technician works and records resolution → requester confirms satisfactory result → authorized completion closes successfully.

**Failure branch (P):** A technician unable to fulfill records the reason and attempted work. The Intake Owner assesses whether a justified reassignment/escalation is viable; otherwise, a properly authorized Completion Authority may decide **Closed — Unable to Fulfill**, with recorded rationale and a conclusion visible to the requester. This must not be counted as successful resolution.

**Nonresponse branch (O/P):** Lack of requester response does **not** equal confirmation. The request remains pending with an accountable Intake Owner; no automatic close deadline is invented. A policy for final disposition of indefinite nonresponse has **not** been validated or fully specified.

### 8.2 Secondary Facility Request flow (P)

Requester reports a facility issue and relevant location → Facility intake checks scope → authorized assignment → Facility Staff performs work and records outcome → authorized Facility Acceptance evaluates success/failure → authorized completion closes when conditions are met.

**Unsuccessful repair (O):** A failed acceptance must not close as fulfilled. Rework is a proposed minimum continuation; **the terminal outcome when no viable repair is available remains an open business rule (BL-02)**.

### 8.3 Scope decisions (P)

**MUST:** bounded access; request creation and tracking; intake and authorized assignment; IT work/result and requester confirmation; Facility work/acceptance; authorized closure; meaningful decision history; basic missing-information, unauthorized-action, wrong-scope and failure exceptions; end-to-end demonstrations.

**SHOULD:** full cross-department transfer with receiving-party acceptance, ordinary reassignment conveniences, search/filter, proactive notifications, post-closure feedback.

**OUT:** ERP, purchasing, full asset/inventory management, complex SLAs, multi-level approvals, drag-and-drop workflow design, AI/RAG, enterprise-wide integration, emergency-response management.

## 9. Hypotheses and validation plan

| Hypothesis | What must be learned | Feasible validation activity | Status |
|---|---|---|---|
| H-PS-01 | Do fragmented intake channels exist, and where? | Interview a relevant service owner; inspect a redacted process sample | UNVALIDATED |
| H-PS-02 | Are ownership and handoffs unclear? | Map actual responsibility and recent handoff cases | UNVALIDATED |
| H-PS-03 | Is progress visibility a meaningful pain point? | Ask requesters and handlers about status inquiries; inspect records | UNVALIDATED |
| H-PS-04 | How are successful and unsuccessful closures determined? | Obtain current policy or agree on a hypothetical test policy | UNVALIDATED |
| H-PS-05 | Does Facility require independent acceptance, and by whom? | Interview Facility process owner; review representative repair cases | UNVALIDATED |
| H-PS-06 | Is a second domain useful to the business or primarily a reuse test? | Compare actual demand with OLP demonstration goals | UNVALIDATED |
| H-PS-07 | Are shared capabilities genuinely independent of domain semantics? | Define and execute reuse/change-isolation acceptance tests | NOT TESTED |

**Evidence handling:** Keep interview notes, anonymized examples, or process references only when legitimately obtained and permitted to be shared. A student-designed scenario or synthetic test dataset is **demonstration evidence**, not proof of real-world demand.

## 10. Problem-to-response traceability

| Problem hypothesis | Proposed user/business need | Proposed Application behavior | Related acceptance evidence |
|---|---|---|---|
| PS-01 / P-01 | Clear request receipt and tracking | Submit and view own request | Valid submission and tracking scenario |
| PS-01 / P-02 | Accountable handling | Intake and authorized assignment | Named owner; unauthorized assignment denied |
| PS-01 / P-03 | Visible progress and outcome | Work updates and outcome recording | End-to-end IT/Facility path |
| P-04 | Meaningful completion | IT confirm vs authorized closure | Confirm/reject and close conditions |
| P-05 | Controlled unresolved outcome | Review, reassignment or unable-to-fulfill decision | Failed IT case with rationale and authority |
| P-06 | Facility-specific quality decision | Acceptance pass/fail | Failed acceptance cannot close fulfilled |
| P-07 | Accountability for important decisions | Decision history | Actor/action/reason traceability with scoped visibility |
| PS-02 / P-08 | Consistent shared coordination where justified | Common capabilities used by both domains | **Separate Core reuse tests required** |

This is a **draft traceability bridge**, not the final requirements-to-test matrix.

## 11. Open issues and blockers

| ID | Issue | Why it matters | Disposition |
|---|---|---|---|
| **BL-01** | TRICORE has not formally approved the business baseline | Proposed choices must not be presented as policy | OPEN — team review/signoff |
| **BL-02** | Facility cannot be repaired further: no agreed terminal unsuccessful outcome | Incomplete Facility exception path | OPEN — business rule required |
| **BL-03** | Visibility of decision history by role is not fully specified | Authorization and privacy risk | OPEN — role/view matrix required |
| **BL-04** | BR-01–BR-072 not yet reconciled into a full approved register | Possible duplication, contradictions, conditional SHOULD rules | OPEN — consolidate/review |
| **E-01** | No real organizational evidence | Cannot validate the stated pain points or business value | OPEN — validate or retain explicit hypothetical scope |
| **E-02** | No baseline performance measures | Cannot claim improvements or ROI | OPEN — design measurement method |
| **R-01** | No completed cross-domain Core reuse proof | Cannot claim open-core reusability | OPEN — later technical demonstration |
| **O-PS-01** | Final disposition of indefinite IT requester nonresponse | Pending requests may remain indefinitely | OPEN — explicit policy or bounded MVP limitation |

## 12. Acceptance criteria for this document

- [ ] **AC-PS-01:** The organizational problem is stated without presuming a software solution.
- [ ] **AC-PS-02:** All current-state symptoms, causes and impacts are labeled as hypotheses, not survey findings.
- [ ] **AC-PS-03:** Organizational, Application, and Core problems/objectives are distinguished explicitly.
- [ ] **AC-PS-04:** At least SOP-only, Forms/Sheets, existing platform, single custom app, and DX-OS Core + ISP are compared fairly.
- [ ] **AC-PS-05:** No unsupported numerical baseline, ROI, performance improvement, or invented company policy appears.
- [ ] **AC-PS-06:** IT and Facility have separate, recognizable business completion conditions.
- [ ] **AC-PS-07:** Unresolved IT, Facility failure, wrong scope, and requester nonresponse are not misrepresented as successful closure.
- [ ] **AC-PS-08:** Proposed MVP response can be traced back to a hypothesized problem or an explicitly stated competition/reuse objective.
- [ ] **AC-PS-09:** Open issues and validation needs are recorded with no false signoff.
- [ ] **AC-PS-10:** TRICORE reviews the document and records its decision before assigning APPROVED status.

**Current gate assessment:** **CONDITIONAL REVIEW READY** — suitable for repository review as a draft; **NOT APPROVED** and **NOT VALIDATED**.

## 13. Dependencies and next documents

- **Upstream:** [01_Organizational_Context.md](01_Organizational_Context.md) — hypothetical organization, stakeholders, boundaries and assumptions.
- **Next:** `03_Stakeholder_and_User_Needs.md` — stakeholder interests, actor responsibilities and hypothesized needs.
- **Process:** `04_ASIS_TOBE_Process_Analysis.md` — testable current-state hypotheses and proposed workflows.
- **Scope:** `05_MVP_Scope_and_Golden_Paths.md` — MoSCoW and full business scenarios.
- **Rules:** `06_Business_Rules_and_Decision_Log.md` — reconciled decisions and rule register.
- **Review:** `07_Traceability_and_Review_Report.md` — evidence gaps, traceability and acceptance gate.

## 14. Review and approval record

| Field | Value |
|---|---|
| Prepared for | TRICORE internal review |
| Reviewer(s) | Not yet assigned/recorded |
| Team decision | Not recorded |
| Business stakeholder validation | Not performed |
| Approved version | None |
| Current status | **DRAFT — PROPOSED** |

> **Reviewer note:** The strongest defensible claim is that the team has constructed a coherent, testable **hypothetical problem framing** for an OLP open-core demonstration. The team cannot yet claim verified organizational demand, measurable business benefit, or proven Core reuse.
