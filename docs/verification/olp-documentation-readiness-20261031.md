# DX-OS OLP — Public API Documentation readiness (2026-10-31)

> **Scope:** documentation/evidence only, not Backend release or API security acceptance.
> **Status:** PARTIAL / REVIEW REQUIRED. Do not say "deployed", "HTTP 200 verified", "Swagger authorized E2E passed" until corresponding proof is attached.

## Source-verified checkpoints

- Root static API reference exists on the **gh-pages** branch at **index.html**.
- The root page enumerates **exactly nine** IT Helpdesk HTTP operations: list, create, detail, history, triage, assign, start, resolve and confirm.
- Static source inspection found all intra-page anchors resolve to existing IDs. This is **not** a browser click test.
- The page includes Bearer OIDC/JWT, request examples, statuses 400/401/403/404/409, Draft/MVP warning and no live Execute button.
- The new **contract matrix** lists method, path, principal input, expected business effect and role constraint. This is source-derived and is not an accepted runtime response-schema export.
- Responsive CSS media rules exist, but no independent real-device viewport/accessibility acceptance has been recorded.
- Relevant candidates: [#79 Swagger/OpenAPI](https://github.com/lexuanbao258-collab/dxos-open-core/pull/79), [#81 Developer Portal V2](https://github.com/lexuanbao258-collab/dxos-open-core/pull/81), [#74 Helpdesk MVP](https://github.com/lexuanbao258-collab/dxos-open-core/pull/74), and [Issue #37](https://github.com/lexuanbao258-collab/dxos-open-core/issues/37). Draft PRs are not approved or merged.

## Operator Windows follow-up — 2026-10-31

- Operator successfully cloned this PR branch into `C:\Users\Admin\dxos-docs-olp-pr92` and confirmed evidence script exists.
- Script entered **both** public Documentation screenshot branches (which run only after its HTTP 200 probe). This is consistent with a successful operator-side HTTP 200 check; the saved manifest should be inspected for explicit confirmation.
- PNG capture initially failed after Chromium logged a renderer warning on stderr. Because the original script used direct native invocation with PowerShell stop-on-error, this warning prematurely triggered the catch handler. **This is not evidence that the Documentation HTML failed to load.**
- Swagger check at `127.0.0.1:8080` returned connection failure: no reachable local Backend listener. Static `gh-pages` checkout alone cannot run Swagger.
- Candidate script patch uses `Start-Process` with separate stderr/stdout files, validates PNG existence and size, and avoids treating Chromium diagnostic stderr as a capture failure. **This fix is committed but has not yet been executed on the operator machine.**
- Follow-up: operator runs `git pull --ff-only`, reruns the script, reviews `olp-report-evidence/manifest.json`, checks PNGs visually, and separately starts the opt-in local Backend if a Swagger screenshot is required.

## Not verified / blockers

1. **GitHub Pages deploy & external HTTP 200: NOT VERIFIED.** Expected URL: https://lexuanbao258-collab.github.io/dxos-open-core/ . Open Repository → Settings → Pages; check Source = Deploy from a branch, Branch = gh-pages, Folder = /(root), then check deployment status, public HTTP 200 and asset loading.
2. **Actual desktop and mobile browser experience: NOT VERIFIED.** Inspect navigation, nine collapsible operations, horizontal table scroll, narrow viewport, text overflow and keyboard focus.
3. **External links: identified in source, not independently clicked in an external browser.** Check PR #74, #79, #81, Issue #37 and the Helpdesk Swagger runbook from the public site.
4. **Swagger local screenshot: NOT CAPTURED by this review.** By design Swagger stays on loopback and no public API server is exposed.
5. **Runtime OpenAPI schema matching: NOT ACCEPTED.** Prior review of PR #81 reported 5 P1 (UTF-8 exporter, request validation, response nullability, error/status definitions, wildcard success-schema handling) and 4 P2. Some have fix candidates; full gates still open.

## Windows evidence capture (read-only)

1. Confirm Pages actually serves HTTP 200 and Backend Swagger UI is explicitly enabled on 127.0.0.1:8080 with a disposable demo environment. Never enable a public Swagger listener.
2. In this branch's worktree run:

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\capture-olp-docs-evidence.ps1
~~~

3. Review olp-docs-evidence/manifest.json and the PNG screenshots for desktop Documentation, narrow-viewport Documentation and local Swagger. Each screenshot is captured **only after HTTP 200** at the relevant address.
4. Inspect the screenshots for credentials, private IDs, business data and tokens before sharing with the lecturers. Do not commit evidence without review.

## Suggested report wording (only if publicly accessible)

> Nhóm đã xây dựng website tài liệu API DX-OS (MVP/Draft), mô tả 9 thao tác HTTP IT Helpdesk, xác thực và phân quyền, quy trình xử lý và hướng dẫn tích hợp. Swagger UI được thiết kế cho môi trường demo nội bộ; các kiểm thử hợp đồng và ACL nâng cao vẫn đang được đối chiếu. Nhóm không công bố Backend API ra Internet.

If Pages is not available, write **"đã chuẩn bị mã nguồn website tài liệu; đang cấu hình xuất bản"** rather than a claim that the public documentation website is live.

## Gate before submitting to lecturer

- [ ] GitHub Pages HTTP 200 verified externally, screenshot + deployment status saved.
- [ ] Public desktop and mobile/narrow view checked, navigation/links tested.
- [ ] Nine routes and explicit MVP/Draft notice visible.
- [ ] Loopback-only Swagger screenshot without credentials.
- [ ] Independent review and merge of this documentation-only candidate per team process.

**Safety:** do not publish real tokens, production API base URLs or Swagger live listeners; no bypass of #37, #79, #81 review gates.
