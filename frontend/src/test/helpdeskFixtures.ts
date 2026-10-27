import type { HelpdeskRequest } from "../services/mvp/http/types";

// Test-only Backend-shaped records. Never imported by the production graph.
export const helpdeskId = "11111111-1111-4111-8111-111111111111";
export const helpdeskCursor = "22222222-2222-4222-8222-222222222222";

export function helpdeskWire(
  overrides: Partial<HelpdeskRequest> = {},
): HelpdeskRequest {
  return {
    id: helpdeskId,
    title: "Không thể lưu báo cáo",
    description: "Phần mềm nội bộ báo lỗi khi lưu báo cáo công việc.",
    requestType: "IT_HELPDESK",
    workflowInstanceId: "wf_test-only",
    state: "TRIAGED",
    version: 17,
    assignmentVersion: 0,
    requesterId: "test-requester-subject",
    assigneeId: "test-worker-subject",
    resolution: null,
    allowedActions: ["assign"],
    ...overrides,
  };
}
