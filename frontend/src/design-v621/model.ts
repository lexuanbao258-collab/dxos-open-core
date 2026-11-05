// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

/**
 * OpenTrix V6.2.1 preview view model. Deliberately independent from
 * `domain/request.ts` and `services/**`: the preview workflow vocabulary is a
 * design candidate and is NOT mapped onto the existing business states.
 */
export const STEPS = [
  "SUBMITTED",
  "TRIAGED",
  "IN_PROGRESS",
  "RESOLVED",
  "CONFIRMED",
] as const;
export type Step = (typeof STEPS)[number];

export const STEP_LABELS: Record<Step, string> = {
  SUBMITTED: "Đã gửi",
  TRIAGED: "Đã tiếp nhận",
  IN_PROGRESS: "Đang xử lý",
  RESOLVED: "Đã giải quyết",
  CONFIRMED: "Đã xác nhận",
};

export const SERVICES = {
  "helpdesk.v1": "Hỗ trợ CNTT",
  "facility.v1": "Cơ sở vật chất",
} as const;
export type ServiceId = keyof typeof SERVICES;

export type Priority = "Cao" | "Bình thường";

export interface PreviewTicket {
  readonly id: string;
  readonly service: ServiceId;
  readonly title: string;
  readonly status: Step;
  readonly priority: Priority;
  readonly requester: string;
  readonly updated: string;
  readonly version?: number;
  readonly assignee?: string;
  /** Always empty in the preview: never derived from role, status or UI. */
  readonly allowedActions: readonly string[];
}

export interface PreviewAuditEvent {
  readonly time: string;
  readonly what: string;
  readonly actor: string;
}

export type QaScenario =
  "normal" | "loading" | "empty" | "error" | "success" | "unknown";

export type ListRoute = "mine" | "queue";
export type ListTab = "open" | "closed" | "processing" | "all";

export interface ListFilters {
  query: string;
  service: "" | ServiceId;
  status: "" | Step;
  priority: "" | Priority;
}

/** The only requester whose tickets appear under "Yêu cầu của tôi". */
export const PREVIEW_VIEWER = "Ngọc Ánh";

export const INTEGRATION_ROWS: ReadonlyArray<
  readonly [string, string, string]
> = [
  ["Identity / SSO", "#24", "Chờ OIDC Authorization Code + PKCE"],
  ["API Gateway", "#22 / #23", "Chưa nghiệm thu kết nối API có kiểm quyền"],
  ["Workflow Engine", "#22 / #23 / #24", "Chưa có bằng chứng LIVE đầy đủ"],
  ["Data Management", "#25 / #26", "Chưa xác nhận năng lực dữ liệu LIVE"],
  ["Tệp đính kèm", "#26", "Chờ Storage API có kiểm quyền"],
  ["OpenAPI / DTO mapping", "#37", "Chưa duyệt hợp đồng và mapping tích hợp"],
  ["HTTP adapter (9 route)", "PR #63", "Có mã, mới thử với fake transport"],
];
