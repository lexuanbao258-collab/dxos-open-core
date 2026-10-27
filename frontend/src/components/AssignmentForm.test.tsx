import {
  afterEach,
  beforeEach,
  describe,
  expect,
  expectTypeOf,
  it,
  vi,
} from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  AssignmentForm,
  type AssignmentFormProps,
  type AssignmentSubmitBinding,
} from "./AssignmentForm";
import { MvpApplicationError, type MvpErrorCode } from "../services/mvp/errors";
import type { Assignee, AssigneeId } from "../services/mvp/types";

const first = {
  id: { scope: "assignee", value: { opaque: "test-person-1" } },
  displayName: "Nguyễn An",
} satisfies Assignee;
const second = {
  id: { scope: "assignee", value: { opaque: "test-person-2" } },
  displayName: "Nguyễn An",
} satisfies Assignee;

/** Test-only ready callback: no production concurrency contract/token implied. */
function fixture(overrides: Partial<AssignmentFormProps> = {}) {
  const submitAssignment = vi
    .fn<(id: AssigneeId) => Promise<void>>()
    .mockResolvedValue(undefined);
  const props: AssignmentFormProps = {
    currentAssignee: null,
    eligibleAssignees: { items: [first, second], completeness: "complete" },
    assignmentPermission: "allowed",
    submission: { status: "ready", submitAssignment },
    ...overrides,
  };
  return { props, submitAssignment };
}

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((resolvePromise) => {
    resolve = resolvePromise;
  });
  return { promise, resolve };
}

let assertNoNetwork: () => void;
beforeEach(() => {
  const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => {
    throw new Error("Unexpected LIVE fetch");
  });
  const xhrSpy = vi
    .spyOn(XMLHttpRequest.prototype, "send")
    .mockImplementation(() => {
      throw new Error("Unexpected LIVE XMLHttpRequest");
    });
  assertNoNetwork = () => {
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
  };
});

afterEach(() => {
  cleanup();
  try {
    assertNoNetwork();
  } finally {
    vi.restoreAllMocks();
  }
});

