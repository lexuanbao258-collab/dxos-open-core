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
  serviceCounts,
  stepCounts,
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
      ...readdirSync(join(__dirname, "components")).map(
        (f) => `components/${f}`,
      ),
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
    ).toEqual([
      "DX-1048",
      "DX-1047",
      "DX-1040",
      "DX-1043",
      "DX-1045",
      "DX-1046",
    ]);
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
    expect(auditLabel("SUBMITTED → TRIAGED v1")).toBe(
      "Đã gửi → Đã tiếp nhận v1",
    );
  });
});

describe("step and service counts", () => {
  it("are derived from the tickets, not hardcoded", () => {
    expect(stepCounts(PREVIEW_TICKETS)).toEqual({
      SUBMITTED: 3,
      TRIAGED: 1,
      IN_PROGRESS: 2,
      RESOLVED: 0,
      CONFIRMED: 2,
    });
    expect(serviceCounts(PREVIEW_TICKETS)).toEqual({
      "helpdesk.v1": 5,
      "facility.v1": 3,
    });
    expect(stepCounts([PREVIEW_TICKETS[0]]).SUBMITTED).toBe(1);
    expect(Object.values(stepCounts([])).every((n) => n === 0)).toBe(true);
  });
});

describe("create validation", () => {
  const ok = {
    service: "helpdesk.v1",
    title: "Hello",
    description: "x".repeat(15),
    location: "",
  };
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
    expect(
      within(nav).getByRole("link", { name: "Bàn xử lý" }),
    ).toHaveAttribute("aria-current", "page");
    expect(
      within(nav).getByRole("link", { name: "Tổng quan" }),
    ).not.toHaveAttribute("aria-current");
    const bottom = screen.getByRole("navigation", {
      name: "Điều hướng nhanh trên điện thoại",
    });
    expect(within(bottom).getAllByRole("link")).toHaveLength(4);
  });

  it("states MOCK/DEV, offers a skip link and one polite live region", () => {
    renderAt("/design-v621");
    expect(screen.getByText("MOCK / DEV")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Đến nội dung chính" }),
    ).toHaveAttribute("href", "#main");
    const live = document.querySelectorAll("[aria-live]");
    expect(live).toHaveLength(1);
    expect(live[0]).toHaveAttribute("aria-live", "polite");
    expect(document.title).toBe("Tổng quan · OpenTrix V7 (MOCK)");
  });

  it("brands as OpenTrix with the DX-OS Open-Core note", () => {
    renderAt("/design-v621");
    expect(screen.getByText("OpenTrix")).toBeInTheDocument();
    expect(
      screen.getByText("Service Portal · DX-OS Open-Core"),
    ).toBeInTheDocument();
  });

  it("unknown preview paths fall back to the overview", () => {
    renderAt("/design-v621/does-not-exist");
    expect(
      screen.getByRole("heading", { name: "Yêu cầu đang mở" }),
    ).toBeInTheDocument();
  });
});

