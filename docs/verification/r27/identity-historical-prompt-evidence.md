<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# R27 historical prompt evidence (DRAFT)

A user-held execution brief, `DXOS_PaaS_Fix_Guide_for_Codex.docx`, dated 17 October 2026, contains a Codex instruction section and WS-1 Identity requirements covering JWT `sub`, `exp`, `aud`, `nbf`, anonymous Bearer HTTP 401, stateless/CSRF, filter-chain and local JWKS tests. This supports a topic-level connection to G3/G4 technical work, not a verified per-file prompt execution or code authorship claim.

| Evidence | Scope | Disposition |
| --- | --- | --- |
| Dated Codex execution brief | G3 JWT security; G4 JWT/OIDC tests | DOCUMENT_IDENTIFIED; EXECUTION_UNVERIFIED |
| Historical discussions about OIDC principal and JWT negative cases | G3 principal and SecurityConfig; G4 related tests | SUMMARY_ONLY; EXACT_PROMPT_NOT_RECOVERED |
| Existing R27 Git register | File paths, blob SHAs, introduction/content commits | TECHNICAL_GIT_HISTORY_ONLY |

Subsequent bounded review matched 14 historical Codex patch calls to exact `call_id` outputs; see the [G3/G4 call evidence](identity-g3-g4-codex-call-evidence.md). The outputs do not verify patch execution, and exact prompt-to-code or full-hunk correspondence remains unverified. Historical ChatGPT responses and manual/terminal integration evidence remain incomplete. External copied/adapted sources remain `UNKNOWN`, test-fixture lineage remains `UNKNOWN`, attribution/NOTICE remain `UNKNOWN/PENDING`, file/portion licensing authority remains `PENDING`, and independent rights review remains `PENDING`. Yến's Apache-2.0 authorization applies only to her lawful rights. Bảo's source content is excluded from this personal declaration.

Keep Issue #138 OPEN, PR #142 DRAFT and R24 release authorization blocked. Do not publish private prompt transcripts or any real tokens in public evidence.
