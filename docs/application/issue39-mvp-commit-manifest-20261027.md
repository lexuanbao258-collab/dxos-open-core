# Application commit manifest — 27/10/2026

Source of truth: working tree `feature/issue39-mvp-integration`, HEAD `00b10fa43d01cc3dcca725b2ed66a43bdaa92268`. Đây là **kế hoạch review**, không có staged files/commit/PR mới. [Báo cáo và cách chạy tests](issue39-mvp-readiness-20261027.md).

Mỗi file dirty có đúng một disposition bên dưới. **FULL** = có thể review nguyên file cho Application; **HUNKS** = chỉ chọn chức năng, không stage nguyên file; **PREREQUISITE** = MOCK baseline có sẵn phải được review/làm base trước; **HOLD** = chưa đưa vào PR MVP này. FULL không có nghĩa đã được phép commit. Bằng chứng PASS thuộc toàn bộ source local hiện tại, chưa chứng minh subset hunks chưa tạo có thể build độc lập.

Không stage `frontend/`, `docs/` hoặc dùng `git add .`. Khi base chưa chứa current local UI và functional MOCK baseline, không ghép adapter diff với toàn bộ redesign để ép build. Cần base/commit sequencing đã review; chưa thực hiện checkout hay thao tác Git nào để tạo nó.

## FULL — 39 files

| File | Git | Lý do / ranh giới chọn |
| --- | --- | --- |
| `docs/application/issue39-app10-app11-app13-verification.md` | `??` | Application evidence/readiness/manifest; giữ rõ ngày và fake/LIVE boundary. |
| `docs/application/issue39-app14-presentation-integration.md` | `??` | Application evidence/readiness/manifest; giữ rõ ngày và fake/LIVE boundary. |
| `docs/application/issue39-mvp-commit-manifest-20261027.md` | `??` | Application evidence/readiness/manifest; giữ rõ ngày và fake/LIVE boundary. |
| `docs/application/issue39-mvp-readiness-20261027.md` | `??` | Application evidence/readiness/manifest; giữ rõ ngày và fake/LIVE boundary. |
| `frontend/src/components/AssignmentForm.integration.test.tsx` | `??` | APP-11 form/binding integration tests đã có; không redesign. |
| `frontend/src/components/AssignmentForm.test.tsx` | `??` | APP-11 form/binding integration tests đã có; không redesign. |
| `frontend/src/components/AssignmentForm.tsx` | `??` | APP-11 form/binding integration tests đã có; không redesign. |
| `frontend/src/context.service.test.tsx` | `??` | Application seam/session hoặc fake-only test support. |
| `frontend/src/features/helpdesk/HelpdeskPortal.test.tsx` | `??` | APP-14 feature/read hook/integration tests, gồm session guards mới. |
| `frontend/src/features/helpdesk/HelpdeskPortal.tsx` | `??` | APP-14 feature/read hook/integration tests, gồm session guards mới. |
| `frontend/src/features/helpdesk/useHelpdeskRead.ts` | `??` | APP-14 feature/read hook/integration tests, gồm session guards mới. |
| `frontend/src/hooks/useSessionOperation.ts` | `??` | Application seam/session hoặc fake-only test support. |
| `frontend/src/services/mvp/HelpdeskRequestService.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/assignmentBinding.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/commands.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/services/mvp/errors.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/foundation.test.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/services/mvp/helpdeskPortalPort.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/helpdeskPresentation.test.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/helpdeskPresentation.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/HelpdeskApiClient.test.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/HelpdeskApiClient.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/codecs.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/errors.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/liveGate.test.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/transport.ts` | `??` | APP-10/11/13/14 service/model/adapter và tests; chỉ injected fake transport. |
| `frontend/src/services/mvp/http/types.ts` | `??` | Wire DTO candidate đã pin PR #63; tách normalized model, không cấp LIVE approval. |
| `frontend/src/services/mvp/port.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/services/mvp/presentation.test.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/services/mvp/presentation.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/services/mvp/projections.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/services/mvp/types.ts` | `??` | Foundation CANDIDATE có sẵn; giữ tách normalized model khỏi wire DTO, không cấp LIVE approval. |
| `frontend/src/test/fakeHelpdeskTransport.ts` | `??` | Application seam/session hoặc fake-only test support. |
| `frontend/src/test/helpdeskFixtures.ts` | `??` | Application seam/session hoặc fake-only test support. |
| `frontend/tests/helpdesk-mvp.spec.ts` | `??` | Fake browser entry/journey hoặc production session regression; không vào production default. |
| `frontend/tests/mvp-preview.html` | `??` | Fake browser entry/journey hoặc production session regression; không vào production default. |
| `frontend/tests/mvp-preview.tsx` | `??` | Fake browser entry/journey hoặc production session regression; không vào production default. |
| `frontend/tests/mvp.playwright.config.ts` | `??` | Fake browser entry/journey hoặc production session regression; không vào production default. |
| `frontend/tests/session.spec.ts` | `??` | Fake browser entry/journey hoặc production session regression; không vào production default. |

## HUNKS — 7 files

