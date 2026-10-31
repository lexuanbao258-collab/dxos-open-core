# G0-F01 — README Review and Acceptance Checklist

> **Artifact:** `docs/README.md`  
> **Gate:** G0 — Baseline & Governance  
> **Status:** DRAFT / NOT REVIEWED  
> **Date prepared:** 2026-10-28  
> **Documentation baseline:** Pending verification on `DX_OS_DOCS`  
> **Review owner:** To be assigned

## Agreed decisions

| ID | Selected decision | Review expectation |
| --- | --- | --- |
| Q01 | Mixed audience | Reading paths for newcomers, developers, and assessors |
| Q02 | 80–120-word introduction | One bounded introductory paragraph |
| Q03 | Reusable Core + multiple applications | Explicit architectural responsibility boundary |
| Q04 | Unspecified reuse demonstration | Do not silently select Facility or Access Request |
| Q05 | Sequential + role-based reading | Both reading order and reader table |
| Q06 | 12 categories and key entry points | Complete taxonomy without verbose file list |
| Q07 | Link existing files only | Pending paths in plain code, not fake hyperlinks |
| Q08 | One Mermaid architecture figure | Conceptual diagram, not runtime evidence |
| Q09 | Goal and verifiability | Reuse defined as claim to demonstrate |
| Q10 | Document Register as single authority | No duplicate status table |
| Q11 | Source, evidence and review entry points | Distinguish verified URL from planned file paths |
| Q12 | Concise contribution guidance | Link/reference editorial and review conventions |

## Content and architecture checks

- [ ] **F01-C01:** Introduction is 80–120 English words (review counting method recorded).
- [ ] **F01-C02:** Reader audiences and roles are addressed without separate repetitive introductions.
- [ ] **F01-C03:** Core mechanisms are separated from business rules and application ownership.
- [ ] **F01-C04:** IT Helpdesk is presented as reference scenario, not proof of the full release.
- [ ] **F01-C05:** Secondary application selection remains explicitly open.
- [ ] **F01-C06:** Mermaid diagram is logically defensible against source and ADRs.
- [ ] **F01-C07:** Diagram is labeled conceptual and does not assert implemented routing.
- [ ] **F01-C08:** Reading order and role-based paths are both present.
- [ ] **F01-C09:** All twelve documentation areas appear exactly once in the map.
- [ ] **F01-C10:** Reuse claim is connected to acceptance evidence, not asserted as PASS.

## Markdown and link checks

- [ ] **F01-M01:** All prose, diagram labels, headings, and tables use English.
- [ ] **F01-M02:** Exactly one H1; heading levels are well formed and unnumbered.
- [ ] **F01-M03:** Mermaid renders in GitHub Markdown.
- [ ] **F01-M04:** All active internal hyperlinks resolve to tracked files on the reviewed commit.
- [ ] **F01-M05:** Unverified or planned paths are inline code, not broken links.
- [ ] **F01-M06:** External repository link resolves to intended project.
- [ ] **F01-M07:** No copied API contract, source status table, or implementation claim without evidence.
- [ ] **F01-M08:** Document Register is the single source for status and ownership.

## Independent verification

- [ ] **F01-V01:** Review records `DX_OS_DOCS` remote, branch, and full HEAD SHA.
- [ ] **F01-V02:** Review records `dxos-open-core` code baseline, as needed for substantive claims.
- [ ] **F01-V03:** Reviewer runs `git diff --check` and checks Markdown rendering.
- [ ] **F01-V04:** Reviewer checks links in the actual docs revision; any unsupported path is marked planned.
- [ ] **F01-V05:** Reviewer records findings, severity, resolution, and sign-off.

## Suggested PowerShell verification

Run in the documentation repository; commands are not evidence until their output is saved.

```powershell
git remote -v
git status --short --branch
git rev-parse HEAD
git ls-files docs
git diff --check
```

If files are newly created and untracked, `git diff --check` by itself will not inspect them; review the new files explicitly and repeat checks after staging.

## Gate decision

| Field | Value |
| --- | --- |
| Baseline | NOT RECORDED |
| Reviewer | NOT ASSIGNED |
| P0 findings | NOT EVALUATED |
| P1/P2 findings | NOT EVALUATED |
| F01 decision | NOT REVIEWED |
| Sign-off reference | NONE |

**PASS:** All mandatory content and Markdown checks pass, all active links resolve at the reviewed revision, no P0 findings remain, and an independent reviewer signs off. **CONDITIONAL PASS:** Only non-blocking, documented P1/P2 issues remain under an agreed due date. **BLOCKED:** P0 issue, broken required entry point, contradictory authority, or unverified mandatory baseline.

## Review record (complete during independent review)

- Reviewer:
- Review date and time:
- Docs remote / branch / commit:
- Code reference commit:
- Evidence inspected:
- Findings and severity:
- Final decision:
- Required follow-up:
