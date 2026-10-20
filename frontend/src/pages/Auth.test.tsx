import { beforeEach, afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { AuthPage } from "./Auth";

beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
});
afterEach(() => vi.restoreAllMocks());
function setup(mode: "login" | "register" = "login") {
  return render(
    <MemoryRouter>
      <AuthPage mode={mode} />
    </MemoryRouter>,
  );
}
async function fill(registration = false) {
  const user = userEvent.setup();
  if (registration)
    await user.type(screen.getByLabelText("Họ và tên"), "Người dùng thử");
  await user.type(
    screen.getByLabelText("Email", { exact: true }),
    "preview@example.edu.vn",
  );
  await user.type(
    screen.getByLabelText("Mật khẩu", { exact: true }),
    "Synthetic preview only!",
  );
  if (registration)
    await user.type(
      screen.getByLabelText("Xác nhận mật khẩu"),
      "Synthetic preview only!",
    );
  return user;
}

describe("email/password UI remains blocked", () => {
  it.each([
    "",
    "invalid",
    "a@",
    "a@@b.edu",
    "a b@example.edu",
    "a@example",
    "a@example..edu",
  ])("rejects invalid email %j accessibly", async (value) => {
    setup();
    fireEvent.change(screen.getByLabelText("Email", { exact: true }), {
      target: { value },
    });
    await userEvent.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(screen.getByLabelText("Email", { exact: true })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("Email", { exact: true })).toHaveFocus();
    expect(screen.getByText(/Nhập email hợp lệ/)).toBeVisible();
  });

  it.each(["preview@example.edu.vn", "preview+test@example.org"])(
    "accepts email %s but never signs in",
    async (value) => {
      setup();
      const user = await fill();
      await user.clear(screen.getByLabelText("Email", { exact: true }));
      await user.type(screen.getByLabelText("Email", { exact: true }), value);
      await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "BLOCKED FEATURE",
      );
      expect(screen.getByRole("alert")).toHaveTextContent("Chưa đăng nhập");
      expect(sessionStorage.length).toBe(0);
    },
  );

  it("validates registration name, minimum password length and confirmation", async () => {
    setup("register");
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Đăng ký tài khoản" }));
    expect(screen.getByText(/Họ và tên cần/)).toBeVisible();
    expect(screen.getByText(/Mật khẩu cần từ 12/)).toBeVisible();
    expect(screen.getByText("Mật khẩu xác nhận chưa khớp.")).toBeVisible();
    await fill(true);
    await user.clear(screen.getByLabelText("Xác nhận mật khẩu"));
    await user.type(
      screen.getByLabelText("Xác nhận mật khẩu"),
      "Different preview only!",
    );
    await user.click(screen.getByRole("button", { name: "Đăng ký tài khoản" }));
    expect(screen.getByLabelText("Xác nhận mật khẩu")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByLabelText("Xác nhận mật khẩu")).toHaveFocus();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it.each(["Sinh viên", "Giảng viên"])(
    "accepts %s only as a form selection without granting a session",
    async (role) => {
      setup("register");
      const user = await fill(true);
      expect(screen.getAllByRole("radio")).toHaveLength(2);
      expect(
        screen.queryByRole("radio", { name: /admin|cán bộ|người xử lý/i }),
      ).not.toBeInTheDocument();
      await user.click(screen.getByRole("radio", { name: role }));
      await user.click(
        screen.getByRole("button", { name: "Đăng ký tài khoản" }),
      );
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Chưa tạo tài khoản",
      );
      expect(localStorage.length).toBe(0);
      expect(sessionStorage.length).toBe(0);
      expect(screen.getByLabelText("Mật khẩu", { exact: true })).toHaveValue(
        "",
      );
      expect(screen.getByLabelText("Xác nhận mật khẩu")).toHaveValue("");
    },
  );

  it.each(["admin", "processor"])("rejects tampered role %s", async (value) => {
    setup("register");
    const user = await fill(true);
    screen
      .getByRole("radio", { name: "Sinh viên" })
      .setAttribute("value", value);
    await user.click(screen.getByRole("button", { name: "Đăng ký tài khoản" }));
    expect(
      screen.getByText("Vui lòng chọn Sinh viên hoặc Giảng viên."),
    ).toBeVisible();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(sessionStorage.length).toBe(0);
  });

  it.each(["login", "register"] as const)(
    "%s never sends, persists or logs credentials and preserves existing demo",
    async (mode) => {
      sessionStorage.setItem("dxos-demo-role", "employee");
      const fetchSpy = vi.spyOn(globalThis, "fetch");
      const xhrSpy = vi.spyOn(XMLHttpRequest.prototype, "send");
      const storageSpy = vi.spyOn(Storage.prototype, "setItem");
      const logs = [
        vi.spyOn(console, "log"),
        vi.spyOn(console, "warn"),
        vi.spyOn(console, "error"),
      ];
      setup(mode);
      const user = await fill(mode === "register");
      await user.dblClick(
        screen.getByRole("button", {
          name: mode === "login" ? "Đăng nhập" : "Đăng ký tài khoản",
        }),
      );
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "BLOCKED FEATURE",
      );
      expect(fetchSpy).not.toHaveBeenCalled();
      expect(xhrSpy).not.toHaveBeenCalled();
      expect(storageSpy).not.toHaveBeenCalled();
      for (const log of logs) expect(log).not.toHaveBeenCalled();
      expect(sessionStorage.getItem("dxos-demo-role")).toBe("employee");
    },
  );

  it("remembers only an opted-in email; removes it immediately when unchecked", async () => {
    const view = setup();
    const user = await fill();
    await user.click(screen.getByRole("checkbox", { name: "Ghi nhớ email" }));
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(await screen.findByRole("alert")).toBeVisible();
    expect({ ...localStorage }).toEqual({
      "dxos-remembered-email-v1": "preview@example.edu.vn",
    });
    view.unmount();
    setup();
    expect(screen.getByLabelText("Email", { exact: true })).toHaveValue(
      "preview@example.edu.vn",
    );
    expect(screen.getByLabelText("Mật khẩu", { exact: true })).toHaveValue("");
    await user.click(screen.getByRole("checkbox", { name: "Ghi nhớ email" }));
    expect(localStorage.length).toBe(0);
  });

  it("works when browser storage is unavailable", async () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("Storage unavailable");
    });
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("Storage unavailable");
    });
    setup();
    const user = await fill();
    await user.click(screen.getByRole("checkbox", { name: "Ghi nhớ email" }));
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "BLOCKED FEATURE",
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      "Trình duyệt không cho phép",
    );
  });

  it("shows a local form-checking state and clears secrets immediately", async () => {
    setup();
    const user = await fill();
    await user.click(screen.getByRole("button", { name: "Đăng nhập" }));
    expect(
      screen.getByRole("button", { name: /Đang kiểm tra/ }),
    ).toBeDisabled();
    expect(screen.getByLabelText("Mật khẩu", { exact: true })).toHaveValue("");
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Đăng nhập" })).toBeEnabled(),
    );
  });

  it("toggles password visibility and keeps recovery blocked with a separate Demo link", async () => {
    setup();
    const user = userEvent.setup();
    const password = screen.getByLabelText("Mật khẩu", { exact: true });
    expect(password).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "Hiện mật khẩu" }));
    expect(password).toHaveAttribute("type", "text");
    await user.click(screen.getByRole("button", { name: "Ẩn mật khẩu" }));
    expect(password).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "Quên mật khẩu?" }));
    expect(screen.getByRole("alert")).toHaveTextContent("BLOCKED FEATURE");
    expect(
      screen.getByRole("link", { name: "Vào phiên Demo" }),
    ).toHaveAttribute("href", "/session");
    expect(screen.getByRole("link", { name: "Đăng ký" })).toHaveAttribute(
      "href",
      "/register",
    );
  });
});
