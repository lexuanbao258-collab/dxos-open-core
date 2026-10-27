import { afterEach, describe, expect, expectTypeOf, it, vi } from "vitest";
import {
  createAssignmentCommand,
  createRequestInput,
  createWorkflowActionCommand,
} from "./commands";
import {
  errorFromHttpStatus,
  executeWriteOnce,
  MvpApplicationError,
  networkFailure,
  type MvpErrorCode,
} from "./errors";
import type { MvpRequestPort } from "./port";
import { projectAuthorizedHistory } from "./projections";
import type {
  AssignmentCommand,
  AssigneeId,
  AuthorizedHistory,
  BusinessVersion,
  CreateRequestInput,
  EligibleAssignees,
  RequestSnapshot,
  WorkflowActionCommand,
  WorkflowActionName,
  WorkflowVersion,
} from "./types";

const form: CreateRequestInput = {
  kind: "it",
  title: "Phần mềm nội bộ báo lỗi",
  description: "Không thể lưu báo cáo công việc trong phần mềm nội bộ.",
  priority: "normal",
  location: "Văn phòng tầng 3",
};

function snapshot(): RequestSnapshot {
  return {
    id: "test-request",
    details: form,
    workflow: {
      state: "test-only-authoritative-state",
      version: { scope: "workflow", value: { opaque: "workflow-revision" } },
    },
    businessVersion: { scope: "business", value: "business-revision" },
    allowedActions: [],
    currentAssignee: null,
  };
}

function fakePort(
  provided: RequestSnapshot & { history: AuthorizedHistory },
  eligible: EligibleAssignees,
): MvpRequestPort {
  return {
    create: vi.fn(async () => provided),
    list: vi.fn(async () => ({
      items: [provided],
      completeness: "unknown" as const,
    })),
    detail: vi.fn(async () => provided),
    workflowAction: vi.fn(async () => provided),
    assignment: vi.fn(async () => provided),
    eligibleAssignees: vi.fn(async () => eligible),
    history: vi.fn(async () => provided.history),
  };
}

afterEach(() => vi.restoreAllMocks());

