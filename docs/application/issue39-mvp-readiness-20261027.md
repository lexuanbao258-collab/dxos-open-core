# Application MVP readiness — 27/10/2026

Phạm vi: **DX-OS Open-Core / DX-OS Internal Service Portal**, IT Support và Facility Maintenance theo AGENTS.md. Source local đầu lượt là chuẩn giao diện. Không khôi phục PR #52, không redesign, không thêm reject/close, API, actor, quyền hoặc trạng thái mới. Chỉ sửa Application; Backend, Gateway, Identity và PostgreSQL không thay đổi.

## Kết quả theo nhiệm vụ Bảo giao

| Nhiệm vụ | Trạng thái | Kết luận |
| --- | --- | --- |
| 1. Commit HTTP Adapter | **PARTIAL** | Code APP-13 **DONE/PASS**: typed client, codecs, Application service và tests đã có từ đầu lượt. Không viết lại; chưa commit theo yêu cầu an toàn Git. Concrete LIVE transport **BLOCKED**. |
| 2. Tích hợp Assignment Form | **DONE trong fake integration** | APP-11 đã được APP-14 mount trong Detail qua optional `helpdeskService`. Sửa follow-up GET sau logout; giữ `expectedAssignmentVersion`, eligibility và exact `allowedActions`. LIVE **BLOCKED**. |
| 3. Hoàn thiện Login/Logout | **PARTIAL** | Demo login/restore/logout/expiry, storage fallback và protected routes PASS. Đã sửa late-response/session isolation. Email/password preview, OIDC/BFF login, session refresh/revocation và logout thật **BLOCKED**. |
| 4. Chuẩn bị BFF/Backend thật và E2E | **PARTIAL** | Presentation/service/transport injection seams và fake browser journey **DONE/PASS**. Chưa đủ approved BFF/session/transport contract để kết nối thật; Portal → BFF → Gateway → Backend E2E **BLOCKED**. |

Các checkpoint ngày 26/10 là lịch sử. APP-14 đã giải quyết blocker mount Portal của checkpoint APP-11; không coi câu “Portal integration BLOCKED” trong báo cáo APP-10/11/13 cũ là trạng thái fake integration hiện tại.

| Hạng mục | Đã có trước lượt này | Xác minh hiện tại |
| --- | --- | --- |
| APP-10 | Wire/presentation model riêng; state authoritative, optional/redacted fields, list cursor và history tối thiểu | PASS; không ép sang legacy MOCK model, không giả display name/metadata |
| APP-11 | AssignmentForm, binding và component/fake transport tests | PASS; assign là Application command, không đổi Workflow version/history ở frontend |
| APP-13 | Chín route candidate PR #63, codecs, safe errors, 401 callback đóng client, single-attempt writes | PASS; không đổi adapter implementation trong lượt này |
| APP-14 | Create/List/Detail/History/action panel/Assignment; optional injection trong App/provider | PASS; production entry vẫn `<App />`, default MOCK; fake entry riêng |
| Facility Maintenance | Legacy MOCK hiện tại | Regression PASS; không có HTTP Facility hoặc tuyên bố Facility E2E thật |

## Những lỗi thực sự đã sửa

Baseline full suite: **15 files / 353 PASS**. Thêm regression tests trước khi sửa đã tái hiện **9 FAIL / 45 PASS** trong hai suites liên quan:

1. Create success trả sau khi rời form vẫn chuyển trang; create/transition success cũ phát toast vào phiên mới.
2. Create/transition 401 của phiên cũ làm hết hạn phiên mới, kể cả khi vào lại cùng demo role.
3. MVP read/triage 401 bị bỏ qua sau khi rời feature, khiến Portal còn hiển thị phiên hoạt động dù client đã bị đóng.
4. Assignment confirmed sau logout vẫn bắt đầu GET Detail.
5. MVP mutation 401 cũ lưu write stop vào provider và khóa thao tác của service/phiên mới.

Provider hiện tạo ranh giới phiên cục bộ, vô hiệu hóa đồng bộ khi logout/expiry/đổi role và kiểm tra service injection hiện hành. `useSessionOperation` chụp ranh giới trước `await`: side effects UI cần màn hình còn mount và phiên còn đúng; 401 của chính phiên đó vẫn kết thúc phiên sau điều hướng. 401 từ phiên/service đã thay thế không tác động phiên mới.

