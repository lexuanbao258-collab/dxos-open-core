// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import type { PreviewAuditEvent, PreviewTicket } from "./model";

// Verbatim from OpenTrix_V6_2_1_Review/index.html (8 tickets, 3 audit events).
// Preview-only; do not edit to "improve" data and do not reuse in services.
type Row = Omit<PreviewTicket, "allowedActions">;
const rows: Row[] = [
  {
    id: "DX-1048",
    service: "helpdesk.v1",
    title: "Cấp quyền truy cập kho mã nguồn dự án",
    status: "SUBMITTED",
    priority: "Cao",
    requester: "Ngọc Ánh",
    updated: "09:15 20/10/2026",
  },
  {
    id: "DX-1047",
    service: "facility.v1",
    title: "Điều hòa phòng họp Lotus không làm mát",
    status: "IN_PROGRESS",
    priority: "Cao",
    requester: "Minh Anh",
    updated: "11:16 20/10/2026",
  },
  {
    id: "DX-1046",
    service: "helpdesk.v1",
    title: "Cài đặt môi trường phát triển cho laptop mới",
    status: "IN_PROGRESS",
    priority: "Bình thường",
    requester: "Ngọc Ánh",
    updated: "11:17 19/10/2026",
    version: 2,
    assignee: "Nhóm IT Support",
  },
  {
    id: "DX-1045",
    service: "facility.v1",
    title: "Chuẩn bị phòng họp cho buổi demo OLP",
    status: "TRIAGED",
    priority: "Bình thường",
    requester: "Ngọc Ánh",
    updated: "10:18 19/10/2026",
  },
  {
    id: "DX-1044",
    service: "helpdesk.v1",
    title: "Không kết nối được mạng Wi-Fi nội bộ",
    status: "CONFIRMED",
    priority: "Bình thường",
    requester: "Hoàng Nam",
    updated: "12:19 18/10/2026",
  },
  {
    id: "DX-1043",
    service: "facility.v1",
    title: "Thay đèn khu vực làm việc tầng 3",
    status: "SUBMITTED",
    priority: "Bình thường",
    requester: "Ngọc Ánh",
    updated: "09:20 18/10/2026",
  },
  {
    id: "DX-1042",
    service: "helpdesk.v1",
    title: "Thiết lập tài khoản công cụ thiết kế",
    status: "CONFIRMED",
    priority: "Bình thường",
    requester: "Ngọc Ánh",
    updated: "17/10/2026",
  },
  {
    id: "DX-1040",
    service: "helpdesk.v1",
    title: "Kiểm tra máy in phòng hành chính",
    status: "SUBMITTED",
    priority: "Bình thường",
    requester: "Thu Hà",
    updated: "16/10/2026",
  },
];

export const PREVIEW_TICKETS: readonly PreviewTicket[] = Object.freeze(
  rows.map((r) => Object.freeze({ ...r, allowedActions: Object.freeze([]) })),
);

/** Audit trail exists only for DX-1046 in the design candidate. */
export const AUDIT_TICKET_ID = "DX-1046";
export const PREVIEW_AUDIT: readonly PreviewAuditEvent[] = Object.freeze([
  { time: "09:17 19/10/2026", what: "Khởi tạo v0", actor: "Ngọc Ánh" },
  {
    time: "10:17 19/10/2026",
    what: "SUBMITTED → TRIAGED v1",
    actor: "Lê Xuân Bảo",
  },
  {
    time: "11:17 19/10/2026",
    what: "TRIAGED → IN_PROGRESS v2",
    actor: "Nhóm IT Support",
  },
]);