describe("overview", () => {
  it("summarises open requests and keeps the MOCK boundary visible", () => {
    renderAt("/design-v621");
    expect(
      screen.getByText("6 yêu cầu đang mở · 3 chờ tiếp nhận · 2 đang xử lý"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Trạng thái môi trường")).toHaveTextContent(
      "LIVE chưa được xác nhận",
    );
  });

  it("lists attention items: high priority first, then oldest, max 5", () => {
    renderAt("/design-v621");
    const list = screen
      .getByRole("heading", { name: "Cần chú ý" })
      .closest("section")!;
    const rows = [...list.querySelectorAll(".att-row")];
    expect(rows).toHaveLength(5);
    expect(rows.map((r) => r.querySelector(".att-id")?.textContent)).toEqual([
      "DX-1048",
      "DX-1047",
      "DX-1040",
      "DX-1043",
      "DX-1045",
    ]);
    // Only the two "Cao" tickets carry a badge.
    expect(list.querySelectorAll(".ot-badge-high")).toHaveLength(2);
    expect(
      within(list).getByRole("link", {
        name: "Cấp quyền truy cập kho mã nguồn dự án",
      }),
    ).toHaveAttribute("href", "/design-v621/detail/DX-1048?from=overview");
    expect(
      within(list).getByRole("link", { name: "Xem toàn bộ hàng đợi" }),
    ).toHaveAttribute("href", "/design-v621/queue");
  });

  it("shows the per-step distribution derived from fixtures, not a rail", () => {
    renderAt("/design-v621");
    const aside = screen.getByLabelText("Phân bố theo bước");
    expect(
      within(aside).getByRole("heading", { name: "Số yêu cầu theo bước" }),
    ).toBeInTheDocument();
    const items = [...aside.querySelectorAll(".dist-list li")].map((li) => [
      li.querySelector(".dist-label")?.textContent,
      li.querySelector(".dist-count")?.textContent,
    ]);
    expect(items).toEqual([
      ["Đã gửi", "3"],
      ["Đã tiếp nhận", "1"],
      ["Đang xử lý", "2"],
      ["Đã giải quyết", "0"],
      ["Đã xác nhận", "2"],
    ]);
    expect(
      [...aside.querySelectorAll(".dist-bar")].every(
        (b) => b.getAttribute("aria-hidden") === "true",
      ),
    ).toBe(true);
    // The distribution chart must never reuse the per-ticket rail.
    expect(aside.querySelector(".rail")).toBeNull();
    expect(aside).toHaveTextContent("Hỗ trợ CNTT 5 · Cơ sở vật chất 3");
    expect(aside).toHaveTextContent("Workflow Core");
    const widths = [
      ...aside.querySelectorAll<HTMLElement>(".dist-bar > span"),
    ].map((e) => e.style.width);
    expect(widths[0]).toBe("100%");
    expect(widths[3]).toBe("0%");
  });

  it("marks the create CTA as a mobile duplicate of the bottom navigation", () => {
    renderAt("/design-v621");
    const cta = screen.getAllByRole("link", { name: /Tạo yêu cầu/ });
    expect(cta.some((a) => a.classList.contains("cta-dup"))).toBe(true);
    const bottom = screen.getByRole("navigation", {
      name: "Điều hướng nhanh trên điện thoại",
    });
    expect(
      within(bottom).getByRole("link", { name: "Tạo mới" }),
    ).toHaveAttribute("href", "/design-v621/create");
  });
});

describe("workflow rail", () => {
  it("renders 5 segments: done, current and outline, plus the step label", () => {
    renderAt("/design-v621/queue");
    const row = document
      .querySelector('a.row-link[data-ticket="DX-1046"]')!
      .closest("tr")!;
    const segs = [...row.querySelectorAll(".rail-seg")].map((e) => e.className);
    expect(segs).toEqual([
      "rail-seg is-done",
      "rail-seg is-done",
      "rail-seg is-current",
      "rail-seg is-todo",
      "rail-seg is-todo",
    ]);
    expect(row.querySelector(".rail-label")).toHaveTextContent("Đang xử lý");
    expect(row.querySelector(".rail-track")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
  });
});

describe("request list (mine / queue)", () => {
  it("renders segment counts and a labelled search box", () => {
    renderAt("/design-v621/queue");
    expect(
      screen.getByRole("button", { name: /Cần theo dõi/ }),
    ).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toBeInTheDocument();
    expect(screen.getByText("6 / 8 yêu cầu")).toBeInTheDocument();
    expect(screen.getByRole("table", { name: /Hàng đợi/ })).toBeInTheDocument();
    expect(
      screen.getByRole("columnheader", { name: "Phụ trách" }),
    ).toBeInTheDocument();
  });

  it("mine has no assignee column and shows 4 open rows", () => {
    renderAt("/design-v621/mine");
    expect(
      screen.queryByRole("columnheader", { name: "Phụ trách" }),
    ).toBeNull();
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
    await user.selectOptions(
      screen.getByLabelText("Lọc dịch vụ"),
      "facility.v1",
    );
    expect(document.getElementById("chips")).toHaveTextContent(
      "Cơ sở vật chất",
    );
    expect(screen.getByText("3 / 8 yêu cầu")).toBeInTheDocument();
  });

  it("filter sheet applies a draft and shows the active filter count", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    await user.click(screen.getByRole("button", { name: /^Lọc/ }));
    const dialog = screen.getByRole("dialog", { name: "Lọc yêu cầu" });
    await user.selectOptions(within(dialog).getByLabelText("Ưu tiên"), "Cao");
    await user.click(within(dialog).getByRole("button", { name: "Áp dụng" }));
    expect(
      screen.getByRole("button", { name: /Lọc \(1\)/ }),
    ).toBeInTheDocument();
    expect(screen.getByText("2 / 8 yêu cầu")).toBeInTheDocument();
  });

  it("every row links to its detail with the origin preserved", () => {
    renderAt("/design-v621/queue");
    const link = document.querySelector<HTMLAnchorElement>(
      'a.row-link[data-ticket="DX-1046"]',
    )!;
    expect(link.getAttribute("href")).toBe(
      "/design-v621/detail/DX-1046?from=queue",
    );
  });

  it("queue keyboard: J/K move focus, E reports no server-granted action, Esc returns to search", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    const rows = () => [
      ...document.querySelectorAll<HTMLAnchorElement>(
        "a.row-link[data-ticket]",
      ),
    ];
    // jsdom has no layout, so every link counts as shown.
    vi.spyOn(Element.prototype, "getClientRects").mockReturnValue([
      {},
    ] as unknown as DOMRectList);
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
    expect(document.getElementById("announcement")).toHaveTextContent(
      "DX-1048",
    );
    await user.keyboard("{Escape}");
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toHaveFocus();
    // Typing into the search box must not trigger shortcuts.
    await user.keyboard("jk");
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toHaveValue("jk");
  });

  it("moving focus clears stale shortcut feedback", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    vi.spyOn(Element.prototype, "getClientRects").mockReturnValue([
      {},
    ] as unknown as DOMRectList);
    await user.keyboard("je");
    expect(document.getElementById("kbd-feedback")).not.toHaveAttribute(
      "hidden",
    );
    await user.keyboard("j");
    expect(document.getElementById("kbd-feedback")).toHaveAttribute("hidden");
  });

  it("mine has no keyboard shortcuts hint", () => {
    renderAt("/design-v621/mine");
    expect(document.querySelector(".kbd-hint")).toBeNull();
  });
});

