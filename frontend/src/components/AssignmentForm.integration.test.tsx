import { useState } from "react";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { helpdeskId, helpdeskWire } from "../test/helpdeskFixtures";
import { helpdeskAssignmentFormProps } from "../services/mvp/assignmentBinding";
import { MvpApplicationError } from "../services/mvp/errors";
import {
  HelpdeskRequestService,
  type HelpdeskAssignmentService,
} from "../services/mvp/HelpdeskRequestService";
import {
  mapHelpdeskRequestPresentation,
  type HelpdeskPortalRequestPresentation,
} from "../services/mvp/helpdeskPresentation";
import { HelpdeskApiClient } from "../services/mvp/http/HelpdeskApiClient";
import type { HelpdeskHttpTransport } from "../services/mvp/http/transport";
import { AssignmentForm } from "./AssignmentForm";

function detail(overrides: Partial<HelpdeskPortalRequestPresentation> = {}) {
  return {
    ...mapHelpdeskRequestPresentation(
      helpdeskWire({
        requesterDisplayName: "Người gửi từ Backend",
        assigneeDisplayName: "Người xử lý hiện tại",
        eligibleAssignees: [{ id: "worker-2", displayName: "Người xử lý mới" }],
      }),
    ),
    ...overrides,
  };
}

function fakeService() {
  return {
    assign: vi
      .fn<HelpdeskAssignmentService["assign"]>()
      .mockResolvedValue(detail()),
    detail: vi
      .fn<HelpdeskAssignmentService["detail"]>()
      .mockResolvedValue(detail()),
  } satisfies HelpdeskAssignmentService;
}

// A test-only host. The production Portal remains bound to its MOCK service.
function Host({
  initial,
  service,
}: {
  initial: HelpdeskPortalRequestPresentation;
  service: HelpdeskAssignmentService;
}) {
  const [current, setCurrent] = useState(initial);
  const props = helpdeskAssignmentFormProps(current, service, setCurrent);
  return (
    <>
      <output data-testid="authoritative">
        {current.state}:{current.version}:{current.assignmentVersion}:
        {current.assigneeId}
      </output>
      {props && <AssignmentForm {...props} />}
    </>
  );
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

let assertNoLive: () => void;
beforeEach(() => {
  const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => {
    throw new Error("Unexpected LIVE fetch");
  });
  const xhrSpy = vi
    .spyOn(XMLHttpRequest.prototype, "send")
    .mockImplementation(() => {
      throw new Error("Unexpected LIVE XHR");
    });
  assertNoLive = () => {
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
  };
});
afterEach(() => {
  try {
    assertNoLive();
  } finally {
    vi.restoreAllMocks();
  }
});