describe("normalized Application commands", () => {
  it("allowlists form data without role, fake requester or server-owned fields", () => {
    const supplied = {
      ...form,
      role: "processor",
      requester: "Must not be sent",
      department: "Must not be sent",
      state: "Must not be sent",
      assignee: "Must not be used as an ID",
    };
    const input = createRequestInput(supplied);
    expect(input).toEqual(form);
    expect(input).not.toHaveProperty("role");
    expect(input).not.toHaveProperty("requester");
    expect(input).not.toBe(supplied);
    expect(supplied.role).toBe("processor");
  });

  it.each<WorkflowActionName>(["triage", "start_work", "resolve", "confirm"])(
    "selects %s only from supplied allowedActions and preserves its opaque reference",
    (name) => {
      const provided = snapshot();
      const reference = { opaque: `test-only-${name}` };
      const permitted = { ...provided, allowedActions: [{ name, reference }] };
      const command = createWorkflowActionCommand(permitted, name, undefined);
      expect(command.action).toEqual({ name, reference });
      expect(command.action.reference).toBe(reference);
      expect(() =>
        createWorkflowActionCommand(provided, name, undefined),
      ).toThrow(RangeError);
      expect(provided.allowedActions).toEqual([]);
      expect(permitted.workflow).toBe(provided.workflow);
    },
  );

  it("does not substitute another action or derive one from a supplied persona", () => {
    const provided = {
      ...snapshot(),
      role: "processor",
      allowedActions: [
        { name: "resolve" as const, reference: "test-reference" },
      ],
    };
    expect(() =>
      createWorkflowActionCommand(provided, "confirm", undefined),
    ).toThrow(RangeError);
  });

  it.each(["RESOLVED", "CONFIRMED", "test-only-unknown-state"])(
    "preserves authoritative %s and separate version values without a state transition",
    (state) => {
      const provided: RequestSnapshot = {
        ...snapshot(),
        workflow: { ...snapshot().workflow, state },
        allowedActions: [{ name: "confirm", reference: "test-only-reference" }],
      };
      const before = structuredClone(provided);
      const command = createWorkflowActionCommand(provided, "confirm", null);
      expect(command.observedVersions.workflow).toBe(provided.workflow.version);
      expect(command.observedVersions.business).toBe(provided.businessVersion);
      expect(command.observedVersions.workflow.value).toEqual({
        opaque: "workflow-revision",
      });
      expect(command.observedVersions.business?.value).toBe(
        "business-revision",
      );
      expect(provided).toEqual(before);
    },
  );

  it("keeps assignment separate from triage and uses an explicit assignee ID", () => {
    const provided: RequestSnapshot = {
      ...snapshot(),
      allowedActions: [{ name: "triage", reference: "test-only-reference" }],
    };
    const selected = {
      id: { scope: "assignee" as const, value: "test-assignee-42" },
      displayName: "Bộ phận CNTT",
    };
    const triage = createWorkflowActionCommand(provided, "triage", undefined);
    const assignment = createAssignmentCommand(
      provided,
      selected.id,
      undefined,
    );
    expect(triage.kind).toBe("workflow-action");
    expect(assignment.kind).toBe("assignment");
    expect(assignment.assigneeId).toBe(selected.id);
    expect(assignment.assigneeId.value).not.toBe(selected.displayName);
    expect(assignment).not.toHaveProperty("action");
    expect(triage).not.toHaveProperty("assigneeId");
    expect(provided.currentAssignee).toBeNull();
  });

  it("does not manufacture a business version from the Workflow version", () => {
    const { businessVersion: _omitted, ...provided } = snapshot();
    const command = createAssignmentCommand(
      provided,
      { scope: "assignee", value: "test-assignee" },
      undefined,
    );
    expect(command.observedVersions.workflow).toBe(provided.workflow.version);
    expect(command.observedVersions).not.toHaveProperty("business");
  });

  it("preserves explicit action input without inventing actor, note or requester", () => {
    const provided: RequestSnapshot = {
      ...snapshot(),
      allowedActions: [{ name: "resolve", reference: "test-only-reference" }],
    };
    const input = Object.freeze({ testOnly: "opaque, not a wire DTO" });
    const command = createWorkflowActionCommand(provided, "resolve", input);
    expect(command.input).toBe(input);
    expect(command).not.toHaveProperty("role");
    expect(command).not.toHaveProperty("requester");
    expect(command).not.toHaveProperty("note");
    expect(command).not.toHaveProperty("actor");
  });

  it("keeps versions, assignee identifiers and command categories distinct in TypeScript", () => {
    expectTypeOf<BusinessVersion>().not.toExtend<WorkflowVersion>();
    expectTypeOf<WorkflowVersion>().not.toExtend<BusinessVersion>();
    expectTypeOf<string>().not.toExtend<AssigneeId>();
    expectTypeOf<AssignmentCommand>().not.toExtend<WorkflowActionCommand>();
    expectTypeOf<WorkflowActionCommand>().not.toExtend<AssignmentCommand>();
    expectTypeOf<
      Parameters<MvpRequestPort["workflowAction"]>[0]
    >().toEqualTypeOf<WorkflowActionCommand>();
    expectTypeOf<
      Parameters<MvpRequestPort["assignment"]>[0]
    >().toEqualTypeOf<AssignmentCommand>();
    expectTypeOf<"assign" | "close">().not.toExtend<WorkflowActionName>();
  });
});

describe("authorized history projection", () => {
  it("preserves filtered fields, event order and supplied visibility without enrichment", () => {
    const provided: AuthorizedHistory = {
      completeness: "partial",
      events: [
        { id: "newer", at: "2026-10-24T09:00:00Z", title: "Đã cập nhật" },
        {
          id: "older",
          at: "2026-10-24T08:00:00Z",
          title: "Đã tiếp nhận",
          actor: "Bộ phận CNTT",
        },
        {
          id: "note-only",
          at: "2026-10-24T07:00:00Z",
          title: "Ghi chú được phép xem",
          note: "Nội dung đã được lọc quyền",
        },
      ],
    };
    const projected = projectAuthorizedHistory(provided);
    expect(projected).toEqual(provided);
    expect(projected.events).not.toBe(provided.events);
    expect(projected.events[0]).not.toBe(provided.events[0]);
    expect(projected.events[0]).not.toHaveProperty("actor");
    expect(projected.events[0]).not.toHaveProperty("note");
    expect(projected.events[1]).not.toHaveProperty("note");
    expect(projected.events[2]).not.toHaveProperty("actor");
  });

  it.each(["complete", "partial", "unknown"] as const)(
    "does not treat empty %s history as proof of complete visibility or recovery",
    (completeness) => {
      expect(projectAuthorizedHistory({ events: [], completeness })).toEqual({
        events: [],
        completeness,
      });
    },
  );
});