Assignment binding có guard trước follow-up read; không hủy lệnh assign đã gửi. Workflow pending locks, 409 explicit reload và unknown-write stops của APP-14 được giữ. Unknown outcome đến sau logout vẫn khóa ghi; có regression riêng xác minh không vô tình mở lại bằng phiên mới. Không tự retry, refresh token, reconcile hoặc tuyên bố ghi thất bại/thành công khi thiếu bằng chứng.

Final full suite: **15 files / 365 PASS**, tăng 12 cases so với baseline. Các tests cũ vẫn được giữ.

### File thay đổi trong lượt này so với source local đầu lượt

Mười file có sẵn được cập nhật:

- `frontend/src/context.tsx`
- `frontend/src/hooks/useRequests.ts`
- `frontend/src/pages/NewRequest.tsx`
- `frontend/src/pages/RequestDetail.tsx`
- `frontend/src/features/helpdesk/HelpdeskPortal.tsx`
- `frontend/src/features/helpdesk/useHelpdeskRead.ts`
- `frontend/src/services/mvp/assignmentBinding.ts`
- `frontend/src/context.service.test.tsx`
- `frontend/src/features/helpdesk/HelpdeskPortal.test.tsx`
- `frontend/README.md` — sửa mô tả cũ “chưa có DTO/adapter”, ghi đúng fake integration và giới hạn phiên.

Bốn file mới:

- `frontend/src/hooks/useSessionOperation.ts`
- `frontend/tests/session.spec.ts`
- `docs/application/issue39-mvp-readiness-20261027.md`
- `docs/application/issue39-mvp-commit-manifest-20261027.md`

Không sửa App layout, Auth UI, CSS/theme, fixtures, screenshots hoặc các tài liệu lịch sử. Những diff sẵn có của các file đó vẫn được bảo toàn, không nhận thành thay đổi của lượt này.

## Login/Logout đang ở mức nào

- `/session`: vào demo với hai persona có sẵn; role hợp lệ khôi phục trong cùng tab; storage bị chặn có fallback bộ nhớ. Không phải xác thực danh tính Backend.
- Kết thúc phiên xóa role trong bộ nhớ/storage khi storage cho phép, vô hiệu hóa response cũ; protected routes không mở lại qua Back/Reload.
- 401 đọc/ghi của phiên hiện hành kết thúc demo và xóa notice cũ; yêu cầu chủ động vào lại. HTTP client instance đã nhận 401 vẫn đóng; không “đăng nhập lại” vào cùng client bằng demo role.
- `/login`, `/register`, recovery và SSO vẫn **BLOCKED**: chỉ preview validation, không gửi credentials, không cấp tài khoản/role/token.
- Chưa có BFF cookie/session, verified principal bootstrap, login/callback/logout endpoints, refresh policy, CSRF mechanism hoặc provider logout được phê duyệt/triển khai trong Application. Không tự đặt route hay chọn cookie/token architecture thay Yến.
- Unknown-write stops chỉ trong provider memory; hard refresh tạo fake instance mới, không phải durable recovery xuyên phiên. Demo storage removal bị trình duyệt từ chối chỉ bảo đảm logout trong bộ nhớ hiện hành; không có claim thu hồi phiên server.

## Nguồn contract đã đối chiếu chỉ đọc

