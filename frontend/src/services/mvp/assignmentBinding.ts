import type { AssignmentFormProps } from "../../components/AssignmentForm";
import type { HelpdeskAssignmentService } from "./HelpdeskRequestService";
import type { HelpdeskPortalRequestPresentation } from "./helpdeskPresentation";

/**
 * Prepare the standalone form only from authorized Detail. Caller must keep the
 * form unmounted when this returns null. Eligibility never grants permission.
 * APP-14 mounts this through its separate MVP model, never legacy MOCK data.
 */
export function helpdeskAssignmentFormProps(
  detail: HelpdeskPortalRequestPresentation,
  service: HelpdeskAssignmentService,
  onAuthoritativeDetail: (detail: HelpdeskPortalRequestPresentation) => void,
  isCurrent: () => boolean = () => true,
): AssignmentFormProps | null {
  if (!detail.allowedActions.includes("assign")) return null;

  const eligible = detail.eligibleAssignees;
  const version = detail.assignmentVersion;
  const valid =
    detail.state === "TRIAGED" &&
    Number.isSafeInteger(version) &&
    version >= 0 &&
    eligible != null &&
    eligible.items.length > 0 &&
    eligible.items.every(
      (item) =>
        typeof item.id.value === "string" &&
        item.id.value.trim().length > 0 &&
        item.id.value.length <= 1024,
    );

  return {
    currentAssignee: detail.currentAssignee,
    eligibleAssignees: eligible ?? undefined,
    assignmentPermission: "allowed",
    submission: valid
      ? {
          status: "ready",
          submitAssignment: async (selectedId) => {
            // Membership is checked again; never serialize a name or another token.
            const selected = eligible.items.find(
              (item) => item.id === selectedId,
            );
            if (!selected || typeof selected.id.value !== "string") {
              throw new RangeError(
                "Assignee was not supplied by authorized Detail.",
              );
            }
            await service.assign(detail.id, {
              assigneeId: selected.id.value,
              expectedAssignmentVersion: version,
            });
            // A confirmed write is not undone by leaving the screen/session.
            // Do not start another protected read for the obsolete UI.
            if (!isCurrent()) return;
            // Mutation responses omit names/eligibility at this Backend HEAD.
            // Reload Detail after confirmed success; no optimistic local updates.
            onAuthoritativeDetail(await service.detail(detail.id));
          },
        }
      : {
          status: "blocked",
          reason:
            "Chưa có dữ liệu phân công hợp lệ. Tải lại chi tiết trước khi tiếp tục.",
        },
  };
}