describe("queue table (V7)", () => {
  it("has a separate id column, badges only for high priority, dashes for no data", () => {
    renderAt("/design-v621/queue");
    expect(
      screen.getByRole("columnheader", { name: "Mã" }),
    ).toBeInTheDocument();
    const row = (id: string) =>
      document.querySelector(`a.row-link[data-ticket="${id}"]`)!.closest("tr")!;
    expect(row("DX-1048").querySelector(".ot-id")).toHaveTextContent("DX-1048");
    expect(row("DX-1048").querySelector(".ot-badge-high")).toHaveTextContent(
      "Cao",
    );
    expect(row("DX-1040").querySelector(".ot-badge-high")).toBeNull();
    expect(row("DX-1040").querySelector(".prio-normal")).toHaveTextContent(
      "Bình thường",
    );
    // Missing assignee is a dash visually, but still "Chưa có dữ liệu" for SR.
    const cell = row("DX-1048").querySelectorAll("td")[5];
    expect(cell.querySelector(".empty-dash")).toHaveAttribute(
      "aria-hidden",
      "true",
    );
    expect(cell).toHaveTextContent("Chưa có dữ liệu");
    expect(row("DX-1046").querySelectorAll("td")[5]).toHaveTextContent(
      "Nhóm IT Support",
    );
  });

  it("shows Xóa lọc only while a filter or query is active and resets everything", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/queue");
    expect(screen.queryByRole("button", { name: "Xóa lọc" })).toBeNull();
    await user.selectOptions(
      screen.getByLabelText("Lọc dịch vụ"),
      "facility.v1",
    );
    await user.type(screen.getByLabelText("Tìm kiếm yêu cầu"), "lotus");
    expect(screen.getByText("1 / 8 yêu cầu")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Xóa lọc" }));
    expect(screen.getByText("6 / 8 yêu cầu")).toBeInTheDocument();
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toHaveValue("");
    expect(screen.queryByRole("button", { name: "Xóa lọc" })).toBeNull();
  });

  it("card rows keep the keyboard contract (data-ticket links, priority badge)", () => {
    renderAt("/design-v621/queue");
    const card = document.querySelector(
      'a.mobile-card[data-ticket="DX-1047"]',
    )!;
    expect(card.querySelector(".ot-badge-high")).toHaveTextContent("Cao");
    expect(card.querySelector("h2")).toHaveTextContent(
      "Điều hòa phòng họp Lotus không làm mát",
    );
    expect(card.getAttribute("href")).toBe(
      "/design-v621/detail/DX-1047?from=queue",
    );
  });

  it("uses the V7 placeholder text", () => {
    renderAt("/design-v621/queue");
    expect(screen.getByLabelText("Tìm kiếm yêu cầu")).toHaveAttribute(
      "placeholder",
      "Tìm mã, tiêu đề…",
    );
  });
});

