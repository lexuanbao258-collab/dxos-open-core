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
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
  useNavigate,
} from "react-router-dom";
import { PortalProvider, usePortal } from "./context";
import type { ServiceRequest } from "./domain/request";
import { useRequests } from "./hooks/useRequests";
import { NewRequest } from "./pages/NewRequest";
import { RequestDetail } from "./pages/RequestDetail";
import { Requests } from "./pages/Requests";
import { MockRequestService } from "./services/MockRequestService";
import { requestService as defaultService } from "./services/portalService";
import { PortalError, type RequestService } from "./services/RequestService";
import type { MvpRequestPort } from "./services/mvp/port";

// Legacy Application view model for injection tests, not a Backend DTO.
const provided: ServiceRequest = {
  id: "SEAM-01",
  kind: "it",
  title: "Yêu cầu do fake service cung cấp",
  description: "Dữ liệu kiểm thử seam Application, không gọi Backend API.",
  status: "resolved",
  priority: "normal",
  requester: "Mai An",
  department: "Phòng phát triển",
  location: "Văn phòng tầng 3",
  assignee: "Bộ phận CNTT",
  createdAt: "2026-10-25T02:00:00Z",
  updatedAt: "2026-10-25T03:00:00Z",
  version: 17,
  timeline: [
    {
      id: "seam-event",
      at: "2026-10-25T03:00:00Z",
      title: "Sự kiện từ fake service",
      actor: "Bộ phận CNTT",
      note: "Lịch sử do fake service cung cấp.",
    },
  ],
};

function fakeService(item: ServiceRequest = provided) {
  return {
    list: vi.fn<RequestService["list"]>().mockResolvedValue([item]),
    get: vi.fn<RequestService["get"]>().mockResolvedValue(item),
    actions: vi.fn<RequestService["actions"]>().mockReturnValue([]),
    create: vi.fn<RequestService["create"]>().mockResolvedValue(item),
    transition: vi.fn<RequestService["transition"]>().mockResolvedValue(item),
  } satisfies RequestService;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function SessionProbe() {
  const { role, sessionExpired } = usePortal();
  return (
    <output data-testid="session">
      {role ?? "no-session"}:{sessionExpired ? "expired" : "active"}
    </output>
  );
}

function SessionControls() {
  const { setRole, role, sessionExpired } = usePortal();
  const navigate = useNavigate();
  const location = useLocation();
  return (
    <>
      <output data-testid="session-location">
        {location.pathname}:{role ?? "none"}:
        {sessionExpired ? "expired" : "active"}
      </output>
      <button
        onClick={() => {
          setRole(null);
          navigate("/session");
        }}
      >
        Test logout
      </button>
      <button
        onClick={() => {
          setRole("employee");
          navigate("/requests");
        }}
      >
        Test new session
      </button>
      <button onClick={() => navigate("/requests")}>Test leave form</button>
    </>
  );
}

function mountSessionPortal(service: RequestService, path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PortalProvider requestService={service}>
        <SessionControls />
        <Routes>
          <Route path="/requests/new" element={<NewRequest />} />
          <Route path="/requests/:id" element={<RequestDetail />} />
          <Route path="*" element={<p>Test destination</p>} />
        </Routes>
      </PortalProvider>
    </MemoryRouter>,
  );
}

function BindingProbe() {
  const { requestService } = usePortal();
  return (
    <output data-testid="binding">
      {requestService === defaultService ? "MOCK" : "injected"}
    </output>
  );
}

function ReadProbe() {
  const { data, loading, error } = useRequests();
  return (
    <output data-testid="read-result">
      {loading ? "loading" : error || data.map((item) => item.title).join(",")}
    </output>
  );
}

function mountPortal(service: RequestService, path = "/requests") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PortalProvider requestService={service}>
        <SessionProbe />
        <Routes>
          <Route path="/requests" element={<Requests />} />
          <Route path="/approvals" element={<Requests mode="approvals" />} />
          <Route path="/history" element={<Requests mode="history" />} />
          <Route path="/requests/new" element={<NewRequest />} />
          <Route path="/requests/:id" element={<RequestDetail />} />
        </Routes>
      </PortalProvider>
    </MemoryRouter>,
  );
}