describe("APP-11 standalone Application binding using fake service", () => {
  it.each(
    [[], ["triage"], ["start"], ["start_work"]].map((allowedActions) => ({
      allowedActions,
    })),
  )(
    "does not mount without allowedActions assign: $allowedActions, even with populated eligibility",
    ({ allowedActions }) => {
      const service = fakeService();
      render(<Host initial={detail({ allowedActions })} service={service} />);
      expect(screen.queryByRole("form")).not.toBeInTheDocument();
      expect(service.assign).not.toHaveBeenCalled();
      expect(service.detail).not.toHaveBeenCalled();
    },
  );

  it.each([
    { eligibleAssignees: undefined },
    { eligibleAssignees: null },
    { eligibleAssignees: { items: [], completeness: "unknown" as const } },
    {
      eligibleAssignees: {
        items: [
          {
            id: { scope: "assignee" as const, value: " " },
            displayName: "Name",
          },
        ],
        completeness: "unknown" as const,
      },
    },
    {
      eligibleAssignees: {
        items: [
          { id: { scope: "assignee" as const, value: "a".repeat(1025) } },
        ],
        completeness: "unknown" as const,
      },
    },
    ...[undefined, null, NaN, -1, 0.5, Number.MAX_SAFE_INTEGER + 1].map(
      (assignmentVersion) => ({
        assignmentVersion: assignmentVersion as number,
      }),
    ),
    ...["SUBMITTED", "IN_PROGRESS", "RESOLVED", "CONFIRMED"].map((state) => ({
      state,
    })),
  ])(
    "blocks invalid/missing eligibility, assignment version or policy state: %j",
    (overrides) => {
      const service = fakeService();
      render(<Host initial={detail(overrides)} service={service} />);
      expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
      fireEvent.submit(screen.getByRole("form"));
      expect(service.assign).not.toHaveBeenCalled();
      expect(service.detail).not.toHaveBeenCalled();
    },
  );

  it("uses assignmentVersion zero separately from Workflow 17, awaits authoritative Detail and prevents double submit", async () => {
    const service = fakeService();
    const write = deferred<HelpdeskPortalRequestPresentation>();
    const read = deferred<HelpdeskPortalRequestPresentation>();
    service.assign.mockReturnValue(write.promise);
    service.detail.mockReturnValue(read.promise);
    const initial = detail();
    const user = userEvent.setup();
    render(<Host initial={initial} service={service} />);
    expect(
      screen.getByText("Người xử lý hiện tại", { selector: "span" }),
    ).toBeVisible();
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
    expect(service.assign).toHaveBeenCalledExactlyOnceWith(helpdeskId, {
      assigneeId: "worker-2",
      expectedAssignmentVersion: 0,
    });
    expect(service.detail).not.toHaveBeenCalled();
    expect(screen.getByTestId("authoritative")).toHaveTextContent(
      "TRIAGED:17:0:test-worker-subject",
    );
    expect(
      screen.getByRole("button", { name: "Đang phân công…" }),
    ).toBeDisabled();
    await act(async () => {
      // Mutation DTOs do not include eligible list/names. Do not invent either.
      write.resolve(
        mapHelpdeskRequestPresentation(
          helpdeskWire({ assigneeId: "worker-2", assignmentVersion: 1 }),
        ),
      );
      await write.promise;
    });
    expect(service.detail).toHaveBeenCalledExactlyOnceWith(helpdeskId);
    expect(screen.getByTestId("authoritative")).toHaveTextContent(
      "TRIAGED:17:0:test-worker-subject",
    );
    await act(async () => {
      read.resolve(
        detail({
          assigneeId: "worker-2",
          assignmentVersion: 1,
          currentAssignee: {
            id: { scope: "assignee", value: "worker-2" },
            displayName: "Tên sau reload Detail",
          },
        }),
      );
      await read.promise;
    });
    expect(
      await screen.findByText("Tên sau reload Detail", { selector: "span" }),
    ).toBeVisible();
    expect(screen.getByTestId("authoritative")).toHaveTextContent(
      "TRIAGED:17:1:worker-2",
    );
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    await waitFor(() => expect(service.assign).toHaveBeenCalledTimes(2));
    expect(service.assign.mock.calls[1][1]).toEqual({
      assigneeId: "worker-2",
      expectedAssignmentVersion: 1,
    });
  });

  it.each([
    "CONFLICT",
    "COMMIT_OUTCOME_UNKNOWN",
    "UNAUTHENTICATED",
    "FORBIDDEN",
    "NOT_FOUND",
  ] as const)(
    "shows safe %s and locks write without retry, refresh or optimistic changes",
    async (code) => {
      const service = fakeService();
      service.assign.mockRejectedValue(new MvpApplicationError(code));
      const user = userEvent.setup();
      const view = render(<Host initial={detail()} service={service} />);
      await user.selectOptions(screen.getByRole("combobox"), "choice-0");
      await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        new MvpApplicationError(code).message,
      );
      view.rerender(
        <Host initial={detail({ assignmentVersion: 1 })} service={service} />,
      );
      fireEvent.submit(screen.getByRole("form"));
      expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
      expect(service.assign).toHaveBeenCalledTimes(1);
      expect(service.detail).not.toHaveBeenCalled();
      expect(screen.getByTestId("authoritative")).toHaveTextContent(
        "TRIAGED:17:0:test-worker-subject",
      );
    },
  );

  it("locks after a successful write followed by failed Detail reload without replay", async () => {
    const service = fakeService();
    service.detail.mockRejectedValue(
      new MvpApplicationError("NETWORK_FAILURE"),
    );
    const user = userEvent.setup();
    render(<Host initial={detail()} service={service} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      new MvpApplicationError("COMMIT_OUTCOME_UNKNOWN").message,
    );
    fireEvent.submit(screen.getByRole("form"));
    expect(service.assign).toHaveBeenCalledTimes(1);
    expect(service.detail).toHaveBeenCalledTimes(1);
    expect(
      screen.getByText("Người xử lý hiện tại", { selector: "span" }),
    ).toBeVisible();
  });

  it("keeps redacted names redacted while selecting the real eligible ID", async () => {
    const initial = detail({
      currentAssignee: { id: { scope: "assignee", value: "current" } },
      eligibleAssignees: {
        items: [
          {
            id: { scope: "assignee", value: "worker-redacted" },
            displayName: null,
          },
        ],
        completeness: "unknown",
      },
    });
    const service = fakeService();
    render(<Host initial={initial} service={service} />);
    expect(
      screen.getByText("Tên không được cung cấp", { selector: "span" }),
    ).toBeVisible();
    const user = userEvent.setup();
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    expect(service.assign).toHaveBeenCalledExactlyOnceWith(helpdeskId, {
      assigneeId: "worker-redacted",
      expectedAssignmentVersion: 0,
    });
  });

  it("rejects an ID not supplied by authorized Detail before writing", async () => {
    const service = fakeService();
    const props = helpdeskAssignmentFormProps(detail(), service, vi.fn());
    expect(props?.submission?.status).toBe("ready");
    if (props?.submission?.status !== "ready")
      throw new Error("Expected ready test binding");
    await expect(
      props.submission.submitAssignment({ scope: "assignee", value: "forged" }),
    ).rejects.toThrow(RangeError);
    expect(service.assign).not.toHaveBeenCalled();
  });
});

