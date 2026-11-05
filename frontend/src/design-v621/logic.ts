// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import {
  PREVIEW_VIEWER,
  SERVICES,
  type ServiceId,
  STEPS,
  STEP_LABELS,
  type ListFilters,
  type ListRoute,
  type ListTab,
  type PreviewTicket,
  type Step,
} from "./model";

export const emptyFilters = (): ListFilters => ({
  query: "",
  service: "",
  status: "",
  priority: "",
});

export const stepLabel = (s: Step): string => STEP_LABELS[s];

/** Sortable timestamp from "HH:MM DD/MM/YYYY" or "DD/MM/YYYY". */
export function dateKey(t: string): number {
  const m = t.match(/(?:(\d\d?):(\d\d?)\s+)?(\d\d?)\/(\d\d?)\/(\d{4})/);
  return m ? Date.UTC(+m[5], +m[4] - 1, +m[3], +(m[1] ?? 0), +(m[2] ?? 0)) : 0;
}

const scope = (tickets: readonly PreviewTicket[], route: ListRoute) =>
  tickets.filter((r) => route === "queue" || r.requester === PREVIEW_VIEWER);

export function numbers(tickets: readonly PreviewTicket[]) {
  return {
    all: tickets.length,
    open: tickets.filter((r) => r.status !== "CONFIRMED").length,
    submitted: tickets.filter((r) => r.status === "SUBMITTED").length,
    processing: tickets.filter((r) => r.status === "IN_PROGRESS").length,
    high: tickets.filter((r) => r.priority === "Cao").length,
  };
}

/** Ticket count per workflow step; every step is present (0 when empty). */
export function stepCounts(
  tickets: readonly PreviewTicket[],
): Record<Step, number> {
  const out = Object.fromEntries(STEPS.map((s) => [s, 0])) as Record<
    Step,
    number
  >;
  for (const t of tickets) out[t.status] += 1;
  return out;
}

export function serviceCounts(
  tickets: readonly PreviewTicket[],
): Record<ServiceId, number> {
  const out = Object.fromEntries(
    (Object.keys(SERVICES) as ServiceId[]).map((id) => [id, 0]),
  ) as Record<ServiceId, number>;
  for (const t of tickets) out[t.service] += 1;
  return out;
}

export function counters(tickets: readonly PreviewTicket[], route: ListRoute) {
  const items = scope(tickets, route);
  return {
    all: items.length,
    open: items.filter((r) => r.status !== "CONFIRMED").length,
    closed: items.filter((r) => r.status === "CONFIRMED").length,
    processing: items.filter((r) => r.status === "IN_PROGRESS").length,
  };
}

export function selectRows(
  tickets: readonly PreviewTicket[],
  route: ListRoute,
  tab: ListTab,
  f: ListFilters,
): PreviewTicket[] {
  let rows = scope(tickets, route).filter((r) =>
    tab === "open"
      ? r.status !== "CONFIRMED"
      : tab === "closed"
        ? r.status === "CONFIRMED"
        : tab === "processing"
          ? r.status === "IN_PROGRESS"
          : true,
  );
  if (f.service) rows = rows.filter((r) => r.service === f.service);
  if (f.status) rows = rows.filter((r) => r.status === f.status);
  if (f.priority) rows = rows.filter((r) => r.priority === f.priority);
  const q = f.query.trim().toLocaleLowerCase("vi");
  if (q) {
    rows = rows.filter((r) =>
      [r.id, r.title, r.requester, SERVICES[r.service]].some((s) =>
        s.toLocaleLowerCase("vi").includes(q),
      ),
    );
  }
  return rows.sort(
    route === "queue"
      ? (a, b) =>
          (a.priority === "Cao" ? 0 : 1) - (b.priority === "Cao" ? 0 : 1) ||
          dateKey(a.updated) - dateKey(b.updated)
      : (a, b) => dateKey(b.updated) - dateKey(a.updated),
  );
}

export const activeFilterCount = (f: ListFilters): number =>
  [f.service, f.status, f.priority].filter(Boolean).length;

export function nextStepNote(status: Step): string {
  return {
    SUBMITTED: "Đang chờ tiếp nhận yêu cầu.",
    TRIAGED: "Đã tiếp nhận; chờ bắt đầu xử lý.",
    IN_PROGRESS: "Đang được xử lý; bước tiếp theo là ghi nhận kết quả.",
    RESOLVED: "Chờ người gửi xác nhận kết quả.",
    CONFIRMED: "Yêu cầu đã được xác nhận hoàn thành.",
  }[status];
}

export function auditLabel(raw: string): string {
  let out = raw;
  for (const s of STEPS) out = out.replaceAll(s, STEP_LABELS[s]);
  return out;
}

export interface CreateFormValues {
  service: string;
  title: string;
  description: string;
  location: string;
}
export type CreateErrors = Partial<
  Record<"title" | "description" | "location", string>
>;

export function validateCreate(v: CreateFormValues): CreateErrors {
  const errors: CreateErrors = {};
  if (v.title.trim().length < 5) errors.title = "Tiêu đề cần ít nhất 5 ký tự.";
  if (v.description.trim().length < 15)
    errors.description = "Mô tả cần ít nhất 15 ký tự.";
  if (v.service === "facility.v1" && v.location.trim().length < 2)
    errors.location = "Cần nhập vị trí khi yêu cầu Cơ sở vật chất.";
  return errors;
}