async function fillCreateForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(
    screen.getByRole("textbox", { name: /Tiêu đề yêu cầu/ }),
    "Sự cố phần mềm từ seam",
  );
  await user.type(
    screen.getByRole("textbox", { name: /Mô tả chi tiết/ }),
    "Mô tả đủ dài để kiểm tra create qua fake service.",
  );
  await user.type(screen.getByRole("textbox", { name: /Vị trí/ }), "Tầng 3");
}

async function openConfirmation(user: ReturnType<typeof userEvent.setup>) {
  await user.click(
    await screen.findByRole("button", { name: "Xác nhận từ fake service" }),
  );
  const dialog = screen.getByRole("dialog");
  await user.type(
    within(dialog).getByRole("textbox", { name: "Ghi chú xử lý" }),
    "Đã kiểm tra kết quả",
  );
  return {
    dialog,
    submit: within(dialog).getByRole("button", {
      name: "Xác nhận từ fake service",
    }),
  };
}

describe("session boundaries for pending Application writes", () => {
  it.each(["create", "transition"] as const)(
    "expires the same session on a delayed %s 401 even after leaving the form",
    async (operation) => {
      const pending = deferred<ServiceRequest>();
      const service = fakeService();
      service[operation].mockReturnValue(pending.promise);
      service.actions.mockReturnValue([
        { id: "confirm", label: "Xác nhận từ fake service" },
      ]);
      const user = userEvent.setup();
      mountSessionPortal(
        service,
        operation === "create" ? "/requests/new" : `/requests/${provided.id}`,
      );
      if (operation === "create") {
        await fillCreateForm(user);
        await user.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
      } else {
        const { submit } = await openConfirmation(user);
        await user.click(submit);
      }
      await user.click(screen.getByRole("button", { name: "Test leave form" }));
      await act(async () =>
        pending.reject(new PortalError("401", "same session")),
      );
      expect(screen.getByTestId("session-location")).toHaveTextContent(
        "/requests:none:expired",
      );
      expect(sessionStorage.getItem("dxos-demo-role")).toBeNull();
    },
  );

  it.each(["create", "transition"] as const)(
    "ignores late %s success after logout and a new session",
    async (operation) => {
      const pending = deferred<ServiceRequest>();
      const service = fakeService();
      service[operation].mockReturnValue(pending.promise);
      service.actions.mockReturnValue([
        { id: "confirm", label: "Xác nhận từ fake service" },
      ]);
      const user = userEvent.setup();
      mountSessionPortal(
        service,
        operation === "create" ? "/requests/new" : `/requests/${provided.id}`,
      );
      if (operation === "create") {
        await fillCreateForm(user);
        await user.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
      } else {
        const { submit } = await openConfirmation(user);
        await user.click(submit);
      }
      await user.click(screen.getByRole("button", { name: "Test logout" }));
      await user.click(
        screen.getByRole("button", { name: "Test new session" }),
      );
      await act(async () => pending.resolve(provided));
      expect(screen.getByTestId("session-location")).toHaveTextContent(
        "/requests:employee:active",
      );
      expect(
        screen.queryByRole("button", { name: "Đóng thông báo" }),
      ).not.toBeInTheDocument();
      expect(service[operation]).toHaveBeenCalledTimes(1);
    },
  );

  it.each(["create", "transition"] as const)(
    "ignores late %s 401 from an old session",
    async (operation) => {
      const pending = deferred<ServiceRequest>();
      const service = fakeService();
      service[operation].mockReturnValue(pending.promise);
      service.actions.mockReturnValue([
        { id: "confirm", label: "Xác nhận từ fake service" },
      ]);
      const user = userEvent.setup();
      mountSessionPortal(
        service,
        operation === "create" ? "/requests/new" : `/requests/${provided.id}`,
      );
      if (operation === "create") {
        await fillCreateForm(user);
        await user.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
      } else {
        const { submit } = await openConfirmation(user);
        await user.click(submit);
      }
      await user.click(screen.getByRole("button", { name: "Test logout" }));
      await user.click(
        screen.getByRole("button", { name: "Test new session" }),
      );
      await act(async () =>
        pending.reject(new PortalError("401", "old session")),
      );
      expect(screen.getByTestId("session-location")).toHaveTextContent(
        "/requests:employee:active",
      );
      expect(sessionStorage.getItem("dxos-demo-role")).toBe("employee");
    },
  );

  it("does not redirect or notify for a create success after leaving the form", async () => {
    const pending = deferred<ServiceRequest>();
    const service = fakeService();
    service.create.mockReturnValue(pending.promise);
    const user = userEvent.setup();
    mountSessionPortal(service, "/requests/new");
    await fillCreateForm(user);
    await user.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
    await user.click(screen.getByRole("button", { name: "Test leave form" }));
    await act(async () => pending.resolve(provided));
    expect(screen.getByTestId("session-location")).toHaveTextContent(
      "/requests:employee:active",
    );
    expect(
      screen.queryByRole("button", { name: "Đóng thông báo" }),
    ).not.toBeInTheDocument();
  });
});

let assertNoNetworkOrFallback: () => void;
beforeEach(() => {
  sessionStorage.setItem("dxos-demo-role", "employee");
  const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => {
    throw new Error("Unexpected LIVE fetch");
  });
  const xhrSpy = vi
    .spyOn(XMLHttpRequest.prototype, "send")
    .mockImplementation(() => {
      throw new Error("Unexpected LIVE XMLHttpRequest");
    });
  const defaultSpies = [
    vi.spyOn(defaultService, "list"),
    vi.spyOn(defaultService, "get"),
    vi.spyOn(defaultService, "actions"),
    vi.spyOn(defaultService, "create"),
    vi.spyOn(defaultService, "transition"),
  ];
  assertNoNetworkOrFallback = () => {
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
    for (const spy of defaultSpies) expect(spy).not.toHaveBeenCalled();
  };
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.setAttribute("open", "");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    value: function (this: HTMLDialogElement) {
      this.removeAttribute("open");
    },
  });
  Object.defineProperty(HTMLElement.prototype, "scrollIntoView", {
    configurable: true,
    value: vi.fn(),
  });
});

afterEach(() => {
  cleanup();
  try {
    assertNoNetworkOrFallback();
  } finally {
    vi.restoreAllMocks();
    delete (HTMLDialogElement.prototype as { showModal?: unknown }).showModal;
    delete (HTMLDialogElement.prototype as { close?: unknown }).close;
    delete (HTMLElement.prototype as { scrollIntoView?: unknown })
      .scrollIntoView;
  }
});

