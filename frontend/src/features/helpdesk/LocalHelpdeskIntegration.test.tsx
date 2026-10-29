import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { helpdeskId, helpdeskWire } from "../../test/helpdeskFixtures";
import LocalHelpdeskIntegration from "./LocalHelpdeskIntegration";

beforeEach(() => vi.stubGlobal("location", new URL("https://localhost:8443")));
afterEach(() => vi.unstubAllGlobals());
function mount(path = "/mvp/helpdesk") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <LocalHelpdeskIntegration />
    </MemoryRouter>,
  );
}

it("requires the real BFF session even when a demo persona is set", async () => {
  sessionStorage.setItem("dxos-demo-role", "processor");
  const fetcher = vi
    .fn<typeof fetch>()
    .mockResolvedValue(Response.json({ authenticated: false }));
  vi.stubGlobal("fetch", fetcher);
  mount();
  expect(
    await screen.findByRole("link", { name: "Đăng nhập Keycloak" }),
  ).toHaveAttribute("href", "/oauth2/authorization/keycloak");
  expect(fetcher).toHaveBeenCalledTimes(1);
});

it("creates once through BFF without a demo role and renders only the returned request", async () => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockImplementation(async (url, init) => {
      if (url === "/bff/session")
        return Response.json({ authenticated: true, csrfToken: "csrf-test" });
      if (init?.method === "POST")
        return Response.json(helpdeskWire(), { status: 201 });
      if (String(url).includes("?limit="))
        return Response.json({ items: [helpdeskWire()], nextCursor: null });
      if (String(url).endsWith("/history")) return Response.json([]);
      return Response.json(helpdeskWire());
    });
  vi.stubGlobal("fetch", fetcher);
  mount("/mvp/helpdesk/new");
  const user = userEvent.setup();
  await user.type(
    await screen.findByLabelText(/Tiêu đề/),
    "Disposable integration test",
  );
  await user.type(
    screen.getByLabelText(/Mô tả/),
    "Disposable test description",
  );
  await user.click(screen.getByRole("button", { name: "Gửi yêu cầu IT" }));
  await waitFor(() =>
    expect(
      fetcher.mock.calls.filter(([, init]) => init?.method === "POST"),
    ).toHaveLength(1),
  );
  expect(sessionStorage.getItem("dxos-demo-role")).toBeNull();
  expect(
    fetcher.mock.calls.find(([, init]) => init?.method === "POST"),
  ).toEqual([
    "/bff/api/v1/mvp/requests",
    expect.objectContaining({
      headers: expect.objectContaining({ "X-CSRF-TOKEN": "csrf-test" }),
    }),
  ]);
  expect(await screen.findByText(helpdeskId)).toBeInTheDocument();
});

it("expires the local session on API 401 and removes the request UI", async () => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockResolvedValueOnce(
      Response.json({ authenticated: true, csrfToken: "csrf-test" }),
    )
    .mockResolvedValueOnce(new Response("", { status: 401 }));
  vi.stubGlobal("fetch", fetcher);
  mount();
  expect(
    await screen.findByRole("link", { name: "Đăng nhập Keycloak" }),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "Tạo yêu cầu IT" }),
  ).not.toBeInTheDocument();
  expect(fetcher).toHaveBeenCalledTimes(2);
});

it("shows a session network error without falling back to mocks", async () => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockRejectedValue(new TypeError("offline"));
  vi.stubGlobal("fetch", fetcher);
  mount();
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Không thể kiểm tra phiên BFF",
  );
  expect(fetcher).toHaveBeenCalledTimes(1);
});

it("keeps an unknown create blocked after navigation without replay", async () => {
  const fetcher = vi
    .fn<typeof fetch>()
    .mockImplementation(async (url, init) => {
      if (url === "/bff/session")
        return Response.json({ authenticated: true, csrfToken: "csrf-test" });
      if (init?.method === "POST")
        return Response.json(
          { code: "COMMIT_OUTCOME_UNKNOWN" },
          { status: 503 },
        );
      return Response.json({ items: [], nextCursor: null });
    });
  vi.stubGlobal("fetch", fetcher);
  mount("/mvp/helpdesk/new");
  const user = userEvent.setup();
  await user.type(
    await screen.findByLabelText(/Tiêu đề/),
    "Disposable unknown write",
  );
  await user.type(
    screen.getByLabelText(/Mô tả/),
    "Connection lost after commit",
  );
  await user.click(screen.getByRole("button", { name: "Gửi yêu cầu IT" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Chưa xác định kết quả ghi",
  );
  expect(screen.getByRole("button", { name: "Gửi yêu cầu IT" })).toBeDisabled();
  await user.click(screen.getByRole("link", { name: "Danh sách Helpdesk" }));
  await user.click(await screen.findByRole("link", { name: "Tạo yêu cầu IT" }));
  expect(screen.getByRole("button", { name: "Gửi yêu cầu IT" })).toBeDisabled();
  expect(
    fetcher.mock.calls.filter(([, init]) => init?.method === "POST"),
  ).toHaveLength(1);
});
