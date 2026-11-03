import { afterEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import type { ServiceRequest } from "../../domain/request";
import type { MvpRequestPort } from "./port";
import {
  mapMvpEligibleAssignees,
  mapMvpListPresentation,
  mapMvpRequestPresentation,
  type MvpPortalRequestPresentation,
} from "./presentation";
import { projectAuthorizedHistory } from "./projections";
import type {
  Assignee,
  AuthorizedHistory,
  EligibleAssignees,
  RequestSnapshot,
  WorkflowActionName,
} from "./types";

function snapshot(): RequestSnapshot {
  return {
    id: "mapping-request",
    details: {
      kind: "it",
      title: "Phần mềm nội bộ không lưu được báo cáo",
      description:
        "Dữ liệu normalized dành riêng cho unit test mapping Application.",
      priority: "high",
      location: "Văn phòng tầng 3",
    },
    workflow: {
      state: "RESOLVED",
      version: { scope: "workflow", value: { opaque: "workflow-revision" } },
    },
    businessVersion: { scope: "business", value: "business-revision" },
    allowedActions: [
      { name: "confirm", reference: { opaque: "action-reference" } },
    ],
    currentAssignee: {
      id: { scope: "assignee", value: { opaque: "assignee-reference" } },
      displayName: "Người xử lý được phép hiển thị",
    },
  };
}

afterEach(() => vi.restoreAllMocks());

describe("APP-10 normalized MVP presentation mapping", () => {
  it("maps only the shared display fields without adding server-owned identity", () => {
    const source = snapshot();
    const view = mapMvpRequestPresentation(source);
    expect(view).toMatchObject({ id: source.id, ...source.details });
    for (const key of [
      "requester",
      "department",
      "createdAt",
      "updatedAt",
      "actor",
      "role",
    ]) {
      expect(view).not.toHaveProperty(key);
    }
    expectTypeOf<MvpPortalRequestPresentation>().not.toExtend<ServiceRequest>();
  });

  it("retains requester, department and timestamps only when actually supplied", () => {
    const source = snapshot();
    const details = {
      ...source.details,
      requester: "Tên người gửi từ projection được phép xem",
      department: "Phòng vận hành",
      createdAt: "2026-10-25T01:00:00Z",
      updatedAt: "2026-10-25T02:00:00Z",
    };
    const view = mapMvpRequestPresentation({ ...source, details });
    expect(view).toMatchObject(details);
    expect(view.createdAt).toBe(details.createdAt);
    expect(view.updatedAt).toBe(details.updatedAt);
  });

  it.each(["RESOLVED", "CONFIRMED", "resolved", "UNRECOGNIZED_BACKEND_STATE"])(
    "preserves state %s without inventing a MOCK status or terminal classification",
    (state) => {
      const source = snapshot();
      const view = mapMvpRequestPresentation({
        ...source,
        workflow: { ...source.workflow, state },
      });
      expect(view.state).toBe(state);
      expect(view).not.toHaveProperty("status");
      expect(view).not.toHaveProperty("isTerminal");
      expect(view).not.toHaveProperty("closed");
    },
  );

  it("preserves separate authoritative Workflow/business tokens, including opaque values", () => {
    const source = snapshot();
    const view = mapMvpRequestPresentation(source);
    expect(view.workflowVersion).toBe(source.workflow.version);
    expect(view.workflowVersion.value).toBe(source.workflow.version.value);
    expect(view.businessVersion).toBe(source.businessVersion);
    expect(view).not.toHaveProperty("version");
  });

  it("does not manufacture a business version when only Workflow version is supplied", () => {
    const { businessVersion: _omitted, ...source } = snapshot();
    expect(mapMvpRequestPresentation(source)).not.toHaveProperty(
      "businessVersion",
    );
  });

  it.each<WorkflowActionName>(["triage", "start_work", "resolve", "confirm"])(
    "preserves supplied action %s and its reference without creating a legacy alias",
    (name) => {
      const source = snapshot();
      const reference = { opaque: `test-only-${name}` };
      const view = mapMvpRequestPresentation({
        ...source,
        allowedActions: [{ name, reference }],
      });
      expect(view.allowedActions).toEqual([{ name, reference }]);
      expect(view.allowedActions[0].reference).toBe(reference);
      expect(view.allowedActions[0]).not.toHaveProperty("id");
      expect(view.allowedActions[0]).not.toHaveProperty("label");
    },
  );

  it("does not derive capabilities from role/state or add assignment as a Workflow action", () => {
    const source = { ...snapshot(), role: "processor", allowedActions: [] };
    const view = mapMvpRequestPresentation(source);
    expect(view.allowedActions).toEqual([]);
    expect(view).not.toHaveProperty("role");
    expect(view).not.toHaveProperty("canAssign");
    expect(view).not.toHaveProperty("actions");
  });

  it("keeps assignee reference separate from display name", () => {
    const source = snapshot();
    const view = mapMvpRequestPresentation(source);
    expect(view.currentAssignee?.id).toBe(source.currentAssignee?.id);
    expect(view.currentAssignee?.displayName).toBe(
      source.currentAssignee?.displayName,
    );
    expect(view.assigneeDisplayName).toBe(source.currentAssignee?.displayName);
    expect(view.currentAssignee).not.toBe(source.currentAssignee);
    expect(view.currentAssignee?.id.value).not.toBe(view.assigneeDisplayName);
    expect(view).not.toHaveProperty("assignee");
  });

  it("preserves null assignment without synthesizing a group or display placeholder", () => {
    const view = mapMvpRequestPresentation({
      ...snapshot(),
      currentAssignee: null,
    });
    expect(view.currentAssignee).toBeNull();
    expect(view).not.toHaveProperty("assigneeDisplayName");
    expect(view).not.toHaveProperty("eligibleAssignees");
  });

  it("does not turn absent history into an empty, complete timeline", () => {
    const view = mapMvpRequestPresentation(snapshot());
    expect(view).not.toHaveProperty("history");
    expect(view).not.toHaveProperty("timeline");
  });

  it("preserves authorized redaction, event order and history completeness", () => {
    const history: AuthorizedHistory = {
      completeness: "partial",
      events: [
        { id: "visible-2", at: "2026-10-25T02:00:00Z", title: "Kết quả xử lý" },
        {
          id: "visible-1",
          at: "2026-10-25T01:00:00Z",
          title: "Tiếp nhận",
          actor: "Tên đã được phép xem",
        },
        {
          id: "visible-note",
          at: "2026-10-25T00:00:00Z",
          title: "Ghi chú",
          note: "Nội dung đã lọc quyền",
        },
      ],
    };
    const view = mapMvpRequestPresentation({ ...snapshot(), history });
    expect(view.history).toEqual(history);
    expect(view.history).not.toBe(history);
    expect(view.history?.events[0]).not.toHaveProperty("actor");
    expect(view.history?.events[0]).not.toHaveProperty("note");
    expect(view.history?.events[1]).not.toHaveProperty("note");
    expect(view.history?.events[2]).not.toHaveProperty("actor");
  });

  it.each(["complete", "partial", "unknown"] as const)(
    "preserves empty %s list/history/eligibility without inventing totals or authority",
    (completeness) => {
      expect(mapMvpListPresentation({ items: [], completeness })).toEqual({
        items: [],
        completeness,
      });
      const view = mapMvpRequestPresentation(
        { ...snapshot(), history: { events: [], completeness } },
        { eligibleAssignees: { items: [], completeness } },
      );
      expect(view.history).toEqual({ events: [], completeness });
      expect(view.eligibleAssignees).toEqual({ items: [], completeness });
      expect(view).not.toHaveProperty("canAssign");
      expect(view).not.toHaveProperty("total");
    },
  );

  it("uses explicitly provided authorized-detail eligibility without treating it as assignment permission", () => {
    const assignee: Assignee = {
      id: { scope: "assignee", value: "test-id" },
      displayName: "Người xử lý hợp lệ",
    };
    const eligible: EligibleAssignees = {
      items: [assignee],
      completeness: "partial",
    };
    const view = mapMvpRequestPresentation(snapshot(), {
      eligibleAssignees: eligible,
    });
    expect(view.eligibleAssignees).toEqual(eligible);
    expect(view.eligibleAssignees?.items[0].id).toBe(assignee.id);
    expect(view.eligibleAssignees?.items).not.toBe(eligible.items);
    expect(mapMvpEligibleAssignees(eligible)).toEqual(eligible);
    expect(view).not.toHaveProperty("canAssign");
    expect(view.allowedActions.map((action) => action.name)).toEqual([
      "confirm",
    ]);
  });

  it("copies list items without mutating snapshots or claiming a complete collection", () => {
    const source = snapshot();
    const before = structuredClone(source);
    const view = mapMvpListPresentation({
      items: [source],
      completeness: "partial",
    });
    expect(view.items[0]).toEqual(mapMvpRequestPresentation(source));
    expect(view.items[0].allowedActions).not.toBe(source.allowedActions);
    expect(view.completeness).toBe("partial");
    expect(view).not.toHaveProperty("total");
    expect(source).toEqual(before);
  });
});

describe("APP-10 fake MvpRequestPort read projections", () => {
  it("maps fake detail/list/history without HTTP, automatic enrichment fetches or writes", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => {
      throw new Error("Unexpected LIVE fetch");
    });
    const xhrSpy = vi
      .spyOn(XMLHttpRequest.prototype, "send")
      .mockImplementation(() => {
        throw new Error("Unexpected LIVE XMLHttpRequest");
      });
    const detail = snapshot();
    const history: AuthorizedHistory = {
      events: [
        {
          id: "visible",
          at: "2026-10-25T01:00:00Z",
          title: "Sự kiện được phép xem",
        },
      ],
      completeness: "unknown",
    };
    const eligible: EligibleAssignees = { items: [], completeness: "unknown" };
    const port = {
      create: vi.fn<MvpRequestPort["create"]>(),
      list: vi
        .fn<MvpRequestPort["list"]>()
        .mockResolvedValue({ items: [detail], completeness: "unknown" }),
      detail: vi.fn<MvpRequestPort["detail"]>().mockResolvedValue(detail),
      workflowAction: vi.fn<MvpRequestPort["workflowAction"]>(),
      assignment: vi.fn<MvpRequestPort["assignment"]>(),
      eligibleAssignees: vi.fn<MvpRequestPort["eligibleAssignees"]>(),
      history: vi.fn<MvpRequestPort["history"]>().mockResolvedValue(history),
    } satisfies MvpRequestPort;
    const view = mapMvpRequestPresentation(await port.detail(detail.id));
    expect(view.state).toBe(detail.workflow.state);
    expect(view).not.toHaveProperty("history");
    expect(view).not.toHaveProperty("eligibleAssignees");
    // Explicit normalized authorized-detail enrichment, not a guessed wire field.
    const enriched = mapMvpRequestPresentation(detail, {
      eligibleAssignees: eligible,
    });
    expect(enriched.eligibleAssignees).toEqual(eligible);
    const list = mapMvpListPresentation(await port.list());
    expect(list.items[0]).toEqual(view);
    expect(list.completeness).toBe("unknown");
    const projectedHistory = projectAuthorizedHistory(
      await port.history(detail.id),
    );
    expect(projectedHistory.events[0]).not.toHaveProperty("actor");
    expect(projectedHistory.events[0]).not.toHaveProperty("note");
    expect(port.detail).toHaveBeenCalledTimes(1);
    expect(port.list).toHaveBeenCalledTimes(1);
    expect(port.history).toHaveBeenCalledTimes(1);
    expect(port.eligibleAssignees).not.toHaveBeenCalled();
    expect(port.create).not.toHaveBeenCalled();
    expect(port.workflowAction).not.toHaveBeenCalled();
    expect(port.assignment).not.toHaveBeenCalled();
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
  });
});