describe("APP-09 provider injection with MOCK default", () => {
  it("keeps the existing composition-root MockRequestService as default", () => {
    render(
      <PortalProvider>
        <BindingProbe />
      </PortalProvider>,
    );
    expect(screen.getByTestId("binding")).toHaveTextContent("MOCK");
    expect(defaultService).toBeInstanceOf(MockRequestService);
  });

  it("accepts RequestService without pretending MvpRequestPort is compatible", () => {
    expectTypeOf<MvpRequestPort>().not.toExtend<RequestService>();
    expectTypeOf<
      NonNullable<Parameters<typeof PortalProvider>[0]["requestService"]>
    >().toEqualTypeOf<RequestService>();
    render(
      <PortalProvider requestService={fakeService()}>
        <BindingProbe />
      </PortalProvider>,
    );
    expect(screen.getByTestId("binding")).toHaveTextContent("injected");
  });

  it.each(["list", "approvals", "history"] as const)(
    "feeds the %s view through the injected read service",
    async (mode) => {
      const role = mode === "approvals" ? "processor" : "employee";
      sessionStorage.setItem("dxos-demo-role", role);
      const item: ServiceRequest = { ...provided, status: "pending" };
      const service = fakeService(item);
      const path = mode === "list" ? "/requests" : `/${mode}`;
      mountPortal(service, path);
      expect(await screen.findByText(new RegExp(item.title))).toBeVisible();
      expect(service.list).toHaveBeenCalledWith(role);
      expect(service.get).not.toHaveBeenCalled();
      if (mode === "history") {
        expect(screen.getByText(item.timeline[0].note)).toBeVisible();
      }
    },
  );

  it("reads detail and actions from the injected service", async () => {
    const service = fakeService();
    service.actions.mockReturnValue([
      { id: "confirm", label: "Xác nhận từ fake service" },
    ]);
    mountPortal(service, `/requests/${provided.id}`);
    expect(
      await screen.findByRole("button", { name: "Xác nhận từ fake service" }),
    ).toBeVisible();
    expect(service.get).toHaveBeenCalledWith(provided.id, "employee");
    expect(service.actions).toHaveBeenCalledWith(provided, "employee");
    expect(service.list).not.toHaveBeenCalled();
  });

  it("does not invent processor actions when the injected service supplies none", async () => {
    sessionStorage.setItem("dxos-demo-role", "processor");
    const item: ServiceRequest = { ...provided, status: "pending" };
    const service = fakeService(item);
    mountPortal(service, `/requests/${item.id}`);
    await screen.findByRole("heading", { name: item.title });
    expect(service.actions).toHaveBeenCalledWith(item, "processor");
    expect(
      screen.queryByRole("button", { name: "Phê duyệt" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Từ chối" }),
    ).not.toBeInTheDocument();
  });

  it("retries only a requested read using the same injected service", async () => {
    const service = fakeService();
    service.list.mockRejectedValueOnce(
      new PortalError("NETWORK", "Fake read unavailable"),
    );
    mountPortal(service);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Fake read unavailable",
    );
    expect(service.list).toHaveBeenCalledTimes(1);
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Tải lại dữ liệu" }));
    expect(await screen.findByText(provided.title)).toBeVisible();
    expect(service.list).toHaveBeenCalledTimes(2);
  });

  it.each(["late data", "late 401"])(
    "reloads on service replacement and ignores %s from the previous service",
    async (outcome) => {
      const pending = deferred<ServiceRequest[]>();
      const previous = fakeService();
      previous.list.mockReturnValue(pending.promise);
      const nextItem = { ...provided, title: "Dữ liệu của service mới" };
      const next = fakeService(nextItem);
      const view = render(
        <PortalProvider requestService={previous}>
          <ReadProbe />
          <SessionProbe />
        </PortalProvider>,
      );
      await waitFor(() => expect(previous.list).toHaveBeenCalledTimes(1));
      view.rerender(
        <PortalProvider requestService={next}>
          <ReadProbe />
          <SessionProbe />
        </PortalProvider>,
      );
      await waitFor(() =>
        expect(screen.getByTestId("read-result")).toHaveTextContent(
          nextItem.title,
        ),
      );
      await act(async () => {
        if (outcome === "late 401")
          pending.reject(new PortalError("401", "Old service expired"));
        else pending.resolve([provided]);
        await pending.promise.catch(() => undefined);
      });
      expect(screen.getByTestId("read-result")).toHaveTextContent(
        nextItem.title,
      );
      expect(screen.getByTestId("session")).toHaveTextContent(
        "employee:active",
      );
      expect(next.list).toHaveBeenCalledTimes(1);
    },
  );

  it("does not read through the injected service without a demo session", () => {
    sessionStorage.clear();
    const service = fakeService();
    mountPortal(service);
    expect(screen.getByTestId("session")).toHaveTextContent(
      "no-session:active",
    );
    expect(service.list).not.toHaveBeenCalled();
    expect(service.get).not.toHaveBeenCalled();
  });

  it.each(["it", "facility"] as const)(
    "sends %s create once through the injected service and reads its returned ID",
    async (kind) => {
      const item: ServiceRequest = {
        ...provided,
        id: "SEAM-created",
        kind,
        status: "pending",
      };
      const service = fakeService(item);
      const pending = deferred<ServiceRequest>();
      service.create.mockReturnValue(pending.promise);
      const user = userEvent.setup();
      mountPortal(service, `/requests/new?kind=${kind}`);
      await fillCreateForm(user);
      await user.dblClick(screen.getByRole("button", { name: "Gửi yêu cầu" }));
      expect(service.create).toHaveBeenCalledTimes(1);
      expect(screen.getByRole("button", { name: "Đang gửi…" })).toBeDisabled();
      expect(service.create).toHaveBeenCalledWith(
        {
          kind,
          title: "Sự cố phần mềm từ seam",
          description: "Mô tả đủ dài để kiểm tra create qua fake service.",
          priority: "normal",
          location: "Tầng 3",
        },
        "employee",
      );
      expect(service.create.mock.calls[0][0]).not.toHaveProperty("role");
      expect(service.create.mock.calls[0][0]).not.toHaveProperty("requester");
      await act(async () => {
        pending.resolve(item);
        await pending.promise;
      });
      expect(
        await screen.findByRole("heading", { name: item.title }),
      ).toBeVisible();
      expect(service.get).toHaveBeenCalledWith(item.id, "employee");
      expect(service.create).toHaveBeenCalledTimes(1);
    },
  );

  it("preserves local validation and focus before calling an injected create", async () => {
    const service = fakeService();
    mountPortal(service, "/requests/new");
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
    expect(
      screen.getByRole("textbox", { name: /Tiêu đề yêu cầu/ }),
    ).toHaveFocus();
    expect(service.create).not.toHaveBeenCalled();
  });

  it("keeps uncertain injected create locked with no retry or default fallback", async () => {
    const service = fakeService();
    service.create.mockRejectedValue(
      new PortalError("COMMIT_OUTCOME_UNKNOWN", "Fake write outcome unknown"),
    );
    const user = userEvent.setup();
    mountPortal(service, "/requests/new");
    await fillCreateForm(user);
    await user.dblClick(screen.getByRole("button", { name: "Gửi yêu cầu" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Fake write outcome unknown",
    );
    expect(screen.getByRole("button", { name: "Gửi yêu cầu" })).toBeDisabled();
    expect(service.create).toHaveBeenCalledTimes(1);
  });

  it("sends confirmation once with the supplied version, then reads authoritative detail", async () => {
    const confirmed: ServiceRequest = {
      ...provided,
      status: "confirmed",
      version: 18,
    };
    const service = fakeService();
    service.actions.mockReturnValue([
      { id: "confirm", label: "Xác nhận từ fake service" },
    ]);
    service.get.mockResolvedValueOnce(provided).mockResolvedValue(confirmed);
    const pending = deferred<ServiceRequest>();
    service.transition.mockReturnValue(pending.promise);
    const user = userEvent.setup();
    mountPortal(service, `/requests/${provided.id}`);
    const { submit } = await openConfirmation(user);
    await user.dblClick(submit);
    expect(service.transition).toHaveBeenCalledTimes(1);
    expect(submit).toBeDisabled();
    expect(service.transition).toHaveBeenCalledWith(
      provided.id,
      "confirm",
      "Đã kiểm tra kết quả",
      provided.version,
      "employee",
    );
    await act(async () => {
      pending.resolve(confirmed);
      await pending.promise;
    });
    expect(await screen.findAllByText("Đã xác nhận hoàn thành")).toHaveLength(
      2,
    );
    expect(service.get).toHaveBeenCalledTimes(2);
    expect(service.transition).toHaveBeenCalledTimes(1);
  });

  it("keeps uncertain injected confirmation locked through read reload without retry", async () => {
    const service = fakeService();
    service.actions.mockReturnValue([
      { id: "confirm", label: "Xác nhận từ fake service" },
    ]);
    service.transition.mockRejectedValue(
      new PortalError(
        "COMMIT_OUTCOME_UNKNOWN",
        "Fake confirmation outcome unknown",
      ),
    );
    const user = userEvent.setup();
    mountPortal(service, `/requests/${provided.id}`);
    const { dialog, submit } = await openConfirmation(user);
    await user.dblClick(submit);
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "Fake confirmation outcome unknown",
    );
    expect(submit).toBeDisabled();
    await user.click(within(dialog).getByRole("button", { name: "Đóng" }));
    await user.click(screen.getByRole("button", { name: "Tải lại chi tiết" }));
    expect(
      await screen.findByRole("button", { name: "Xác nhận từ fake service" }),
    ).toBeDisabled();
    expect(service.transition).toHaveBeenCalledTimes(1);
    expect(service.get).toHaveBeenCalledTimes(2);
  });

  it.each(["401", "403", "404"])(
    "keeps injected %s failures in the existing error/session boundary without fallback",
    async (code) => {
      const service = fakeService();
      service.list.mockRejectedValue(
        new PortalError(code, "Fake service read denied"),
      );
      mountPortal(service);
      if (code === "401") {
        await waitFor(() =>
          expect(screen.getByTestId("session")).toHaveTextContent(
            "no-session:expired",
          ),
        );
        expect(sessionStorage.getItem("dxos-demo-role")).toBeNull();
      } else {
        expect(await screen.findByRole("alert")).toHaveTextContent(
          "Fake service read denied",
        );
        expect(screen.getByTestId("session")).toHaveTextContent(
          "employee:active",
        );
      }
      expect(service.list).toHaveBeenCalledTimes(1);
    },
  );
});
