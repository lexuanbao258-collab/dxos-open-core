import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import type { DemoRole } from "./domain/request";
import { App } from "./App";
import { demoControls, requestService } from "./services/portalService";

const ROLE_KEY = "dxos-demo-role";
const securityError = () => {
  throw new DOMException("Browser storage is blocked", "SecurityError");
};

beforeEach(() => {
  sessionStorage.clear();
  demoControls.reset();
  vi.stubGlobal("matchMedia", () => ({
    matches: false,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  }));
});

afterEach(() => {
  // Restore the sessionStorage getter before the shared setup clears storage.
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  demoControls.reset();
});

function setup(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <RouteProbe />
      <App />
    </MemoryRouter>,
  );
}

function RouteProbe() {
  return <span data-testid="session-route">{useLocation().pathname}</span>;
}

function expectRoute(path: string) {
  expect(screen.getByTestId("session-route")).toHaveTextContent(
    new RegExp(`^${path}$`),
  );
}

// The current controls have no role values/IDs. Select by their declared order,
// then verify the semantic role through service calls or persisted/restored state.
// Persona copy and radio label design belong to the separate UI review.
function roleChoice(role: DemoRole) {
  const choices = within(
    screen.getByRole("group", { name: "Vai trò trải nghiệm" }),
  ).getAllByRole("radio");
  expect(choices).toHaveLength(2);
  return choices[role === "employee" ? 0 : 1];
}

function expectNoSession() {
  expectRoute("/session");
  expect(
    screen.getByRole("heading", { name: "Bắt đầu trải nghiệm" }),
  ).toBeVisible();
  expect(screen.getByText("Chưa đăng nhập · Chế độ demo")).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Kết thúc phiên" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Vào phiên demo" })).toBeEnabled();
}

async function openSession(user: ReturnType<typeof userEvent.setup>) {
  // The route is the session contract; the sidebar label is held UI copy.
  const links = document.querySelectorAll<HTMLAnchorElement>(
    'nav a[href="/session"]',
  );
  expect(links).toHaveLength(1);
  await user.click(links[0]);
  expectRoute("/session");
}