describe("APP-10/11/13 fake transport integration", () => {
  it.each([
    [401, "UNAUTHENTICATED"],
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
    [503, "COMMIT_OUTCOME_UNKNOWN"],
  ] as const)(
    "assignment HTTP %s shows a safe error and locks without replay or optimistic updates",
    async (status, code) => {
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockResolvedValueOnce({
          status: 200,
          body: helpdeskWire({
            eligibleAssignees: [{ id: "worker-2", displayName: null }],
          }),
        })
        .mockResolvedValue({
          status,
          body: {
            code: "COMMIT_OUTCOME_UNKNOWN",
            message: "private diagnostic",
          },
        });
      const onSessionInvalid = vi.fn();
      const service = new HelpdeskRequestService(
        new HelpdeskApiClient(transport, onSessionInvalid),
      );
      render(
        <Host initial={await service.detail(helpdeskId)} service={service} />,
      );
      const user = userEvent.setup();
      await user.selectOptions(screen.getByRole("combobox"), "choice-0");
      await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        new MvpApplicationError(code).message,
      );
      expect(screen.getByRole("alert")).not.toHaveTextContent("private");
      fireEvent.submit(screen.getByRole("form"));
      expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
      expect(screen.getByTestId("authoritative")).toHaveTextContent(
        "TRIAGED:17:0:test-worker-subject",
      );
      expect(transport).toHaveBeenCalledTimes(2);
      expect(onSessionInvalid).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
      if (status === 401) {
        await expect(service.detail(helpdeskId)).rejects.toMatchObject({
          code: "UNAUTHENTICATED",
        });
        expect(transport).toHaveBeenCalledTimes(2);
      }
    },
  );

  it("maps Detail, posts assignment guard only, then reloads authoritative Detail through Application service", async () => {
    const authorized = helpdeskWire({
      eligibleAssignees: [{ id: "worker-2", displayName: "Tên Backend" }],
    });
    const assigned = helpdeskWire({
      assigneeId: "worker-2",
      assignmentVersion: 1,
    });
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockResolvedValueOnce({ status: 200, body: authorized })
      .mockResolvedValueOnce({ status: 200, body: assigned })
      .mockResolvedValueOnce({
        status: 200,
        body: {
          ...assigned,
          assigneeDisplayName: "Tên từ Detail",
          eligibleAssignees: [],
        },
      });
    const service = new HelpdeskRequestService(
      new HelpdeskApiClient(transport, vi.fn()),
    );
    render(
      <Host initial={await service.detail(helpdeskId)} service={service} />,
    );
    const user = userEvent.setup();
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    expect(
      await screen.findByText("Tên từ Detail", { selector: "span" }),
    ).toBeVisible();
    expect(
      transport.mock.calls.map(([request]) => [
        request.method,
        request.path,
        request.body,
      ]),
    ).toEqual([
      ["GET", `/api/v1/mvp/requests/${helpdeskId}`, undefined],
      [
        "POST",
        `/api/v1/mvp/requests/${helpdeskId}/assign`,
        { assigneeId: "worker-2", expectedAssignmentVersion: 0 },
      ],
      ["GET", `/api/v1/mvp/requests/${helpdeskId}`, undefined],
    ]);
    expect(screen.getByTestId("authoritative")).toHaveTextContent(
      "TRIAGED:17:1:worker-2",
    );
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
  });
});
