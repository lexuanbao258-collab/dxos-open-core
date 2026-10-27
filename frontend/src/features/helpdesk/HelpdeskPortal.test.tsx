import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../../App";
import { HelpdeskRequestService } from "../../services/mvp/HelpdeskRequestService";
import { HelpdeskApiClient } from "../../services/mvp/http/HelpdeskApiClient";
import type { HelpdeskHttpTransport } from "../../services/mvp/http/transport";
import type { MvpHelpdeskRequestPort } from "../../services/mvp/helpdeskPortalPort";
import { mapHelpdeskRequestPresentation } from "../../services/mvp/helpdeskPresentation";
import { requestService as defaultService } from "../../services/portalService";
import { fakeHelpdeskTransport } from "../../test/fakeHelpdeskTransport";
import {
  helpdeskId,
  helpdeskWire,
  helpdeskCursor,
} from "../../test/helpdeskFixtures";

const ROOT = "/mvp/helpdesk";
let fetchSpy: ReturnType<typeof vi.spyOn>;
let xhrSpy: ReturnType<typeof vi.spyOn>;
beforeEach(() => {
  sessionStorage.setItem("dxos-demo-role", "employee");
  vi.stubGlobal("matchMedia", () => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
  fetchSpy = vi
    .spyOn(globalThis, "fetch")
    .mockRejectedValue(new Error("Unexpected LIVE fetch"));
  xhrSpy = vi.spyOn(XMLHttpRequest.prototype, "send").mockImplementation(() => {
    throw new Error("Unexpected LIVE XHR");
  });
});
afterEach(() => {
  expect(fetchSpy).not.toHaveBeenCalled();
  expect(xhrSpy).not.toHaveBeenCalled();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
function clientFixture(transport: HelpdeskHttpTransport) {
  const invalid = vi.fn();
  return {
    service: new HelpdeskRequestService(
      new HelpdeskApiClient(transport, invalid),
    ),
    invalid,
  };
}
function mount(service?: MvpHelpdeskRequestPort, path = ROOT) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App helpdeskService={service} />
    </MemoryRouter>,
  );
}
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

async function openSession(user: ReturnType<typeof userEvent.setup>) {
  // Session navigation must not depend on sidebar copy from the held redesign.
  const links = document.querySelectorAll<HTMLAnchorElement>(
    'nav a[href="/session"]',
  );
  expect(links).toHaveLength(1);
  await user.click(links[0]);
  expect(screen.getByRole("button", { name: "Kết thúc phiên" })).toBeEnabled();
}

describe("APP-14 Portal MVP presentation integration", () => {
  it.each(["read", "triage"] as const)(
    "expires the same session for a delayed %s 401 after leaving the feature",
    async (operation) => {
      const pending = deferred<Awaited<ReturnType<HelpdeskHttpTransport>>>();
      const wire = helpdeskWire({
        state: "SUBMITTED",
        allowedActions: ["triage"],
      });
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockImplementation(async (request) => {
          if (operation === "read" || request.method === "POST")
            return pending.promise;
          return {
            status: 200,
            body: request.path.endsWith("/history") ? [] : wire,
          };
        });
      const user = userEvent.setup();
      mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
      if (operation === "triage")
        await user.click(
          await screen.findByRole("button", { name: "Tiếp nhận" }),
        );
      else await waitFor(() => expect(transport).toHaveBeenCalled());
      await openSession(user);
      await act(async () => pending.resolve({ status: 401 }));
      expect(
        screen.getByRole("heading", { name: "Bắt đầu trải nghiệm" }),
      ).toBeVisible();
      expect(sessionStorage.getItem("dxos-demo-role")).toBeNull();
    },
  );

  it("does not reload assignment Detail after logout while its write is pending", async () => {
    const pending = deferred<Awaited<ReturnType<HelpdeskHttpTransport>>>();
    const wire = helpdeskWire({
      state: "TRIAGED",
      allowedActions: ["assign"],
      eligibleAssignees: [{ id: "worker", displayName: "Worker" }],
    });
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockImplementation(async (request) =>
        request.method === "POST"
          ? pending.promise
          : {
              status: 200,
              body: request.path.endsWith("/history") ? [] : wire,
            },
      );
    const user = userEvent.setup();
    mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
    await user.selectOptions(await screen.findByRole("combobox"), "choice-0");
    await user.click(screen.getByRole("button", { name: "Phân công" }));
    await openSession(user);
    await user.click(screen.getByRole("button", { name: "Kết thúc phiên" }));
    const attempts = transport.mock.calls.length;
    await act(async () => pending.resolve({ status: 200, body: wire }));
    expect(transport).toHaveBeenCalledTimes(attempts);
    expect(
      screen.getByRole("heading", { name: "Bắt đầu trải nghiệm" }),
    ).toBeVisible();
  });

  it.each([401, 503])(
    "keeps the new session on an old mutation HTTP %s, retaining only the unknown write stop",
    async (status) => {
      const pending = deferred<Awaited<ReturnType<HelpdeskHttpTransport>>>();
      const wire = helpdeskWire({
        state: "SUBMITTED",
        allowedActions: ["triage"],
      });
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockImplementation(async (request) =>
          request.method === "POST"
            ? pending.promise
            : {
                status: 200,
                body: request.path.endsWith("/history") ? [] : wire,
              },
        );
      const previous = clientFixture(transport).service;
      const next = clientFixture(fakeHelpdeskTransport(wire).transport).service;
      const user = userEvent.setup();
      const view = mount(previous, `${ROOT}/${helpdeskId}`);
      await user.click(
        await screen.findByRole("button", { name: "Tiếp nhận" }),
      );
      await openSession(user);
      await user.click(screen.getByRole("button", { name: "Kết thúc phiên" }));
      view.rerender(
        <MemoryRouter>
          <App helpdeskService={next} />
        </MemoryRouter>,
      );
      await user.click(screen.getByRole("button", { name: "Vào phiên demo" }));
      await act(async () =>
        pending.resolve({
          status,
          body: status === 503 ? { code: "COMMIT_OUTCOME_UNKNOWN" } : undefined,
        }),
      );
      expect(sessionStorage.getItem("dxos-demo-role")).toBe("employee");
      await user.click(
        screen.getByRole("link", { name: "IT Helpdesk MVP · kiểm thử" }),
      );
      await user.click(
        await screen.findByRole("link", { name: /Không thể lưu báo cáo/ }),
      );
      const action = await screen.findByRole("button", { name: "Tiếp nhận" });
      if (status === 401) expect(action).toBeEnabled();
      else expect(action).toBeDisabled();
    },
  );

  it("runs Create → List → Detail → Triage → Assign → Start → Resolve → Confirm → History through fake transport", async () => {
    const fake = fakeHelpdeskTransport();
    const { service } = clientFixture(fake.transport);
    const legacy = vi.spyOn(defaultService, "list");
    const user = userEvent.setup();
    mount(service);
    expect(await screen.findByText("Chưa có yêu cầu IT")).toBeVisible();
    await user.click(screen.getByRole("link", { name: "Tạo yêu cầu IT" }));
    await user.type(
      screen.getByRole("textbox", { name: "Tiêu đề yêu cầu" }),
      "Sự cố phần mềm APP-14",
    );
    await user.type(
      screen.getByRole("textbox", { name: "Mô tả chi tiết" }),
      "Không thể lưu báo cáo công việc.",
    );
    await user.dblClick(screen.getByRole("button", { name: "Gửi yêu cầu IT" }));
    await user.click(
      await screen.findByRole("link", { name: /Sự cố phần mềm APP-14/ }),
    );
    expect(await screen.findByTestId("mvp-state")).toHaveTextContent(
      "SUBMITTED",
    );
    expect(
      screen.queryByRole("form", { name: "Phân công xử lý" }),
    ).not.toBeInTheDocument();
    await user.dblClick(screen.getByRole("button", { name: "Tiếp nhận" }));
    await waitFor(() =>
      expect(screen.getByTestId("mvp-state")).toHaveTextContent("TRIAGED"),
    );
    await user.selectOptions(
      screen.getByRole("combobox", { name: "Người xử lý" }),
      "choice-0",
    );
    await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
    await waitFor(() =>
      expect(screen.getByTestId("mvp-assignment-version")).toHaveTextContent(
        "1",
      ),
    );
    expect(screen.getByTestId("mvp-version")).toHaveTextContent("2");
    expect(fake.history).toHaveLength(1);
    await user.dblClick(screen.getByRole("button", { name: "Bắt đầu xử lý" }));
    await waitFor(() =>
      expect(screen.getByTestId("mvp-state")).toHaveTextContent("IN_PROGRESS"),
    );
    await user.click(screen.getByRole("button", { name: "Ghi kết quả xử lý" }));
    expect(
      await screen.findByText("Kết quả xử lý không được để trống."),
    ).toBeVisible();
    await user.type(
      screen.getByRole("textbox", { name: "Kết quả xử lý" }),
      "Đã khắc phục lỗi lưu báo cáo.",
    );
    await user.dblClick(
      screen.getByRole("button", { name: "Ghi kết quả xử lý" }),
    );
    await waitFor(() =>
      expect(screen.getByTestId("mvp-state")).toHaveTextContent("RESOLVED"),
    );
    await user.dblClick(
      screen.getByRole("button", { name: "Xác nhận hoàn thành" }),
    );
    await waitFor(() =>
      expect(screen.getByTestId("mvp-state")).toHaveTextContent("CONFIRMED"),
    );
    const history = screen.getByRole("region", { name: "Lịch sử Workflow" });
    expect(within(history).getAllByRole("listitem")).toHaveLength(4);
    expect(within(history).getByText("start_work")).toBeVisible();
    expect(history).not.toHaveTextContent(/actor|ghi chú|event.id/i);
    expect(
      screen.queryByRole("button", { name: "Xác nhận hoàn thành" }),
    ).not.toBeInTheDocument();
    const mutations = fake.calls.filter((request) => request.method === "POST");
    expect(mutations.map((request) => request.path.split("/").at(-1))).toEqual([
      "requests",
      "triage",
      "assign",
      "start",
      "resolve",
      "confirm",
    ]);
    expect(mutations[0].body).toEqual({
      title: "Sự cố phần mềm APP-14",
      description: "Không thể lưu báo cáo công việc.",
      requestType: "IT_HELPDESK",
    });
    expect(mutations[2].body).toEqual({
      assigneeId: "fake-worker",
      expectedAssignmentVersion: 0,
    });
    expect(mutations[3].body).toEqual({ expectedVersion: 2 });
    expect(mutations[4].body).toEqual({
      expectedVersion: 3,
      resolution: "Đã khắc phục lỗi lưu báo cáo.",
    });
    expect(mutations[5].body).toEqual({ expectedVersion: 4 });
    expect(legacy).not.toHaveBeenCalled();
  });

  it("does not mount the MVP service by default or make network requests", async () => {
    mount();
    expect(
      await screen.findByText("MVP chưa được gắn service kiểm thử"),
    ).toBeVisible();
    expect(
      screen.queryByRole("link", { name: "IT Helpdesk MVP · kiểm thử" }),
    ).not.toBeInTheDocument();
  });

  it("honors service capabilities even when role and state suggest other actions", async () => {
    sessionStorage.setItem("dxos-demo-role", "processor");
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockImplementation(async (request) => ({
        status: 200,
        body: request.path.endsWith("/history")
          ? []
          : helpdeskWire({
              state: "future_authoritative_state",
              allowedActions: [],
              eligibleAssignees: [{ id: "worker", displayName: null }],
            }),
      }));
    mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
    expect(await screen.findByTestId("mvp-state")).toHaveTextContent(
      "future_authoritative_state",
    );
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Tiếp nhận" }),
    ).not.toBeInTheDocument();
    expect(
      screen.getAllByText("Tên không được cung cấp").length,
    ).toBeGreaterThan(0);
    expect(screen.getByText("test-requester-subject")).toBeVisible();
    expect(transport).toHaveBeenCalledTimes(2);
  });

  it.each([
    { eligibleAssignees: undefined },
    { eligibleAssignees: null },
    { eligibleAssignees: [] },
  ])(
    "blocks assignment for eligibility $eligibleAssignees without inventing options",
    async ({ eligibleAssignees }) => {
      const wire = helpdeskWire({ eligibleAssignees });
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockImplementation(async (request) => ({
          status: 200,
          body: request.path.endsWith("/history") ? [] : wire,
        }));
      mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
      expect(
        await screen.findByRole("button", { name: "Phân công" }),
      ).toBeDisabled();
      fireEvent.submit(screen.getByRole("form"));
      expect(transport).toHaveBeenCalledTimes(2);
    },
  );

  it("blocks missing assignmentVersion supplied by a fake presentation port", async () => {
    const { service } = clientFixture(fakeHelpdeskTransport().transport);
    vi.spyOn(service, "detail").mockResolvedValue({
      ...mapHelpdeskRequestPresentation(
        helpdeskWire({
          eligibleAssignees: [{ id: "worker", displayName: null }],
        }),
      ),
      assignmentVersion: undefined as unknown as number,
    });
    vi.spyOn(service, "history").mockResolvedValue([]);
    const assign = vi.spyOn(service, "assign");
    mount(service, `${ROOT}/${helpdeskId}`);
    expect(
      await screen.findByRole("button", { name: "Phân công" }),
    ).toBeDisabled();
    fireEvent.submit(screen.getByRole("form"));
    expect(assign).not.toHaveBeenCalled();
  });

  it.each([403, 404])(
    "renders safe read HTTP %s without leaking resource data",
    async (status) => {
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockResolvedValue({ status, body: { message: "sensitive resource" } });
      mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent(
        status === 403
          ? "Bạn không có quyền"
          : "Không tìm thấy yêu cầu hoặc yêu cầu không khả dụng",
      );
      expect(alert).not.toHaveTextContent("sensitive");
      expect(screen.queryByTestId("mvp-state")).not.toBeInTheDocument();
    },
  );

  it("expires the Portal on 401 and stops protected operations", async () => {
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockResolvedValue({ status: 401 });
    const { service, invalid } = clientFixture(transport);
    mount(service);
    expect(
      await screen.findByRole("heading", { name: "Bắt đầu trải nghiệm" }),
    ).toBeVisible();
    expect(invalid).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem("dxos-demo-role")).toBeNull();
    expect(transport).toHaveBeenCalledTimes(1);
    await expect(service.list()).rejects.toMatchObject({
      code: "UNAUTHENTICATED",
    });
    expect(transport).toHaveBeenCalledTimes(1);
  });

  it.each([401, 403, 404])(
    "handles mutation HTTP %s without displaying stale protected Detail or retrying",
    async (status) => {
      const wire = helpdeskWire({
        state: "SUBMITTED",
        allowedActions: ["triage"],
      });
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockImplementation(async (request) =>
          request.method === "POST"
            ? { status, body: { message: "private diagnostic" } }
            : {
                status: 200,
                body: request.path.endsWith("/history") ? [] : wire,
              },
        );
      const { service, invalid } = clientFixture(transport);
      mount(service, `${ROOT}/${helpdeskId}`);
      await userEvent
        .setup()
        .dblClick(await screen.findByRole("button", { name: "Tiếp nhận" }));
      if (status === 401) {
        expect(
          await screen.findByRole("heading", { name: "Bắt đầu trải nghiệm" }),
        ).toBeVisible();
        expect(invalid).toHaveBeenCalledTimes(1);
        expect(sessionStorage.getItem("dxos-demo-role")).toBeNull();
      } else {
        expect(await screen.findByRole("alert")).not.toHaveTextContent(
          "private",
        );
      }
      expect(screen.queryByTestId("mvp-state")).not.toBeInTheDocument();
      expect(
        transport.mock.calls.filter(([request]) => request.method === "POST"),
      ).toHaveLength(1);
    },
  );

  it("does not substitute assignmentVersion for a missing Workflow version", async () => {
    const { service } = clientFixture(fakeHelpdeskTransport().transport);
    vi.spyOn(service, "detail").mockResolvedValue({
      ...mapHelpdeskRequestPresentation(
        helpdeskWire({ allowedActions: ["triage"], assignmentVersion: 17 }),
      ),
      version: undefined as unknown as number,
    });
    vi.spyOn(service, "history").mockResolvedValue([]);
    const triage = vi.spyOn(service, "triage");
    mount(service, `${ROOT}/${helpdeskId}`);
    expect(
      await screen.findByRole("button", { name: "Tiếp nhận" }),
    ).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Tiếp nhận" }));
    expect(triage).not.toHaveBeenCalled();
  });

  it("hides Detail when History is denied, then recovers only via an explicit read reload", async () => {
    let denied = true;
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockImplementation(async (request) =>
        request.path.endsWith("/history")
          ? { status: denied ? 403 : 200, body: [] }
          : { status: 200, body: helpdeskWire() },
      );
    mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Bạn không có quyền",
    );
    expect(screen.queryByTestId("mvp-state")).not.toBeInTheDocument();
    denied = false;
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Tải lại dữ liệu" }));
    expect(await screen.findByTestId("mvp-state")).toHaveTextContent("TRIAGED");
    expect(transport).toHaveBeenCalledTimes(4);
  });

  it("renders loading, nextCursor and explicit read reload without replacing cursor with a total", async () => {
    const pending = deferred<Awaited<ReturnType<HelpdeskHttpTransport>>>();
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockReturnValueOnce(pending.promise)
      .mockResolvedValue({
        status: 200,
        body: { items: [], nextCursor: null },
      });
    const user = userEvent.setup();
    mount(clientFixture(transport).service);
    expect(screen.getByRole("status")).toHaveTextContent("Đang tải");
    await act(async () =>
      pending.resolve({
        status: 200,
        body: { items: [helpdeskWire()], nextCursor: helpdeskCursor },
      }),
    );
    await user.click(await screen.findByRole("button", { name: "Trang tiếp" }));
    expect(await screen.findByText("Chưa có yêu cầu IT")).toBeVisible();
    expect(transport.mock.calls[1][0].path).toBe(
      `/api/v1/mvp/requests?after=${helpdeskCursor}&limit=20`,
    );
    await user.click(screen.getByRole("button", { name: "Tải lại danh sách" }));
    await waitFor(() => expect(transport).toHaveBeenCalledTimes(3));
  });

  it.each(["triage", "assign"] as const)(
    "%s conflict stops writes until explicit successful Detail/history reload",
    async (action) => {
      let version = 7;
      const wire = () =>
        helpdeskWire({
          state: action === "triage" ? "SUBMITTED" : "TRIAGED",
          version,
          assignmentVersion: 2,
          allowedActions: [action],
          eligibleAssignees: [{ id: "worker", displayName: "Tên kiểm thử" }],
        });
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockImplementation(async (request) =>
          request.method === "POST"
            ? { status: 409 }
            : {
                status: 200,
                body: request.path.endsWith("/history") ? [] : wire(),
              },
        );
      const user = userEvent.setup();
      mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
      await screen.findByTestId("mvp-state");
      if (action === "assign")
        await user.selectOptions(screen.getByRole("combobox"), "choice-0");
      const name = action === "triage" ? "Tiếp nhận" : "Phân công";
      await user.dblClick(screen.getByRole("button", { name }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Dữ liệu đã thay đổi",
      );
      expect(screen.getByRole("button", { name })).toBeDisabled();
      expect(transport).toHaveBeenCalledTimes(3);
      version = 12;
      await user.click(
        screen.getByRole("button", { name: "Tải lại chi tiết và lịch sử" }),
      );
      await waitFor(() =>
        expect(screen.getByTestId("mvp-version")).toHaveTextContent("12"),
      );
      if (action === "assign")
        await user.selectOptions(screen.getByRole("combobox"), "choice-0");
      expect(screen.getByRole("button", { name })).toBeEnabled();
      expect(transport).toHaveBeenCalledTimes(5);
      // The read releases a conflict only. It never replays the failed mutation.
      expect(
        transport.mock.calls.filter(([request]) => request.method === "POST"),
      ).toHaveLength(1);
    },
  );

  it.each(["triage", "assign"] as const)(
    "%s unknown outcome stays blocked across reload and feature navigation",
    async (action) => {
      const wire = helpdeskWire({
        state: action === "triage" ? "SUBMITTED" : "TRIAGED",
        allowedActions: [action],
        eligibleAssignees: [{ id: "worker", displayName: null }],
      });
      const transport = vi
        .fn<HelpdeskHttpTransport>()
        .mockImplementation(async (request) => {
          if (request.method === "POST")
            throw new Error("lost ACK after possible commit");
          return {
            status: 200,
            body: request.path.endsWith("/history")
              ? []
              : request.path.includes("?")
                ? { items: [wire], nextCursor: null }
                : wire,
          };
        });
      const user = userEvent.setup();
      mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
      await screen.findByTestId("mvp-state");
      if (action === "assign")
        await user.selectOptions(screen.getByRole("combobox"), "choice-0");
      const name = action === "triage" ? "Tiếp nhận" : "Phân công";
      await user.dblClick(screen.getByRole("button", { name }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Không gửi lại",
      );
      await user.click(
        screen.getByRole("button", { name: "Tải lại chi tiết và lịch sử" }),
      );
      await screen.findByTestId("mvp-state");
      expect(screen.getByRole("button", { name })).toBeDisabled();
      await user.click(
        screen.getByRole("link", { name: "Danh sách yêu cầu IT" }),
      );
      await user.click(
        await screen.findByRole("link", { name: /Không thể lưu báo cáo/ }),
      );
      await screen.findByTestId("mvp-state");
      expect(screen.getByRole("button", { name })).toBeDisabled();
      expect(
        transport.mock.calls.filter(([request]) => request.method === "POST"),
      ).toHaveLength(1);
    },
  );

  it("keeps unknown create locked across leaving and reopening the form", async () => {
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockImplementation(async (request) => {
        if (request.method === "POST")
          return { status: 503, body: { code: "COMMIT_OUTCOME_UNKNOWN" } };
        return { status: 200, body: { items: [], nextCursor: null } };
      });
    const user = userEvent.setup();
    mount(clientFixture(transport).service, `${ROOT}/new`);
    await user.type(
      screen.getByRole("textbox", { name: "Tiêu đề yêu cầu" }),
      "Title",
    );
    await user.type(
      screen.getByRole("textbox", { name: "Mô tả chi tiết" }),
      "Description",
    );
    await user.dblClick(screen.getByRole("button", { name: "Gửi yêu cầu IT" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không gửi lại");
    await user.click(
      screen.getByRole("link", { name: "Xem danh sách và đối soát" }),
    );
    await user.click(
      await screen.findByRole("link", { name: "Tạo yêu cầu IT" }),
    );
    expect(
      screen.getByRole("button", { name: "Gửi yêu cầu IT" }),
    ).toBeDisabled();
    expect(
      transport.mock.calls.filter(([request]) => request.method === "POST"),
    ).toHaveLength(1);
  });

  it("keeps authoritative assignee/state until deferred assignment and Detail reload finish, locking all mutations", async () => {
    const fake = fakeHelpdeskTransport(
      helpdeskWire({ state: "TRIAGED", version: 17, assignmentVersion: 0 }),
    );
    const pending = deferred<void>();
    const transport = vi
      .fn<HelpdeskHttpTransport>()
      .mockImplementation(async (request) => {
        if (request.method === "POST") await pending.promise;
        return fake.transport(request);
      });
    const user = userEvent.setup();
    mount(clientFixture(transport).service, `${ROOT}/${helpdeskId}`);
    await screen.findByTestId("mvp-state");
    await user.selectOptions(screen.getByRole("combobox"), "choice-0");
    await user.dblClick(screen.getByRole("button", { name: "Phân công" }));
    expect(screen.getByTestId("mvp-assignment-version")).toHaveTextContent("0");
    expect(screen.getByText("test-worker-subject")).toBeVisible();
    expect(
      screen.getByRole("button", { name: "Bắt đầu xử lý" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Tải lại chi tiết và lịch sử" }),
    ).toBeDisabled();
    expect(
      transport.mock.calls.filter(([request]) => request.method === "POST"),
    ).toHaveLength(1);
    await act(async () => pending.resolve());
    await waitFor(() =>
      expect(screen.getByTestId("mvp-assignment-version")).toHaveTextContent(
        "1",
      ),
    );
    expect(screen.getByTestId("mvp-version")).toHaveTextContent("17");
    expect(screen.getByText("fake-worker")).toBeVisible();
  });

  it("never reads the injected service without the existing demo session", async () => {
    sessionStorage.clear();
    const transport = vi.fn<HelpdeskHttpTransport>();
    mount(clientFixture(transport).service);
    expect(
      await screen.findByRole("heading", { name: "Bắt đầu trải nghiệm" }),
    ).toBeVisible();
    expect(transport).not.toHaveBeenCalled();
  });

  it.each([false, true])(
    "ignores obsolete service read data/401 when replaced (401=%s)",
    async (unauthenticated) => {
      const previous = deferred<Awaited<ReturnType<HelpdeskHttpTransport>>>();
      const oldService = clientFixture(() => previous.promise).service;
      const nextTransport = vi.fn<HelpdeskHttpTransport>().mockResolvedValue({
        status: 200,
        body: {
          items: [helpdeskWire({ title: "New service result" })],
          nextCursor: null,
        },
      });
      const next = clientFixture(nextTransport).service;
      const view = mount(oldService);
      view.rerender(
        <MemoryRouter initialEntries={[ROOT]}>
          <App helpdeskService={next} />
        </MemoryRouter>,
      );
      expect(
        await screen.findByRole("link", { name: /New service result/ }),
      ).toBeVisible();
      await act(async () =>
        previous.resolve(
          unauthenticated
            ? { status: 401 }
            : { status: 200, body: { items: [], nextCursor: null } },
        ),
      );
      expect(
        screen.getByRole("link", { name: /New service result/ }),
      ).toBeVisible();
      expect(sessionStorage.getItem("dxos-demo-role")).toBe("employee");
    },
  );
});