describe("QA scenarios (V6.2 N-10..N-13, V6.2.1 N-13)", () => {
  async function simulate(
    user: ReturnType<typeof userEvent.setup>,
    value: string,
  ) {
    await user.selectOptions(screen.getByLabelText("Tình huống"), value);
  }
  async function go(user: ReturnType<typeof userEvent.setup>, name: string) {
    await user.click(
      within(
        screen.getByRole("navigation", { name: "Điều hướng chính" }),
      ).getByRole("link", { name }),
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
    const box = screen.getByRole(role, { name: "", hidden: false });
    expect(box).toHaveTextContent(heading);
    expect(screen.queryByRole("table")).toBeNull();
  });

  it("unknown outcome offers history, never a retry, and locks the create form", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/integration");
    await simulate(user, "unknown");
    await go(user, "Bàn xử lý");
    expect(
      screen.getByRole("link", { name: "Xem lịch sử mẫu" }),
    ).toHaveAttribute("href", "/design-v621/detail/DX-1046?from=queue");
    expect(
      screen.queryByRole("button", { name: /thử lại|gửi lại|retry/i }),
    ).toBeNull();
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
      await user.click(
        screen.getByRole("button", { name: "Tải lại dữ liệu mẫu" }),
      );
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
    expect(
      screen.getByRole("heading", {
        name: "Cài đặt môi trường phát triển cho laptop mới",
      }),
    ).toBeInTheDocument();
    expect(screen.getByText("Đang xử lý · Bước 3/5")).toBeInTheDocument();
    expect(screen.getByText("Lịch sử xử lý (3)")).toBeInTheDocument();
    const items = [...document.querySelectorAll(".tl-item .tl-title")].map(
      (e) => e.textContent,
    );
    expect(items).toEqual([
      "Đã tiếp nhận → Đang xử lý v2",
      "Đã gửi → Đã tiếp nhận v1",
      "Khởi tạo v0",
    ]);
    expect(screen.getByText("v2", { exact: true })).toBeInTheDocument();
    expect(
      screen.getByText("Chưa có thao tác nào được máy chủ cấp."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button")).toBeNull();
    expect(screen.getByText(/BLOCKED #26/)).toBeInTheDocument();
  });

  it("states missing data instead of inventing it", () => {
    renderAt("/design-v621/detail/DX-1048?from=mine");
    expect(
      screen.getByText(
        "Chưa có dữ liệu dấu vết được cung cấp cho yêu cầu này.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("Chưa có dữ liệu").length,
    ).toBeGreaterThanOrEqual(2);
    expect(screen.getByText("Đã gửi · Bước 1/5")).toBeInTheDocument();
  });

  it("back link follows the origin and unknown ids show a not-found heading", () => {
    renderAt("/design-v621/detail/DX-9999?from=overview");
    expect(
      screen.getByRole("heading", { name: "Không tìm thấy yêu cầu" }),
    ).toBeInTheDocument();
    expect(document.querySelector(".detail-back")).toHaveAttribute(
      "href",
      "/design-v621",
    );
  });

  it("sanitises an arbitrary origin param to mine", () => {
    renderAt("/design-v621/detail/DX-1046?from=javascript:alert(1)");
    expect(document.querySelector(".detail-back")).toHaveAttribute(
      "href",
      "/design-v621/mine",
    );
    expect(document.querySelector(".detail-back")).toHaveTextContent(
      "Yêu cầu của tôi",
    );
  });
});

describe("create request form", () => {
  it("validates, focuses the first invalid field and never claims submission", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/create");
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveFocus();
    expect(screen.getByLabelText(/Tiêu đề/)).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(
      screen.getByText("Tiêu đề cần ít nhất 5 ký tự."),
    ).toBeInTheDocument();
    expect(screen.getByText("Mô tả cần ít nhất 15 ký tự.")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Vui lòng kiểm tra các trường đã đánh dấu.",
    );
    await user.type(screen.getByLabelText(/Tiêu đề/), "Không vào được VPN");
    await user.type(
      screen.getByLabelText(/Mô tả chi tiết/),
      "Không đăng nhập được VPN từ sáng nay",
    );
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    expect(screen.getByText("Biểu mẫu hợp lệ")).toBeInTheDocument();
    expect(
      screen.getByText(/Chưa gửi yêu cầu lên máy chủ, chưa tạo ticket/),
    ).toBeInTheDocument();
    expect(document.getElementById("announcement")).toHaveTextContent(
      "Biểu mẫu hợp lệ. Chưa gửi yêu cầu.",
    );
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
    expect(facility).toHaveAttribute("placeholder", "Ví dụ: Phòng họp Lotus…");
    await user.type(screen.getByLabelText(/Tiêu đề/), "Điều hòa hỏng");
    await user.type(
      screen.getByLabelText(/Mô tả chi tiết/),
      "Điều hòa không làm mát từ sáng",
    );
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    expect(facility).toHaveFocus();
    expect(
      screen.getByText("Cần nhập vị trí khi yêu cầu Cơ sở vật chất."),
    ).toBeInTheDocument();
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

describe("integration screen (V7)", () => {
  it("summarises from the data, lists every capability as BLOCKED, no font copy", () => {
    renderAt("/design-v621/integration");
    expect(screen.getByText("7 thành phần")).toBeInTheDocument();
    expect(document.querySelector(".int-summary")).toHaveTextContent(
      "7 thành phần · 7 chưa nghiệm thu",
    );
    expect(screen.getAllByText("BLOCKED")).toHaveLength(7);
    expect(screen.getByText("PR #63")).toBeInTheDocument();
    const names = [
      ...document.querySelectorAll(".int-table tbody th[scope='row']"),
    ].map((e) => e.textContent);
    expect(names).toHaveLength(7);
    expect(names[0]).toBe("Identity / SSO");
    expect(document.body).not.toHaveTextContent(
      /Be Vietnam Pro|IBM Plex|Google Fonts/,
    );
  });

  it("states the write-safety principles as a list", () => {
    renderAt("/design-v621/integration");
    const section = screen
      .getByRole("heading", { name: "Nguyên tắc tích hợp" })
      .closest("section")!;
    const items = section.querySelectorAll("li");
    expect(items).toHaveLength(4);
    expect(section).toHaveTextContent("allowedActions");
    expect(section).toHaveTextContent("expectedVersion");
    expect(section).toHaveTextContent("không gửi lại lệnh");
  });

  it("separates the preview-only QA tool from the status content", () => {
    renderAt("/design-v621/integration");
    const tool = screen
      .getByRole("heading", { name: /Công cụ xem trạng thái giao diện/ })
      .closest("section")!;
    expect(tool).toHaveTextContent("Chỉ dùng khi xem trước");
    expect(within(tool).getByLabelText("Tình huống")).toBeInTheDocument();
    expect(
      screen
        .getByRole("heading", { name: "Trạng thái tích hợp" })
        .closest("section"),
    ).not.toBe(tool);
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
      expect(textFitsInput(input, "Tìm mã hoặc tiêu đề"), `${w}px`).toBe(
        expected,
      );
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

describe("mine (V7)", () => {
  const row = (id: string) =>
    document.querySelector(`a.row-link[data-ticket="${id}"]`)!.closest("tr")!;

  it("drops queue-only columns and explains each status in plain words", () => {
    renderAt("/design-v621/mine");
    for (const name of ["Mã", "Yêu cầu", "Dịch vụ", "Bước", "Cập nhật"])
      expect(screen.getByRole("columnheader", { name })).toBeInTheDocument();
    expect(
      screen.queryByRole("columnheader", { name: "Phụ trách" }),
    ).toBeNull();
    expect(screen.queryByRole("columnheader", { name: "Ưu tiên" })).toBeNull();
    expect(row("DX-1046").querySelector(".subline")).toHaveTextContent(
      "Đang được xử lý; bước tiếp theo là ghi nhận kết quả.",
    );
    expect(row("DX-1048").querySelector(".subline")).toHaveTextContent(
      "Đang chờ tiếp nhận yêu cầu.",
    );
    // The requester is the viewer; it is not repeated.
    expect(row("DX-1048")).not.toHaveTextContent("Ngọc Ánh");
  });

  it("shows the Cao badge next to the title and nowhere else", () => {
    renderAt("/design-v621/mine");
    expect(
      row("DX-1048").querySelector(".title-row .ot-badge-high"),
    ).toHaveTextContent("Cao");
    expect(row("DX-1046").querySelector(".ot-badge-high")).toBeNull();
    expect(row("DX-1046")).not.toHaveTextContent("Bình thường");
  });

  it("closed tab lists only confirmed tickets with their status sentence", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/mine");
    await user.click(screen.getByRole("button", { name: /Đã xong/ }));
    expect(
      [...document.querySelectorAll("a.row-link")].map((a) =>
        a.getAttribute("data-ticket"),
      ),
    ).toEqual(["DX-1042"]);
    expect(row("DX-1042").querySelector(".subline")).toHaveTextContent(
      "Yêu cầu đã được xác nhận hoàn thành.",
    );
  });

  it("cards: status sentence and date share one meta line, date last", () => {
    renderAt("/design-v621/mine");
    for (const card of document.querySelectorAll("a.mobile-card")) {
      const meta = card.querySelector(".mobile-meta")!;
      expect(meta.children).toHaveLength(2);
      expect(meta.lastElementChild).toHaveClass("mobile-card-date");
      expect(meta.lastElementChild?.textContent).toMatch(/^\d\d\/\d\d\/\d{4}$/);
      expect(
        card.querySelector(".mobile-card-status .mobile-card-date"),
      ).toBeNull();
    }
    const card = document.querySelector(
      'a.mobile-card[data-ticket="DX-1046"]',
    )!;
    expect(card.querySelector(".mobile-meta-text")).toHaveTextContent(
      "Đang được xử lý; bước tiếp theo là ghi nhận kết quả.",
    );
  });

  it("queue cards still show requester and assignee instead", () => {
    renderAt("/design-v621/queue");
    const card = document.querySelector(
      'a.mobile-card[data-ticket="DX-1046"]',
    )!;
    expect(card.querySelector(".mobile-meta-text")).toHaveTextContent(
      "Ngọc Ánh · Nhóm IT Support",
    );
  });

  it("mine never exposes a write action or shortcut", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/mine");
    vi.spyOn(Element.prototype, "getClientRects").mockReturnValue([
      {},
    ] as unknown as DOMRectList);
    await user.keyboard("je");
    expect(document.activeElement).toBe(document.body);
    expect(document.getElementById("kbd-feedback")).toBeEmptyDOMElement();
  });
});

describe("request detail (V7)", () => {
  it("renders the hero rail as 5 ordered steps with states", () => {
    renderAt("/design-v621/detail/DX-1046?from=queue");
    const steps = [...document.querySelectorAll(".hero-rail li")];
    expect(steps.map((li) => li.className)).toEqual([
      "hero-step is-done",
      "hero-step is-done",
      "hero-step is-current",
      "hero-step is-todo",
      "hero-step is-todo",
    ]);
    expect(steps.map((li) => li.textContent)).toEqual([
      "Đã gửi",
      "Đã tiếp nhận",
      "Đang xử lý",
      "Đã giải quyết",
      "Đã xác nhận",
    ]);
    expect(
      screen.getByText(/Quy trình 5 bước, hiện ở bước 3 trên 5: Đang xử lý\./),
    ).toBeInTheDocument();
  });

  it("timeline: newest first, title then actor and timestamp", () => {
    renderAt("/design-v621/detail/DX-1046?from=queue");
    const items = [...document.querySelectorAll(".tl-item")];
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveClass("is-latest");
    expect(items[1]).not.toHaveClass("is-latest");
    expect(items[0].querySelector(".tl-meta")).toHaveTextContent(
      "Nhóm IT Support · 11:17 19/10/2026",
    );
    expect(items[0].querySelector("time")).toHaveTextContent(
      "11:17 19/10/2026",
    );
    expect(items[2].querySelector(".tl-title")).toHaveTextContent(
      "Khởi tạo v0",
    );
  });

  it("info list pairs each label with its value; missing values are muted text", () => {
    renderAt("/design-v621/detail/DX-1048?from=mine");
    const rows = [...document.querySelectorAll(".info-list > div")].map((d) => [
      d.querySelector("dt")?.textContent,
      d.querySelector("dd")?.textContent,
    ]);
    expect(rows).toEqual([
      ["Người gửi", "Ngọc Ánh"],
      ["Nhóm phụ trách", "Chưa có dữ liệu"],
      ["Cập nhật", "09:15 20/10/2026"],
      ["Phiên bản", "Chưa có dữ liệu"],
    ]);
    expect(document.querySelectorAll(".info-list .no-data")).toHaveLength(2);
  });

  it("explains missing actions in a disclosure, without offering any action", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/detail/DX-1046?from=queue");
    const block = screen
      .getByRole("heading", { name: "Thao tác xử lý" })
      .closest("section")!;
    expect(block).toHaveTextContent("Chưa có thao tác nào được máy chủ cấp.");
    const details = block.querySelector("details")!;
    expect(details).not.toHaveAttribute("open");
    await user.click(screen.getByText("Vì sao không có thao tác?"));
    expect(details).toHaveAttribute("open");
    expect(details).toHaveTextContent("allowedActions");
    expect(details).toHaveTextContent("expectedVersion");
    expect(block.querySelectorAll("button, a, input")).toHaveLength(0);
  });

  it("keeps attachments as a quiet BLOCKED row", () => {
    renderAt("/design-v621/detail/DX-1046?from=queue");
    const row = document.querySelector(".attach-row")!;
    expect(row).toHaveTextContent("Tệp đính kèm");
    expect(row.querySelector(".blocked-ref")).toHaveTextContent("BLOCKED #26");
    expect(row.querySelectorAll("input[type=file]")).toHaveLength(0);
  });
});

describe("create request (V7)", () => {
  it("turns browser autofill off and ends placeholders with an ellipsis", () => {
    renderAt("/design-v621/create");
    for (const id of ["title", "description", "location"]) {
      const el = document.getElementById(id)!;
      expect(el, id).toHaveAttribute("autocomplete", "off");
      expect(el.getAttribute("placeholder"), id).toMatch(/…$/);
    }
  });

  it("copy no longer points at 'the left button'", () => {
    renderAt("/design-v621/create");
    const note = document.querySelector(".ot-form-note")!;
    expect(note).toHaveTextContent("nút Kiểm tra biểu mẫu");
    expect(note).not.toHaveTextContent("bên trái");
  });

  it("unknown outcome: disabled submit with a visible reason, never a retry", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/integration");
    await user.selectOptions(screen.getByLabelText("Tình huống"), "unknown");
    await user.click(
      within(
        screen.getByRole("navigation", { name: "Điều hướng chính" }),
      ).getByRole("link", { name: "Tạo yêu cầu" }),
    );
    const submit = screen.getByRole("button", { name: "Kiểm tra biểu mẫu" });
    expect(submit).toBeDisabled();
    expect(submit).toHaveAttribute("aria-describedby", "lock-reason");
    expect(document.getElementById("lock-reason")).toHaveTextContent(
      "Đang khóa: kết quả ghi chưa xác định. Cần đối soát trước khi tiếp tục.",
    );
    expect(
      screen.queryByRole("button", { name: /thử lại|gửi lại/i }),
    ).toBeNull();
  });

  it("errors carry an icon and the invalid field is marked", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/create");
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    const err = document.getElementById("title-error")!;
    expect(err).toHaveTextContent("Tiêu đề cần ít nhất 5 ký tự.");
    expect(err.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(document.getElementById("title")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    // Facility rule is untouched: IT does not require a location.
    expect(document.getElementById("location-error")).toBeEmptyDOMElement();
  });

  it("valid IT form shows the success block without sending anything", async () => {
    const user = userEvent.setup();
    renderAt("/design-v621/create");
    await user.type(screen.getByLabelText(/Tiêu đề/), "Không vào được VPN");
    await user.type(
      screen.getByLabelText(/Mô tả chi tiết/),
      "Không đăng nhập được VPN từ sáng nay",
    );
    await user.click(screen.getByRole("button", { name: "Kiểm tra biểu mẫu" }));
    const ok = screen.getByRole("status", { name: "" });
    expect(ok).toHaveTextContent("Biểu mẫu hợp lệ");
    expect(ok.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("service choices are labelled radios inside one group", () => {
    renderAt("/design-v621/create");
    const group = screen.getByRole("group", { name: /Dịch vụ/ });
    expect(within(group).getAllByRole("radio")).toHaveLength(2);
    expect(
      within(group).getByRole("radio", { name: /Hỗ trợ CNTT/ }),
    ).toBeChecked();
  });
});