- [PR #63](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63): metadata trả draft/open, candidate SHA `e12d105c89400a5fd69dd6cc1fca670ff1f04f51`; adapter local đã pin model này. Không có formal review trong endpoint reviews đã đọc; điều đó không phủ nhận review evidence trong comments.
- [Yến — B06 independent evidence](https://github.com/lexuanbao258-collab/dxos-open-core/pull/63#issuecomment-6393813935): ghi **33 PASS / 0 FAIL / 0 SKIP** tại đúng `e12d105…`, Keycloak thật và disposable PostgreSQL. Evidence được đọc qua connector, **không chạy lại Backend trong lượt này**; disposition vẫn DRAFT / LIVE_BLOCKED, còn provisioning/privileges và Portal acceptance. Không dùng mô tả PR cũ “B06 chưa chạy” để phủ nhận comment mới hơn.
- [Owner handoff PR #60 ngày 27/10](https://github.com/lexuanbao258-collab/dxos-open-core/pull/60#issuecomment-6431682163): tham chiếu new HEAD `9f1c4972324798c3cf776d2ee79615b9942b3078`, controlled IT first-demo đến CONFIRMED, vẫn DRAFT/LIVE_BLOCKED. Metadata PR #60 trong lần đọc còn trả `facd75e…`; không dùng metadata đó để suy new HEAD đã được ratify. Lượt này không thực hiện formal review §9A hoặc gửi review/comment.
- Local docs và các nguồn #24/#39/#60/#63 đã đọc chưa cung cấp đủ bằng chứng **approved BFF login/session/logout contract**. Giữ BLOCKED; không suy B06 Backend PASS là BFF/Portal approval.

## Yến/Bảo cần cung cấp để kết nối thật

**Yến:** immutable permalink/SHA và approval của BFF/browser authentication contract: login/callback/logout behavior, verified session/principal projection, expiry và refresh/reentry policy, 401 callback, CSRF/cookie/CORS/credentials requirements theo architecture đã chọn, và safe error/visibility rules. Cần approved local configuration/runbook và test accounts/tenant/resource grants; secrets chỉ qua kênh cấu hình được duyệt, không đưa vào repo/báo cáo/browser storage.

**Bảo:** exact Backend + contract versions được phép dùng cho controlled integration, disposition §9A/PR #60 và các business gates còn mở; approved provisioning/runtime/security dependencies do owner tương ứng xác nhận. Chốt operator/reconciliation procedure cho lost-create-ID hoặc unknown outcome, điều kiện mở khóa xuyên phiên; không tự thêm idempotency API hoặc giả reconciliation từ một lần GET.

Hai owner cần xác nhận acceptance matrix cho integrated Portal/BFF E2E và môi trường disposable được phép chạy. Reject/dispute, unresolved/close và Facility không được triển khai từ các đề xuất chưa ratify; yêu cầu chưa triển khai tiếp tục NOT IMPLEMENTED/CANDIDATE/BLOCKED tương ứng.

## Kiểm thử có thể tái hiện

Đã dùng dependencies local, không cài thêm. Chạy trong `frontend/`:

```powershell
npx.cmd tsc --noEmit
npm.cmd test -- --reporter=dot --maxWorkers=2
npx.cmd playwright test --config tests/mvp.playwright.config.ts
```

Kết quả lần lượt: PASS; **365/365**; **1/1 Chromium** fake journey (desktop 1440/mobile 390, không overflow, không phát hiện axe WCAG violations, không API/external requests). Không có skipped trong các lệnh trên.

Production build lần này ghi ra temp để bảo toàn `frontend/dist` có sẵn:

```powershell
$artifact = Join-Path $env:TEMP 'dxos-app-mvp-audit-20261027-084156/production'
npx.cmd vite build --outDir $artifact --emptyOutDir false
node node_modules/vite/bin/vite.js preview --host 127.0.0.1 --port 4173 --strictPort --outDir $artifact
```

Chạy preview ở terminal riêng, sau khi fake-preview server đã dừng. Terminal khác, trong `frontend/`:

```powershell
npx.cmd playwright test tests/session.spec.ts tests/auth.spec.ts tests/portal.spec.ts --grep 'demo |blocked forms|all eight|create, approve|simulated|uncertain write|hidden and nonexistent' --output "$env:TEMP/dxos-app-mvp-audit-20261027-084156/production-browser"
```

**12/12 Chromium PASS**: auth blocked/no credentials, tám màn hiện tại, IT confirmation, Facility MOCK, network/401/403/404, unknown write, hidden/missing và hai session tests mới. Runner dùng production artifact mới, không dùng `dist` cũ. Windows fake-preview teardown cần dừng đúng Vite PID do lượt này tạo sau khi test đã PASS; runner cuối cùng exit 0. Production preview cũng được dừng sau kiểm thử.

Các kiểm tra bổ sung PASS:

- `npm.cmd run check:security`: heuristic source + existing dist; đã chạy thêm **cùng script với duy nhất scan path `dist` thay bằng production temp artifact mới**, 63 source/bundle files PASS. Script thích nghi ở `<auditRoot>/check-built-security.mjs`, chạy từ `frontend/` bằng `node <auditRoot>/check-built-security.mjs`.
- Production bundle giữ `LIVE_BLOCKED`, không chứa fake transport hoặc các fixture subject/worker markers đã kiểm; `main.tsx`/composition root không đổi.
- Prettier trên đúng code/tests được sửa; `git diff --check`; hash preservation audit.
- Không chạy full responsive/zoom/browser matrix, Backend, Identity, PostgreSQL hay LIVE E2E trong lượt này; không suy PASS ngoài phạm vi trên.

## Phân loại commit và PR đề xuất — chưa thao tác Git

Danh sách **từng file dirty**, disposition và lý do ở [commit manifest](issue39-mvp-commit-manifest-20261027.md). `FULL` là file Application có thể review nguyên file; `HUNKS` phải tách phần chức năng khỏi baseline UI có sẵn; `PREREQUISITE` là chức năng MOCK đã có cần baseline chứa nó; `HOLD` không đưa vào PR tích hợp MVP này. Không dùng `git add .` hoặc stage toàn thư mục frontend/docs.

Đề xuất các commit theo thứ tự phụ thuộc sau khi Ánh/Bảo review:

1. `feat(application): add fake-tested Helpdesk HTTP adapter and assignment form` — foundation candidate có sẵn, APP-10/11/13, fixtures và tests; không transport LIVE.
2. `fix(application): isolate async UI work by demo session` — session guard/provider, read/create/transition wiring, session regression tests; dựa trên MOCK functional baseline đã được review.
3. `feat(application): wire fake Helpdesk MVP into Portal` — APP-14 feature, optional App/provider injection, fake browser entry; giữ nguyên shell/theme local.
4. `docs(application): record MVP readiness and integration gates` — bằng chứng/manifest/hướng dẫn chạy chính xác.

Phân chia này là đề xuất review, chưa phải staged patches hoặc từng commit đã được test độc lập. `context.tsx` có cả provider injection/stops và session guard: cần chọn hunks theo phụ thuộc, tránh một commit trung gian import file chưa có. Nếu chưa chốt baseline MOCK/UI để làm base, giữ PR draft; không giải quyết bằng cách đưa toàn bộ redesign cũ vào PR này hoặc checkout lại UI cũ.

Đề xuất PR: **`Application: prepare IT Helpdesk fake integration and fix session lifecycle`**, draft, refs #39/#53; mô tả phải ghi “fake transport PASS; real BFF/Portal E2E BLOCKED”. Chưa tạo PR, commit, branch, push hay merge. Không gọi PASS full working tree là bằng chứng một subset commit chưa tạo đã PASS.

## Git và bảo toàn

- Branch thực tế đầu/cuối: `feature/issue39-mvp-integration`; không dùng tên worktree hoặc branch trong checkpoint cũ làm tên branch hiện tại.
- HEAD đầu/cuối: `00b10fa43d01cc3dcca725b2ed66a43bdaa92268`.
- Ban đầu **25 tracked modified + 53 untracked files**, staged 0. Cuối **25 tracked modified + 57 untracked files**, staged 0.
- Snapshot SHA-256 ban đầu có **296 files**. Chỉ mười file có sẵn đã liệt kê đổi bytes; **286 file còn lại giữ nguyên**, không mất file. Bốn file mới đúng danh sách trên.
- CSS/theme, Auth, hai ảnh evidence đã modified trước, và toàn bộ file ngoài Application được giữ nguyên so với đầu lượt.
- Audit artifacts/hash/status/diff và backup code trước sửa: `%TEMP%/dxos-app-mvp-audit-20261027-084156/`. Final preservation/status audit cũng ở thư mục này.
- Không stage/commit/push/merge, không tạo branch, không reset/clean/stash/rebase/fetch; không sửa `.git` bằng tay.

## Checklist còn lại đến E2E thật

- [x] Kiểm kê APP-10/11/13/14; giữ phần PASS, sửa session/assignment follow-up bugs.
- [x] Fake unit/component/adapter + Portal browser journey và production regression.
- [x] Chuẩn bị file/hunk manifest và draft commit/PR proposal; bảo toàn worktree.
- [ ] Ánh/Bảo review baseline local và patches; chỉ sau chỉ thị mới thao tác commit/PR.
- [ ] Yến cung cấp approved BFF authentication/session/logout/error contract và exact revision.
- [ ] Bảo/Yến xác nhận exact Backend/security/provisioning revisions và controlled disposable runbook.
- [ ] Implement transport/auth composition theo contract đã duyệt trong phạm vi Application; default MOCK vẫn giữ cho đến quyết định riêng.
- [ ] Chạy Portal → BFF → Gateway → Helpdesk thật: login, create/list/detail, triage/assign/start/resolve/confirm, history, logout/reentry.
- [ ] Negative E2E: 401/expired/revoked session, 403/hidden-404, cross-resource/tenant, stale Workflow/assignment versions, double submit, revoked assignee, lost reply/unknown outcome không replay; giữ truthful state/history.
- [ ] Independent acceptance có SHA, môi trường, commands, test counts và sign-off; chỉ sau đó đánh giá mở LIVE. B06 Backend evidence riêng không thay bước này.