describe("APP-11 standalone assignment capability and data", () => {
  it.each([undefined, "unknown", "denied"] as const)(
    "blocks submit with permission %s despite a populated eligible list and fake ready binding",
    (assignmentPermission) => {
      const { props, submitAssignment } = fixture({ assignmentPermission });
      render(<AssignmentForm {...props} />);
      const form = screen.getByRole("form", { name: "Phân công xử lý" });
      const select = screen.getByRole("combobox", { name: "Người xử lý" });
      expect(select).toBeDisabled();
      expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
      // Even bypassing the disabled controls cannot bypass the callback guard.
      fireEvent.change(select, { target: { value: "choice-0" } });
      fireEvent.submit(form);
      expect(submitAssignment).not.toHaveBeenCalled();
      expect(select).toHaveAccessibleDescription(
        assignmentPermission === "denied"
          ? "Bạn không có quyền phân công yêu cầu này."
          : "Chưa xác nhận quyền phân công.",
      );
    },
  );

  it("defaults to blocked without any props, without requiring Router or PortalProvider", () => {
    render(<AssignmentForm />);
    expect(screen.getByText("Chưa có thông tin được cung cấp")).toBeVisible();
    expect(
      screen.getByRole("heading", { name: "Chưa có danh sách người xử lý" }),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
  });

  it.each([
    undefined,
    { status: "blocked" as const, reason: "Chờ xác nhận dữ liệu phân công." },
  ])(
    "requires an explicitly prepared Application callback, not a fabricated version",
    (submission) => {
      const { props, submitAssignment } = fixture({ submission });
      render(<AssignmentForm {...props} />);
      fireEvent.change(screen.getByRole("combobox"), {
        target: { value: "choice-0" },
      });
      fireEvent.submit(screen.getByRole("form"));
      expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
      expect(submitAssignment).not.toHaveBeenCalled();
      expect(
        screen.getByText(submission?.reason ?? "Phân công chưa sẵn sàng."),
      ).toBeVisible();
    },
  );

  it.each(["complete", "partial", "unknown"] as const)(
    "handles an empty %s projection without claiming other people are eligible",
    (completeness) => {
      const { props, submitAssignment } = fixture({
        eligibleAssignees: { items: [], completeness },
      });
      render(<AssignmentForm {...props} />);
      expect(
        screen.getByRole("heading", {
          name: "Danh sách được cung cấp chưa có người xử lý",
        }),
      ).toBeVisible();
      expect(screen.getByRole("combobox")).toBeDisabled();
      expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
      fireEvent.submit(screen.getByRole("form"));
      expect(submitAssignment).not.toHaveBeenCalled();
    },
  );

  it("distinguishes unavailable eligibility from an empty authorized list", () => {
    const { props, submitAssignment } = fixture({
      eligibleAssignees: undefined,
    });
    render(<AssignmentForm {...props} />);
    expect(
      screen.getByRole("heading", { name: "Chưa có danh sách người xử lý" }),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    expect(submitAssignment).not.toHaveBeenCalled();
  });

  it("shows current assignment as supplied and does not preselect it as a new command", () => {
    const { props, submitAssignment } = fixture({ currentAssignee: first });
    render(<AssignmentForm {...props} />);
    expect(screen.getByText("Nguyễn An", { selector: "span" })).toBeVisible();
    expect(screen.getByRole("combobox")).toHaveValue("");
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    expect(submitAssignment).not.toHaveBeenCalled();
  });

  it("shows loading and prevents submit while loading", () => {
    const { props, submitAssignment } = fixture({ loading: true });
    render(<AssignmentForm {...props} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Đang tải dữ liệu phân công…",
    );
    expect(screen.getByRole("form")).toHaveAttribute("aria-busy", "true");
    expect(screen.getByRole("combobox")).toBeDisabled();
    fireEvent.submit(screen.getByRole("form"));
    expect(submitAssignment).not.toHaveBeenCalled();
  });

  it("shows the supplied read error without submitting or initiating a reload", () => {
    const { props, submitAssignment } = fixture({
      error: "Không thể tải dữ liệu phân công.",
    });
    render(<AssignmentForm {...props} />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Không thể tải dữ liệu phân công.",
    );
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    fireEvent.submit(screen.getByRole("form"));
    expect(submitAssignment).not.toHaveBeenCalled();
  });

  it("keeps Workflow, role and version metadata out of the form contract", () => {
    expectTypeOf<"role">().not.toExtend<keyof AssignmentFormProps>();
    expectTypeOf<"state">().not.toExtend<keyof AssignmentFormProps>();
    expectTypeOf<"workflowVersion">().not.toExtend<keyof AssignmentFormProps>();
    expectTypeOf<"assignmentVersion">().not.toExtend<
      keyof AssignmentFormProps
    >();
    expectTypeOf<
      Parameters<
        Extract<
          AssignmentSubmitBinding,
          { status: "ready" }
        >["submitAssignment"]
      >[0]
    >().toEqualTypeOf<AssigneeId>();
  });
});

describe("APP-11 assignment selection and single write", () => {
  it("displays names but passes the original opaque ID, even with duplicate names", async () => {
    const { props, submitAssignment } = fixture();
    const user = userEvent.setup();
    render(<AssignmentForm {...props} />);
    expect(screen.getAllByRole("option", { name: "Nguyễn An" })).toHaveLength(
      2,
    );
    expect(
      screen
        .getAllByRole("option")
        .map((option) => (option as HTMLOptionElement).value),
    ).toEqual(["", "choice-0", "choice-1"]);
    await user.selectOptions(screen.getByRole("combobox"), "choice-1");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    await waitFor(() => expect(submitAssignment).toHaveBeenCalledTimes(1));
    expect(submitAssignment.mock.calls[0]).toEqual([second.id]);
    expect(submitAssignment.mock.calls[0][0]).toBe(second.id);
    expect(screen.getByText("Chưa phân công")).toBeVisible();
    expect(screen.queryByText(/Đã phân công/)).not.toBeInTheDocument();
  });

  it("keeps the selected ID when the supplied list is reordered", async () => {
    const { props, submitAssignment } = fixture();
    const user = userEvent.setup();
    const view = render(<AssignmentForm {...props} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    view.rerender(
      <AssignmentForm
        {...props}
        eligibleAssignees={{ items: [second, first], completeness: "complete" }}
      />,
    );
    expect(screen.getByRole("combobox")).toHaveValue("choice-1");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    expect(submitAssignment.mock.calls[0][0]).toBe(first.id);
  });

  it("invalidates a removed or replaced reference instead of submitting another person", async () => {
    const { props, submitAssignment } = fixture();
    const user = userEvent.setup();
    const view = render(<AssignmentForm {...props} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    view.rerender(
      <AssignmentForm
        {...props}
        eligibleAssignees={{
          items: [{ ...first, id: { ...first.id } }, second],
          completeness: "complete",
        }}
      />,
    );
    expect(screen.getByRole("combobox")).toHaveValue("");
    expect(screen.getByRole("combobox")).toHaveAccessibleDescription(
      "Danh sách đã thay đổi. Hãy chọn lại người xử lý.",
    );
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    fireEvent.submit(screen.getByRole("form"));
    expect(submitAssignment).not.toHaveBeenCalled();
  });

  it("blocks an existing selection immediately when permission is revoked", async () => {
    const { props, submitAssignment } = fixture();
    const user = userEvent.setup();
    const view = render(<AssignmentForm {...props} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    expect(screen.getByRole("button", { name: "Phân công" })).toBeEnabled();
    view.rerender(<AssignmentForm {...props} assignmentPermission="denied" />);
    fireEvent.submit(screen.getByRole("form"));
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    expect(submitAssignment).not.toHaveBeenCalled();
  });

  it("blocks duplicate submit while pending and never replaces authoritative assignee on success", async () => {
    const current = {
      id: { scope: "assignee", value: "current-person" },
      displayName: "Người đang phụ trách",
    } satisfies Assignee;
    const { props, submitAssignment } = fixture({ currentAssignee: current });
    const pending = deferred();
    submitAssignment.mockReturnValue(pending.promise);
    const user = userEvent.setup();
    render(<AssignmentForm {...props} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-1");
    await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
    fireEvent.submit(screen.getByRole("form"));
    expect(submitAssignment).toHaveBeenCalledTimes(1);
    expect(
      screen.getByRole("button", { name: "Đang phân công…" }),
    ).toBeDisabled();
    expect(screen.getByRole("form")).toHaveAttribute("aria-busy", "true");
    await act(async () => {
      pending.resolve();
      await pending.promise;
    });
    expect(screen.getByRole("form")).toHaveAttribute("aria-busy", "false");
    expect(screen.getByRole("combobox")).toHaveValue("");
    expect(screen.getByText(current.displayName)).toBeVisible();
    expect(props.currentAssignee).toBe(current);
    expect(submitAssignment).toHaveBeenCalledTimes(1);
  });

  it.each<MvpErrorCode>([
    "UNAUTHENTICATED",
    "FORBIDDEN",
    "NOT_FOUND",
    "CONFLICT",
    "NETWORK_FAILURE",
    "COMMIT_OUTCOME_UNKNOWN",
  ])("does not retry %s and keeps the write stopped", async (code) => {
    const { props, submitAssignment } = fixture();
    submitAssignment.mockRejectedValue(new MvpApplicationError(code));
    const user = userEvent.setup();
    render(<AssignmentForm {...props} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
    expect(await screen.findByRole("alert")).toBeVisible();
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    fireEvent.submit(screen.getByRole("form"));
    expect(submitAssignment).toHaveBeenCalledTimes(1);
  });

  it("keeps a lost-response write stopped after props and callbacks refresh", async () => {
    const { props, submitAssignment } = fixture();
    let committed = 0;
    submitAssignment.mockImplementation(async () => {
      committed += 1;
      throw new Error("Sensitive diagnostic: reply lost after commit");
    });
    const user = userEvent.setup();
    const view = render(<AssignmentForm {...props} />);
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Chưa xác định kết quả ghi",
    );
    expect(screen.getByRole("alert")).not.toHaveTextContent(
      "Sensitive diagnostic",
    );
    const next = fixture();
    view.rerender(<AssignmentForm {...next.props} currentAssignee={first} />);
    fireEvent.submit(screen.getByRole("form"));
    expect(screen.getByRole("button", { name: "Phân công" })).toBeDisabled();
    expect(submitAssignment).toHaveBeenCalledTimes(1);
    expect(next.submitAssignment).not.toHaveBeenCalled();
    expect(committed).toBe(1);
  });
});