| File | Git | Lý do / ranh giới chọn |
| --- | --- | --- |
| `frontend/README.md` | `M` | Integration/session/test instructions mới; không stage toàn bộ copy alignment cũ. |
| `frontend/src/App.tsx` | `M` | Chỉ import/prop helpdeskService, provider injection, route MVP và conditional nav link; giữ các hunks shell/copy/focus local cho baseline UI riêng. |
| `frontend/src/context.tsx` | `M` | RequestService/MVP injection, pending/stops, storage/session lifecycle và captureSession; tách theo dependency của các commit đề xuất. |
| `frontend/src/hooks/useRequests.ts` | `M` | Service injection và session/read guards. |
| `frontend/src/pages/NewRequest.tsx` | `M` | Import/use injected service và session guard trong submit/catch/finally; không lấy các hunks nội dung UI/placeholder từ baseline. |
| `frontend/src/pages/RequestDetail.tsx` | `M` | Injected service, session guard và functional confirmation baseline; note-validation/focus/copy layout có sẵn cần review riêng. |
| `frontend/src/App.test.tsx` | `??` | Storage/session/protected-route cases; mobile sidebar/focus cases đi cùng baseline responsive riêng. |

## PREREQUISITE — 5 files

| File | Git | Lý do / ranh giới chọn |
| --- | --- | --- |
| `frontend/src/domain/request.test.ts` | `M` | Tests cho MOCK confirmation baseline và copy scope có sẵn. |
| `frontend/src/domain/request.ts` | `M` | MOCK resolved/confirmed và confirm ActionId đã có local; HEAD chưa chứa chúng. Cần functional baseline, không tự mở nghiệp vụ/permission LIVE. |
| `frontend/src/fixtures/requests.ts` | `M` | Fixtures doanh nghiệp/MOCK hiện hành; review baseline riêng cho status/persona/copy, giữ source local. |
| `frontend/src/services/MockRequestService.ts` | `M` | MOCK resolve/confirm semantics có sẵn; không viết lại hoặc coi là Backend contract. |
| `frontend/src/services/services.test.ts` | `M` | Regression cho MOCK functional baseline có sẵn; không tự đưa các phần khác vào adapter commit. |

## HOLD — 31 files

| File | Git | Lý do / ranh giới chọn |
| --- | --- | --- |
| `docs/evidence/issue39-portal/01-session.png` | `M` | Ảnh modified trước lượt này, chưa xác minh provenance cho MVP commit; bytes giữ nguyên. |
| `docs/evidence/issue39-portal/09-dashboard-desktop.png` | `M` | Ảnh modified trước lượt này, chưa xác minh provenance cho MVP commit; bytes giữ nguyên. |
| `frontend/DEMO_GUIDE.md` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/components/ui.tsx` | `M` | Responsive/validation/theme/UI baseline đã có; không đưa vào PR tích hợp MVP. |
| `frontend/src/pages/Auth.test.tsx` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/pages/Auth.tsx` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/pages/Dashboard.tsx` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/pages/NewRequest.test.tsx` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/pages/Requests.tsx` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/pages/System.tsx` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/src/styles.css` | `M` | Responsive/validation/theme/UI baseline đã có; không đưa vào PR tích hợp MVP. |
| `frontend/src/university-theme.css` | `M` | Responsive/validation/theme/UI baseline đã có; không đưa vào PR tích hợp MVP. |
| `frontend/tests/auth.spec.ts` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `frontend/tests/portal.spec.ts` | `M` | Copy/auth-preview/dashboard/legacy UI hoặc evidence tests có sẵn; bảo toàn, review baseline riêng. |
| `AGENTS.md` | `??` | Scope instructions; bảo toàn, review/commit governance riêng nếu được yêu cầu. |
| `docs/application/issue39-2a-alignment.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-application-completion-plan.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-decision-pack-for-issue53.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-final-source-review.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-gap-analysis.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-golden-paths-implementation-plan.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-it-support-rename-report.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-p1-01-integration-plan.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-p1-02-screenshot-report.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-p1-03-storage-fix-report.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-responsive-fix-report.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `docs/application/issue39-responsive-ui-review.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `frontend/REDESIGN_REVIEW.md` | `??` | Checkpoint/analysis lịch sử có sẵn; không trộn vào PR wiring/session. |
| `frontend/src/pages/RequestDetail.test.tsx` | `??` | Responsive/validation/theme/UI baseline đã có; không đưa vào PR tích hợp MVP. |
| `frontend/tests/responsive.spec.ts` | `??` | Responsive/validation/theme/UI baseline đã có; không đưa vào PR tích hợp MVP. |
| `frontend/tests/zoom.spec.ts` | `??` | Responsive/validation/theme/UI baseline đã có; không đưa vào PR tích hợp MVP. |

## Đối chiếu tổng

- 82 dirty files: 25 tracked modified, 57 untracked; staged 0.
- Disposition: FULL 39; HUNKS 7; PREREQUISITE 5; HOLD 31.
- Trong lượt này chỉ đổi 10 file có sẵn và tạo 4 file; những file Application khác trong manifest là công việc local đã có, không nhận thành implementation mới.
- Không có file Backend/Gateway/Identity/PostgreSQL trong danh sách. Không xóa/reset bất kỳ HOLD hoặc PREREQUISITE file nào.
