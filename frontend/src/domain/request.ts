export type RequestKind = "it" | "facility";
export type RequestStatus =
  "pending" | "approved" | "processing" | "completed" | "rejected";
export type DemoRole = "employee" | "processor";
export type ActionId = "approve" | "reject" | "start" | "complete";
export type Fault = "" | "network" | "401" | "403" | "404" | "unknown";
export interface TimelineEvent {
  id: string;
  at: string;
  actor: string;
  title: string;
  note: string;
}
export interface ServiceRequest {
  id: string;
  title: string;
  description: string;
  kind: RequestKind;
  status: RequestStatus;
  priority: "normal" | "high" | "urgent";
  requester: string;
  department: string;
  location: string;
  assignee: string;
  createdAt: string;
  updatedAt: string;
  version: number;
  timeline: TimelineEvent[];
}
export interface CreateRequestInput {
  kind: RequestKind;
  title: string;
  description: string;
  priority: ServiceRequest["priority"];
  location: string;
}
export interface RequestAction {
  id: ActionId;
  label: string;
  tone?: "danger";
}
export const kindLabels: Record<RequestKind, string> = {
  it: "Hỗ trợ CNTT trường học",
  facility: "Cơ sở vật chất",
};
export const statusLabels: Record<RequestStatus, string> = {
  pending: "Chờ phê duyệt",
  approved: "Đã phê duyệt",
  processing: "Đang xử lý",
  completed: "Hoàn thành",
  rejected: "Từ chối",
};
export const priorityLabels = {
  normal: "Bình thường",
  high: "Cao",
  urgent: "Khẩn cấp",
};
export function validateRequest(input: CreateRequestInput) {
  const errors: Partial<Record<keyof CreateRequestInput, string>> = {};
  if (!["it", "facility"].includes(input.kind))
    errors.kind = "Chọn một dịch vụ hợp lệ.";
  if (input.title.trim().length < 8 || input.title.trim().length > 120)
    errors.title = "Tiêu đề cần từ 8 đến 120 ký tự.";
  if (
    input.description.trim().length < 20 ||
    input.description.trim().length > 2000
  )
    errors.description = "Mô tả cần từ 20 đến 2.000 ký tự.";
  if (!input.location.trim() || input.location.trim().length > 100)
    errors.location = "Nhập vị trí (tối đa 100 ký tự).";
  if (!["normal", "high", "urgent"].includes(input.priority))
    errors.priority = "Chọn mức ưu tiên hợp lệ.";
  return errors;
}
export function filterRequests(
  items: ServiceRequest[],
  search: string,
  kind = "",
  status = "",
) {
  const normalize = (s: string) =>
    s
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/đ/g, "d");
  const query = normalize(search.trim());
  return items.filter(
    (r) =>
      (!kind || r.kind === kind) &&
      (!status || r.status === status) &&
      normalize(`${r.id} ${r.title} ${r.requester}`).includes(query),
  );
}
export const formatDate = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