describe("App demo session with optional sessionStorage", () => {
  it.each(["getItem", "setItem", "removeItem", "all", "getter"] as const)(
    "can start, use, switch and end a demo when %s throws SecurityError",
    async (failure) => {
      const storage = sessionStorage;
      if (failure === "getItem" || failure === "getter") {
        storage.setItem(ROLE_KEY, "processor");
      }
      const methods =
        failure === "all"
          ? (["getItem", "setItem", "removeItem"] as const)
          : failure === "getter"
            ? []
            : [failure];
      const blocked = methods.map((method) =>
        vi.spyOn(Storage.prototype, method).mockImplementation(securityError),
      );
      const blockedGetter =
        failure === "getter"
          ? vi
              .spyOn(window, "sessionStorage", "get")
              .mockImplementation(securityError)
          : undefined;
      const list = vi.spyOn(requestService, "list");
      const fetchSpy = vi.spyOn(globalThis, "fetch");
      const xhrSpy = vi.spyOn(XMLHttpRequest.prototype, "send");
      const user = userEvent.setup();
      const view = setup("/requests");

      expectNoSession();
      expect(list).not.toHaveBeenCalled();
      await user.click(screen.getByRole("button", { name: "Vào phiên demo" }));
      expectRoute("/");
      await user.click(screen.getByRole("link", { name: "Yêu cầu của tôi" }));
      expect(
        await screen.findByRole("link", { name: "Xem DX-1048" }),
      ).toBeVisible();
      expect(list).toHaveBeenCalledWith("employee");

      await openSession(user);
      await user.click(roleChoice("processor"));
      await user.click(
        screen.getByRole("button", { name: "Tiếp tục với vai trò đã chọn" }),
      );
      expectRoute("/");
      await user.click(screen.getByRole("link", { name: "Yêu cầu của tôi" }));
      expect(
        await screen.findByRole("link", { name: "Xem DX-1047" }),
      ).toBeVisible();
      expect(list).toHaveBeenCalledWith("processor");

      await openSession(user);
      await user.click(screen.getByRole("button", { name: "Kết thúc phiên" }));
      expectNoSession();
      expect(screen.getByRole("status")).toHaveTextContent(
        "Đã kết thúc phiên demo.",
      );
      const callsAfterLogout = list.mock.calls.length;
      await user.click(screen.getByRole("link", { name: "Yêu cầu của tôi" }));
      expectNoSession();
      expect(list).toHaveBeenCalledTimes(callsAfterLogout);
      expect(fetchSpy).not.toHaveBeenCalled();
      expect(xhrSpy).not.toHaveBeenCalled();
      for (const spy of blocked) expect(spy).toHaveBeenCalled();
      if (blockedGetter) expect(blockedGetter).toHaveBeenCalled();
      expect(Object.keys(storage).every((key) => key === ROLE_KEY)).toBe(true);

      if (failure === "all" || failure === "getter") {
        // A new provider must not invent a session from the previous React state.
        view.unmount();
        setup();
        expectNoSession();
      }
    },
  );

  it.each(["button", "401"] as const)(
    "clears a restored demo in memory when removeItem fails during expiry via %s",
    async (trigger) => {
      sessionStorage.setItem(ROLE_KEY, "processor");
      const remove = vi
        .spyOn(Storage.prototype, "removeItem")
        .mockImplementation(securityError);
      const user = userEvent.setup();
      if (trigger === "401") demoControls.setFault("401");
      setup(trigger === "401" ? "/requests" : "/session");
      if (trigger === "button") {
        await user.click(
          screen.getByRole("button", { name: "Mô phỏng hết hạn" }),
        );
      }
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "401 mô phỏng · Phiên đã hết hạn.",
      );
      expectNoSession();
      expect(remove).toHaveBeenCalledWith(ROLE_KEY);
      await user.click(screen.getByRole("link", { name: "Yêu cầu của tôi" }));
      expectNoSession();
      await user.click(screen.getByRole("button", { name: "Vào phiên demo" }));
      expectRoute("/");
      await openSession(user);
      expect(
        screen.getByRole("button", { name: "Kết thúc phiên" }),
      ).toBeEnabled();
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    },
  );

  it.each([null, "admin", "invalid"])(
    "keeps protected routes closed for stored role %j",
    (role) => {
      if (role !== null) sessionStorage.setItem(ROLE_KEY, role);
      const list = vi.spyOn(requestService, "list");
      setup("/approvals");
      expectNoSession();
      expect(list).not.toHaveBeenCalled();
    },
  );

  it.each(["employee", "processor"] as const)(
    "preserves persistence, restoration and logout for %s when storage works",
    async (role) => {
      const user = userEvent.setup();
      let view = setup();
      expectNoSession();
      await user.click(roleChoice(role));
      await user.click(screen.getByRole("button", { name: "Vào phiên demo" }));
      expect(sessionStorage.getItem(ROLE_KEY)).toBe(role);
      view.unmount();
      view = setup("/session");
      expect(
        screen.getByRole("heading", { name: "Phiên demo đang hoạt động" }),
      ).toBeVisible();
      expect(roleChoice(role)).toBeChecked();
      await user.click(screen.getByRole("button", { name: "Kết thúc phiên" }));
      expect(sessionStorage.getItem(ROLE_KEY)).toBeNull();
      view.unmount();
      setup("/requests");
      expectNoSession();
    },
  );

  it("removes a persisted demo role on expiry when storage works", async () => {
    sessionStorage.setItem(ROLE_KEY, "employee");
    setup("/session");
    await userEvent.click(
      screen.getByRole("button", { name: "Mô phỏng hết hạn" }),
    );
    expectNoSession();
    expect(sessionStorage.getItem(ROLE_KEY)).toBeNull();
    expect(screen.getByRole("alert")).toHaveTextContent("Phiên đã hết hạn.");
  });

  it.each(["/login", "/register"])(
    "renders %s through PortalProvider when storage access is blocked",
    (path) => {
      vi.spyOn(window, "sessionStorage", "get").mockImplementation(
        securityError,
      );
      setup(path);
      expect(
        screen.getByRole("heading", {
          name: path === "/login" ? "Chào mừng trở lại" : "Tạo tài khoản DX-OS",
        }),
      ).toBeVisible();
      expect(
        within(screen.getByRole("form")).getByRole("button", {
          name: path === "/login" ? "Đăng nhập" : "Đăng ký tài khoản",
        }),
      ).toBeVisible();
    },
  );
});