describe("Application errors and single write invocation", () => {
  it.each([
    [401, "UNAUTHENTICATED"],
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
  ] as const)(
    "classifies known HTTP %s as %s without assuming a JSON envelope",
    (status, code) => {
      expect(errorFromHttpStatus(status)).toMatchObject({ code });
    },
  );

  it.each<MvpErrorCode>([
    "UNAUTHENTICATED",
    "FORBIDDEN",
    "NOT_FOUND",
    "CONFLICT",
    "NETWORK_FAILURE",
    "COMMIT_OUTCOME_UNKNOWN",
  ])("does not retry a write after %s", async (code) => {
    const failure = new MvpApplicationError(code);
    const write = vi.fn(async () => {
      throw failure;
    });
    if (code === "NETWORK_FAILURE") {
      await expect(executeWriteOnce(write)).rejects.toMatchObject({
        code: "COMMIT_OUTCOME_UNKNOWN",
        cause: failure,
      });
    } else {
      await expect(executeWriteOnce(write)).rejects.toBe(failure);
    }
    expect(write).toHaveBeenCalledTimes(1);
  });

  it.each(["async rejection", "synchronous throw"])(
    "keeps an unclassified %s uncertain and never replays the invocation",
    async (failureMode) => {
      const cause = new TypeError("Response lost after a possible commit");
      const write = vi.fn((): Promise<never> => {
        if (failureMode === "synchronous throw") throw cause;
        return Promise.reject(cause);
      });
      await expect(executeWriteOnce(write)).rejects.toMatchObject({
        code: "COMMIT_OUTCOME_UNKNOWN",
        cause,
      });
      expect(write).toHaveBeenCalledTimes(1);
    },
  );

  it("does not expose raw error details in a user-facing message", () => {
    const cause = new Error("Sensitive server diagnostic");
    const error = networkFailure("write", cause);
    expect(error.cause).toBe(cause);
    expect(error.message).not.toContain(cause.message);
    expect(networkFailure("read", cause).code).toBe("NETWORK_FAILURE");
    expect(error.code).toBe("COMMIT_OUTCOME_UNKNOWN");
  });

  it("does not repeat a committed write after its response is lost", async () => {
    let committedWrites = 0;
    const write = vi.fn(async () => {
      committedWrites += 1;
      throw networkFailure("write", new Error("Response lost after commit"));
    });
    await expect(executeWriteOnce(write)).rejects.toMatchObject({
      code: "COMMIT_OUTCOME_UNKNOWN",
    });
    expect(committedWrites).toBe(1);
    expect(write).toHaveBeenCalledTimes(1);
  });

  it("returns an authoritative successful result once without modifying it", async () => {
    const provided = snapshot();
    const write = vi.fn(async () => provided);
    expect(await executeWriteOnce(write)).toBe(provided);
    expect(write).toHaveBeenCalledTimes(1);
  });
});

describe("unbound Application port", () => {
  it("exercises all seven operations with supplied data and no LIVE network calls", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => {
      throw new Error("Unexpected LIVE fetch");
    });
    const xhrSpy = vi
      .spyOn(XMLHttpRequest.prototype, "send")
      .mockImplementation(() => {
        throw new Error("Unexpected LIVE XMLHttpRequest");
      });
    vi.resetModules();
    const [commands, errors, projections] = await Promise.all([
      import("./commands"),
      import("./errors"),
      import("./projections"),
    ]);
    const assignee = {
      id: { scope: "assignee" as const, value: "test-assignee" },
      displayName: "Bộ phận CNTT",
    };
    const provided: RequestSnapshot & { history: AuthorizedHistory } = {
      ...snapshot(),
      allowedActions: [{ name: "triage", reference: "test-only-reference" }],
      currentAssignee: assignee,
      history: projections.projectAuthorizedHistory({
        completeness: "unknown",
        events: [
          {
            id: "visible-event",
            at: "2026-10-24T08:00:00Z",
            title: "Đã tiếp nhận",
          },
        ],
      }),
    };
    const eligible: EligibleAssignees = {
      items: [assignee],
      completeness: "partial",
    };
    const port = fakePort(provided, eligible);
    expect(
      await errors.executeWriteOnce(() =>
        port.create(commands.createRequestInput(form)),
      ),
    ).toBe(provided);
    expect((await port.list()).completeness).toBe("unknown");
    expect(await port.detail(provided.id)).toBe(provided);
    expect(
      await errors.executeWriteOnce(() =>
        port.workflowAction(
          commands.createWorkflowActionCommand(provided, "triage", undefined),
        ),
      ),
    ).toBe(provided);
    expect(
      await errors.executeWriteOnce(() =>
        port.assignment(
          commands.createAssignmentCommand(provided, assignee.id, undefined),
        ),
      ),
    ).toBe(provided);
    expect(await port.eligibleAssignees(provided.id)).toBe(eligible);
    expect((await port.history(provided.id)).events[0]).not.toHaveProperty(
      "actor",
    );
    expect((await port.history(provided.id)).events[0]).not.toHaveProperty(
      "note",
    );
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
  });
});
