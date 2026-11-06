<!--
SPDX-FileCopyrightText: 2026 TRICORE contributors
SPDX-License-Identifier: Apache-2.0
-->

# Independent Design + Code Review — DX-OS Portal V4

## Cần kiểm tra

Repo: `lexuanbao258-collab/dxos-open-core`, nguồn đối chiếu `main@0e789cf` (04/11/2026). Review candidate gồm React route riêng và HTML preview. Chưa push, chưa merge.

1. So sánh V4 với frontend thật (`App.tsx`, `Dashboard.tsx`, `Requests.tsx`, `NewRequest.tsx`, `RequestDetail.tsx`, `System.tsx`, `Auth.tsx`, `styles.css`, `university-theme.css`), đánh giá bố cục, typography, hierarchy, bảng 8 mẫu, mobile 390.
2. Build TS strict của `src/design-v4/PortalV4.tsx` trong repo thật. Không chấp nhận bằng chứng chỉ transpile syntax làm TS build PASS.
3. Test responsive 320/390/768/1024/1440, không scroll ngang, nút ≥44px theo design target, focus keyboard, overflow, contrast.
4. Check `role` demo không cấp quyền, `allowedActions` không được bịa, unknown write không auto retry, no live API, mock status dễ thấy.
5. Test thao tác lọc, đọc chi tiết, J/K/E/Enter/Esc, session switch, biểu mẫu và `role=alertdialog`.
6. Với `integration-local`, khẳng định route preview không làm rò rỉ sang runtime ngoài ý muốn và không làm đổi hiệu năng/giới hạn nghiệp vụ.
7. Các blocker issue #22/#23/#24/#25/#26/#37/PR #63 là mốc trong handoff, phải so với `main` trước khi xuất bản.

## Kết luận yêu cầu

Trả verdict `APPROVE DESIGN PREVIEW` hoặc `CHANGES REQUIRED`, tách lỗi code P0/P1/P2, vấn đề chủ quan có lý do, và điều kiện bắt buộc trước merge. Không yêu cầu tự merge/deploy.
