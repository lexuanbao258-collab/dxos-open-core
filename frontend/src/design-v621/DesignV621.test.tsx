// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, resolve, sep } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import DesignV621 from "./DesignV621";
import { AUDIT_TICKET_ID, PREVIEW_AUDIT, PREVIEW_TICKETS } from "./fixtures";
import { textFitsInput } from "./hooks";
import {
  auditLabel,
  counters,
  dateKey,
  emptyFilters,
  numbers,
  selectRows,
  validateCreate,
} from "./logic";

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/design-v621/*" element={<DesignV621 />} />
      </Routes>
    </MemoryRouter>,
  );
}
const ids = (rows: readonly { id: string }[]) => rows.map((r) => r.id);

let fetchSpy: ReturnType<typeof vi.fn>;
beforeEach(() => {
  fetchSpy = vi.fn();
  vi.stubGlobal("fetch", fetchSpy);
  vi.spyOn(window, "scrollTo").mockImplementation(() => undefined);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("preview fixtures (verbatim from HTML V6.2.1)", () => {
  it("keeps exactly 8 tickets and 3 audit events", () => {
    expect(ids(PREVIEW_TICKETS)).toEqual([
      "DX-1048",
      "DX-1047",
      "DX-1046",
      "DX-1045",
      "DX-1044",
      "DX-1043",
      "DX-1042",
      "DX-1040",
    ]);
    expect(PREVIEW_AUDIT).toHaveLength(3);
    expect(AUDIT_TICKET_ID).toBe("DX-1046");
  });

  it("never grants allowedActions and is immutable", () => {
    for (const t of PREVIEW_TICKETS) {
      expect(t.allowedActions).toEqual([]);
      expect(Object.isFrozen(t)).toBe(true);
      expect(Object.isFrozen(t.allowedActions)).toBe(true);
    }
    expect(Object.isFrozen(PREVIEW_TICKETS)).toBe(true);
  });

  it("only DX-1046 carries a version and an assignee", () => {
    const withVersion = PREVIEW_TICKETS.filter((t) => t.version !== undefined);
    expect(ids(withVersion)).toEqual(["DX-1046"]);
    expect(withVersion[0].version).toBe(2);
  });
});

describe("preview isolation", () => {
  it("does not import domain, services, fixtures or context of the app", () => {
    const files = [
      ...readdirSync(__dirname),
      ...readdirSync(join(__dirname, "components")).map((f) => `components/${f}`),
      ...readdirSync(join(__dirname, "pages")).map((f) => `pages/${f}`),
    ].filter((f) => /\.tsx?$/.test(f) && !f.endsWith(".test.tsx"));
    expect(files.length).toBeGreaterThan(8);
    for (const f of files) {
      const src = readFileSync(join(__dirname, f), "utf8");
      const imports = [...src.matchAll(/from\s+"([^"]+)"/g)].map((m) => m[1]);
      for (const spec of imports.filter((i) => i.startsWith("."))) {
        const target = resolve(dirname(join(__dirname, f)), spec);
        expect(
          target === __dirname || target.startsWith(__dirname + sep),
          `${f} imports ${spec} outside design-v621`,
        ).toBe(true);
      }
    }
  });

  it("makes no network calls on any screen", async () => {
    for (const path of [
      "/design-v621",
      "/design-v621/mine",
      "/design-v621/queue",
      "/design-v621/detail/DX-1046?from=queue",
      "/design-v621/create",
      "/design-v621/integration",
    ]) {
      renderAt(path);
      cleanup();
    }
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe("list logic", () => {
  it("mine: only the viewer's requests, newest update first", () => {
    const open = selectRows(PREVIEW_TICKETS, "mine", "open", emptyFilters());
    expect(ids(open)).toEqual(["DX-1048", "DX-1046", "DX-1045", "DX-1043"]);
    expect(
      ids(selectRows(PREVIEW_TICKETS, "mine", "closed", emptyFilters())),
    ).toEqual(["DX-1042"]);
    expect(counters(PREVIEW_TICKETS, "mine")).toEqual({
      all: 5,
      open: 4,
      closed: 1,
      processing: 1,
    });
  });

  it("queue: high priority first, then oldest update first", () => {
    expect(
      ids(selectRows(PREVIEW_TICKETS, "queue", "open", emptyFilters())),
    ).toEqual(["DX-1048", "DX-1047", "DX-1040", "DX-1043", "DX-1045", "DX-1046"]);
    expect(numbers(PREVIEW_TICKETS)).toEqual({
      all: 8,
      open: 6,
      submitted: 3,
      processing: 2,
      high: 2,
    });
  });

  it("search matches id, title, requester and service, case-insensitively", () => {
    const find = (query: string) =>
      ids(
        selectRows(PREVIEW_TICKETS, "queue", "all", {
          ...emptyFilters(),
          query,
        }),
      );
    expect(find("dx-1048")).toEqual(["DX-1048"]);
    expect(find("LOTUS")).toEqual(["DX-1047"]);
    expect(find("thu hà")).toEqual(["DX-1040"]);
    expect(find("cơ sở vật chất")).toHaveLength(3);
    expect(find("  ")).toHaveLength(8);
    expect(find("không có gì")).toEqual([]);
  });

  it("combines service, status and priority filters", () => {
    const rows = selectRows(PREVIEW_TICKETS, "queue", "all", {
      ...emptyFilters(),
      service: "facility.v1",
      status: "IN_PROGRESS",
      priority: "Cao",
    });
    expect(ids(rows)).toEqual(["DX-1047"]);
  });

  it("parses both date formats", () => {
    expect(dateKey("09:15 20/10/2026")).toBeGreaterThan(dateKey("16/10/2026"));
    expect(dateKey("")).toBe(0);
  });

  it("translates step codes inside audit text", () => {
    expect(auditLabel("SUBMITTED → TRIAGED v1")).toBe("Đã gửi → Đã tiếp nhận v1");
  });
});

describe("create validation", () => {
  const ok = { service: "helpdesk.v1", title: "Hello", description: "x".repeat(15), location: "" };
  it("accepts the minimum valid IT request without a location", () => {
    expect(validateCreate(ok)).toEqual({});
  });
  it("requires title >=5, description >=15 and facility location >=2", () => {
    expect(
      validateCreate({ ...ok, title: " a ", description: "short" }),
    ).toEqual({
      title: "Tiêu đề cần ít nhất 5 ký tự.",
      description: "Mô tả cần ít nhất 15 ký tự.",
    });
    expect(validateCreate({ ...ok, service: "facility.v1" }).location).toBe(
      "Cần nhập vị trí khi yêu cầu Cơ sở vật chất.",
    );
    expect(
      validateCreate({ ...ok, service: "facility.v1", location: "P1" }),
    ).toEqual({});
  });
});

describe("shell", () => {
  it("marks the current section and keeps a detail page under its origin", () => {
    renderAt("/design-v621/detail/DX-1046?from=queue");
    const nav = screen.getByRole("navigation", { name: "Điều hướng chính" });
    expect(within(nav).getByRole("link", { name: "Bàn xử lý" })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(within(nav).getByRole("link", { name: "Tổng quan" })).not.toHaveAttribute(
      "aria-current",
    );
    const bottom = screen.getByRole("navigation", {
      name: "Điều hướng nhanh trên điện thoại",
    });
    expect(within(bottom).getAllByRole("link")).toHaveLength(4);
  });

  it("states MOCK/DEV, offers a skip link and one polite live region", () => {
    renderAt("/design-v621");
    expect(screen.getByText("MOCK / DEV")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đến nội dung chính" })).toHaveAttribute(
      "href",
      "#main",
    );
    const live = document.querySelectorAll("[aria-live]");
    expect(live).toHaveLength(1);
    expect(live[0]).toHaveAttribute("aria-live", "polite");
    expect(document.title).toBe("Tổng quan · OpenTrix V6.2.1 (MOCK)");
  });

  it("brands as OpenTrix with the DX-OS Open-Core note", () => {
    renderAt("/design-v621");
    expect(screen.getByText("OpenTrix")).toBeInTheDocument();
    expect(screen.getByText("Service Portal · DX-OS Open-Core")).toBeInTheDocument();
  });

  it("unknown preview paths fall back to the overview", () => {
    renderAt("/design-v621/does-not-exist");
    expect(screen.getByRole("heading", { name: "Yêu cầu đang mở" })).toBeInTheDocument();
  });
});

describe("overview", () => {
  it("lists up to 5 open requests and the shared 5-step flow", () => {
    renderAt("/design-v621");
    expect(
      screen.getByText("6 yêu cầu đang mở · 3 chờ tiếp nhận · 2 đang xử lý"),
    ).toBeInTheDocument();
    expect(document.querySelectorAll(".home-row")).toHaveLength(5);
    const aside = screen.getByLabelText("Kiến trúc nghiệp vụ");
    for (const label of ["Đã gửi", "Đã tiếp nhận", "Đang xử lý", "Đã giải quyết", "Đã xác nhận"])
      expect(within(aside).getByText(label)).toBeInTheDocument();
  });
});

describe("request list (mine / queue)", () => {
  it("renders segment counts and a labelled search box", () => {
    renderAt("/design-v621/queue");
    expect(screen.getByRole("button", { name: /Cần theo dõi/ })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toBeInTheDocument();
    expect(screen.getByText("6 / 8 yêu cầu")).toBeInTheDocument();
    expect(
      screen.getByRole("table", { name: /Hàng đợi/ }),
    ).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Phụ trách" })).toBeInTheDocument();
  });

  it("mine has no assignee column and shows 4 open rows", () => {
    renderAt("/design-v621/mine");
    expect(screen.queryByRole("columnheader", { name: "Phụ trách" })).toBeNull();
    expect(screen.getByText("4 / 5 yêu cầu")).toBeInTheDocument();
  });

  it("filters by typing, shows chips and resets to the empty state action", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    await user.type(screen.getByLabelText("Tìm kiếm yêu cầu"), "zzz");
    expect(screen.getByText("Không có yêu cầu phù hợp")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xóa bộ lọc" }));
    // Reset clears every filter; the query is cleared by the same action.
    expect(screen.getByText("6 / 8 yêu cầu")).toBeInTheDocument();
  });

  it("applies service filter and shows a chip", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    await user.selectOptions(screen.getByLabelText("Lọc dịch vụ"), "facility.v1");
    expect(document.getElementById("chips")).toHaveTextContent("Cơ sở vật chất");
    expect(screen.getByText("3 / 8 yêu cầu")).toBeInTheDocument();
  });

  it("filter sheet applies a draft and shows the active filter count", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    await user.click(screen.getByRole("button", { name: /^Lọc/ }));
    const dialog = screen.getByRole("dialog", { name: "Lọc yêu cầu" });
    await user.selectOptions(within(dialog).getByLabelText("Ưu tiên"), "Cao");
    await user.click(within(dialog).getByRole("button", { name: "Áp dụng" }));
    expect(screen.getByRole("button", { name: /Lọc \(1\)/ })).toBeInTheDocument();
    expect(screen.getByText("2 / 8 yêu cầu")).toBeInTheDocument();
  });

  it("every row links to its detail with the origin preserved", () => {
    renderAt("/design-v621/queue");
    const link = document.querySelector<HTMLAnchorElement>('a.row-link[data-ticket="DX-1046"]')!;
    expect(link.getAttribute("href")).toBe("/design-v621/detail/DX-1046?from=queue");
  });

  it("queue keyboard: J/K move focus, E reports no server-granted action, Esc returns to search", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    const rows = () =>
      [...document.querySelectorAll<HTMLAnchorElement>("a.row-link[data-ticket]")];
    // jsdom has no layout, so every link counts as shown.
    vi.spyOn(Element.prototype, "getClientRects").mockReturnValue([{}] as unknown as DOMRectList);
    await user.keyboard("j");
    expect(document.activeElement).toBe(rows()[0]);
    await user.keyboard("j");
    expect(document.activeElement).toBe(rows()[1]);
    await user.keyboard("k");
    expect(document.activeElement).toBe(rows()[0]);
    await user.keyboard("e");
    expect(document.getElementById("kbd-feedback")).toHaveTextContent(
      "DX-1048: chưa được máy chủ cấp thao tác xử lý.",
    );
    expect(document.getElementById("announcement")).toHaveTextContent("DX-1048");
    await user.keyboard("{Escape}");
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toHaveFocus();
    // Typing into the search box must not trigger shortcuts.
    await user.keyboard("jk");
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toHaveValue("jk");
  });

  it("moving focus clears stale shortcut feedback", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    vi.spyOn(Element.prototype, "getClientRects").mockReturnValue([{}] as unknown as DOMRectList);
    await user.keyboard("je");
    expect(document.getElementById("kbd-feedback")).not.toHaveAttribute("hidden");
    await user.keyboard("j");
    expect(document.getElementById("kbd-feedback")).toHaveAttribute("hidden");
  });

  it("mine has no keyboard shortcuts hint", () => {
    renderAt("/design-v621/mine");
    expect(document.querySelector(".kbd-hint")).toBeNull();
  });
});

describe("QA scenarios (V6.2 N-10..N-13, V6.2.1 N-13)", () => {
  async function simulate(user: ReturnType<typeof userEvent.setup>, value: string) {
    await user.selectOptions(screen.getByLabelText("Tình huống"), value);
  }
  async function go(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(
      within(screen.getByRole("navigation", { name: "Điều hướng chính" })).getByRole("link", { name }),
    );
  }

  it("shows distinct state text and an independent reset button, announced via #announcement only", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/integration");
    await simulate(user, "unknown");
    expect(document.getElementById("announcement")).toHaveTextContent(
      "Tình huống giao diện: Kết quả ghi chưa xác định",
    );
    const state = document.getElementById("qa-banner-state")!;
    expect(state).toHaveTextContent("Mô phỏng: Kết quả ghi chưa xác định");
    expect(state).toHaveTextContent("Mô phỏng: Ghi chưa xác định"); // mobile text
    expect(state).not.toHaveAttribute("aria-live");
    const reset = screen.getByRole("button", { name: "Tắt mô phỏng" });
    expect(reset).toHaveTextContent("Tắt");
    expect(state.contains(reset)).toBe(false);
  });

  it.each([
    ["loading", "Đang tải danh sách", "status"],
    ["empty", "Không có yêu cầu phù hợp", "status"],
    ["error", "Không thể đọc danh sách", "alert"],
    ["success", "Kiểm tra thành công", "status"],
    ["unknown", "Kết quả ghi chưa xác định", "alert"],
  ])("list renders the %s state", async (scenario, heading, role) => {
    const user = userEvent.setup();
    renderAt("/design-v621/integration");
    await simulate(user, scenario);
    await go(user, "Bàn xử lý");
    const box = screen.getByRole(role, { name: "" , hidden: false });
    expect(box).toHaveTextContent(heading);
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("unknown outcome offers history, never a retry, and locks the create form", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/integration");
    await simulate(user, "unknown");
    await go(user, "Bàn xử lý");
    expect(screen.getByRole("link", { name: "Xem lịch sử mẫu" })).toHaveAttribute(
      "href",
      "/design-v621/detail/DX-1046?from=queue",
    );
    expect(screen.queryByRole("button", { name: /thử lại|gửi lại|retry/i })).toBeNull();
    await go(user, "Tạo yêu cầu");
    const submit = screen.getByRole("button", { name: "Kiểm tra biểu mẫu" });
    expect(submit).toBeDisabled();
    expect(screen.getByRole("alert")).toHaveTextContent("Không gửi lại lệnh");
    // Submitting by keyboard while locked must not validate or announce.
    await user.type(screen.getByLabelText(/Tiêu đề/), "{Enter}");
    expect(screen.queryByText("Biểu mẫu hợp lệ")).toBeNull();
  });

  it("reset returns to normal, focuses <main> and announces", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/integration");
    await simulate(user, "error");
    await user.click(screen.getByRole("button", { name: "Tắt mô phỏng" }));
    expect(screen.queryByRole("button", { name: "Tắt mô phỏng" })).toBeNull();
    expect(document.getElementById("main")).toHaveFocus();
    expect(document.getElementById("announcement")).toHaveTextContent(
      "Đã tắt mô phỏng trạng thái giao diện.",
    );
    expect(screen.getByLabelText("Tình huống")).toHaveValue("normal");
  });

  it("reload simulates loading briefly then recovers without any request", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      renderAt("/design-v621/mine");
      await user.click(screen.getByRole("button", { name: "Tải lại dữ liệu mẫu" }));
      expect(screen.getByText("Đang tải danh sách")).toBeInTheDocument();
      await act(async () => {
        await vi.advanceTimersByTimeAsync(350);
      });
      expect(screen.queryByText("Đang tải danh sách")).toBeNull();
      expect(document.getElementById("announcement")).toHaveTextContent(
        "Đã tải lại danh sách mẫu.",
      );
      expect(fetchSpy).not.toHaveBeenCalled();
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("request detail", () => {
  it("shows progress, audit trail, version and NO write actions for DX-1046", () => {
    renderAt("/design-v621/detail/DX-1046?from=queue");
    expect(screen.getByRole("heading", { name: "Cài đặt môi trường phát triển cho laptop mới" })).toBeInTheDocument();
    expect(screen.getByText("Đang xử lý · Bước 3/5")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử xử lý (3)")).toBeInTheDocument();
    const items = [...document.querySelectorAll(".audit-item strong")].map((e) => e.textContent);
    expect(items).toEqual([
      "Đã tiếp nhận → Đang xử lý v2",
      "Đã gửi → Đã tiếp nhận v1",
      "Khởi tạo v0",
    ]);
    expect(screen.getByText("v2", { exact: true })).toBeInTheDocument();
    expect(
      screen.getByText("Không có thao tác xử lý nào được máy chủ cấp cho bản xem trước này."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText(/BLOCKED #26/)).toBeInTheDocument();
  });

  it("states missing data instead of inventing it", () => {
    renderAt("/design-v621/detail/DX-1048?from=mine");
    expect(screen.getByText("Chưa có dữ liệu dấu vết được cung cấp cho yêu cầu này.")).toBeInTheDocument();
    expect(screen.getAllByText("Chưa có dữ liệu").length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Đã gửi · Bước 1/5")).toBeInTheDocument();
  });

  it("back link follows the origin and unknown ids show a not-found heading", () => {
    renderAt("/design-v621/detail/DX-9999?from=overview");
    expect(screen.getByRole("heading", { name: "Không tìm thấy yêu cầu" })).toBeInTheDocument();
    expect(document.querySelector(".detail-back")).toHaveAttribute("href", "/design-v621");
  });

  it("sanitises an arbitrary origin param to mine", () => {
    renderAt("/design-v621/detail/DX-1046?from=javascript:alert(1)");
    expect(document.querySelector(".detail-back")).toHaveAttribute(
      "href",
      "/design-v621/mine",
    );
    expect(document.querySelector(".detail-back")).toHaveTextContent("Yêu cầu của tôi");
  });
});

describe("create request form", () => {
  it("validates, focuses the first invalid field and never claims submission", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/create");
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveFocus();
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByText("Tiêu đề cần ít nhất 5 ký tự.")).toBeInTheDocument();
    expect(screen.getByText("Mô tả cần ít nhất 15 ký tự.")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Vui lòng kiểm tra các trường đã đánh dấu.");
    await user.type(screen.getByLabelText(/Tiêu đề/), "Không vào được VPN");
    await user.type(screen.getByLabelText(/Mô tả chi tiết/), "Không đăng nhập được VPN từ sáng nay");
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    expect(screen.getByText("Biểu mẫu hợp lệ")).toBeInTheDocument();
    expect(screen.getByText(/Chưa gửi yêu cầu lên máy chủ, chưa tạo ticket/)).toBeInTheDocument();
    expect(document.getElementById("announcement")).toHaveTextContent("Biểu mẫu hợp lệ. Chưa gửi yêu cầu.");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("facility makes location required, IT makes it optional", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/create");
    const location = screen.getByLabelText(/Thiết bị \/ hệ thống liên quan/);
    expect(location).not.toBeRequired();
    await user.click(screen.getByRole("radio", { name: /Cơ sở vật chất/ }));
    const facility = screen.getByLabelText(/Vị trí \/ khu vực/);
    expect(facility).toBeRequired();
    expect(facility).toHaveAttribute("placeholder", "Ví dụ: Phòng họp Lotus");
    await user.type(screen.getByLabelText(/Tiêu đề/), "Điều hòa hỏng");
    await user.type(screen.getByLabelText(/Mô tả chi tiết/), "Điều hòa không làm mát từ sáng");
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    expect(facility).toHaveFocus();
    expect(screen.getByText("Cần nhập vị trí khi yêu cầu Cơ sở vật chất.")).toBeInTheDocument();
  });

  it("reset clears values, errors and feedback", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/create");
    await user.type(screen.getByLabelText(/Tiêu đề/), "abc");
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    await user.click(screen.getByRole("button", { name: "Làm lại" }));
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveValue("");
    expect(screen.queryByText("Tiêu đề cần ít nhất 5 ký tự.")).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });
});

describe("integration screen", () => {
  it("marks every capability BLOCKED and links the baseline references", () => {
    renderAt("/design-v621/integration");
    expect(screen.getAllByText("BLOCKED")).toHaveLength(7);
    expect(screen.getByText("PR #63")).toBeInTheDocument();
    expect(screen.getByText(/expectedVersion/)).toBeInTheDocument();
    expect(screen.getByText(/không tải\s+tài nguyên từ bên thứ ba/)).toBeInTheDocument();
  });
});

describe("search placeholder fitting (V6.2.1 N-09, incl. 361–370px)", () => {
  function fakeInput(clientWidth: number) {
    const input = document.createElement("input");
    document.body.append(input);
    Object.defineProperty(input, "clientWidth", { value: clientWidth });
    return input;
  }
  function stubCanvas(textWidth: number) {
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      font: "",
      measureText: () => ({ width: textWidth }),
    } as unknown as CanvasRenderingContext2D);
  }

  it("keeps the long text only while it fits, at every width 320–420px", () => {
    stubCanvas(110);
    const padding = 41 + 13; // matches .search-box .input padding
    for (let w = 320; w <= 420; w++) {
      const input = fakeInput(w - 190);
      input.style.paddingLeft = "41px";
      input.style.paddingRight = "13px";
      const expected = 110 <= w - 190 - padding;
      expect(textFitsInput(input, "Tìm mã hoặc tiêu đề"), `${w}px`).toBe(expected);
      input.remove();
    }
  });

  it("does not guess when the input is not laid out", () => {
    stubCanvas(9999);
    expect(textFitsInput(fakeInput(0), "x")).toBe(true);
  });

  it("falls back to the full text when canvas is unavailable", () => {
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);
    expect(textFitsInput(fakeInput(500), "x")).toBe(true);
  });
});
