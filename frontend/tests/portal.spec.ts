import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const evidence = resolve("..", "docs", "evidence", "issue39-portal");
async function screenshot(page: Page, name: string) {
  await mkdir(evidence, { recursive: true });
  await page.evaluate(() => document.fonts.ready);
  const closeToast = page.getByRole("button", { name: "Đóng thông báo" });
  if (await closeToast.isVisible()) await closeToast.click();
  await page.evaluate(() =>
    window.scrollTo({ top: 0, left: 0, behavior: "instant" }),
  );
  await page.screenshot({
    path: resolve(evidence, `${name}.png`),
    fullPage: true,
    animations: "disabled",
  });
  const results = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(
    results.violations.map((v) => ({
      id: v.id,
      nodes: v.nodes.map((n) => ({
        target: n.target,
        summary: n.failureSummary,
      })),
    })),
    `Accessibility: ${name}`,
  ).toEqual([]);
}
async function login(page: Page, role = "employee") {
  await page.goto("/session");
  if (role === "processor")
    await page.getByRole("radio", { name: /Người xử lý/ }).check();
  await page.getByRole("button", { name: "Vào phiên demo" }).click();
  await expect(
    page.getByRole("heading", { name: /Chào buổi sáng/ }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Yêu cầu gần đây" }),
  ).toBeVisible();
  const toastClose = page.getByRole("button", { name: "Đóng thông báo" });
  if (await toastClose.isVisible()) await toastClose.click();
}
async function openNav(page: Page, name: string) {
  const menu = page.getByRole("button", { name: "Mở menu" });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole("link", { name, exact: true }).click();
}
async function fillRequest(page: Page, kind: string) {
  await page
    .getByRole("link", { name: "Tạo yêu cầu mới", exact: true })
    .first()
    .click();
  await page
    .getByRole("radio", {
      name: kind === "it" ? /Hỗ trợ CNTT/ : /Cơ sở vật chất/,
    })
    .check();
  await page
    .getByRole("textbox", { name: /Tiêu đề yêu cầu/ })
    .fill(
      kind === "it"
        ? "Cài đặt công cụ phát triển OLP"
        : "Chuẩn bị phòng họp cho demo OLP",
    );
  await page
    .getByRole("textbox", { name: /Mô tả chi tiết/ })
    .fill(
      "Đề nghị nhóm phụ trách kiểm tra và chuẩn bị cho buổi trình diễn OLP 2026.",
    );
  await page
    .getByRole("textbox", { name: /Vị trí/ })
    .fill("Tầng 3 · Phòng demo");
}

test("all eight screens render; capture actual localhost evidence; no API or external requests", async ({
  page,
}) => {
  test.setTimeout(60000);
  const unexpected: string[] = [];
  const errors: string[] = [];
  page.on("request", (request) => {
    if (
      !request.url().startsWith("http://127.0.0.1:4173") ||
      /\/api\//.test(request.url())
    )
      unexpected.push(request.url());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/session");
  await screenshot(page, "01-session");
  await login(page);
  await screenshot(page, "02-dashboard-desktop");
  await openNav(page, "Yêu cầu của tôi");
  await expect(
    page.getByRole("link", { name: /DX-1048.*Cấp quyền/ }),
  ).toBeVisible();
  await screenshot(page, "03-requests");
  await page
    .getByRole("link", { name: "Tạo yêu cầu mới", exact: true })
    .click();
  await screenshot(page, "04-new-request");
  await openNav(page, "Trạng thái phiên");
  await page.getByRole("radio", { name: /Người xử lý/ }).check();
  await page
    .getByRole("button", { name: "Tiếp tục với vai trò đã chọn" })
    .click();
  await openNav(page, "Bàn xử lý");
  await expect(
    page.getByRole("link", { name: /DX-1048.*Cấp quyền/ }),
  ).toBeVisible();
  await screenshot(page, "05-approvals");
  await page.getByRole("link", { name: /DX-1046.*Cài đặt/ }).click();
  await expect(
    page.getByRole("heading", { name: "Lịch sử xử lý" }),
  ).toBeVisible();
  await screenshot(page, "06-detail-timeline");
  await openNav(page, "Lịch sử hoạt động");
  await expect(page.locator(".history-list article")).toHaveCount(6);
  await screenshot(page, "07-history");
  await openNav(page, "Trạng thái tích hợp");
  await screenshot(page, "08-integration");
  await page.getByRole("button", { name: "Kiểm tra chặn LIVE" }).click();
  await expect(page.locator(".inline-notice[role=status]")).toContainText(
    "BLOCKED API",
  );
  expect(unexpected).toEqual([]);
  expect(errors).toEqual([]);
});

for (const kind of ["it", "facility"]) {
  test(`${kind}: create, approve, process, complete using shared UI`, async ({
    page,
  }) => {
    await login(page);
    await fillRequest(page, kind);
    await page.getByRole("button", { name: "Gửi yêu cầu" }).click();
    await expect(
      page.getByRole("heading", { name: "Lịch sử xử lý" }),
    ).toBeVisible();
    await expect(
      page.locator("main").getByText("Chờ phê duyệt", { exact: true }),
    ).toHaveCount(2);
    await openNav(page, "Trạng thái phiên");
    await page.getByRole("radio", { name: /Người xử lý/ }).check();
    await page
      .getByRole("button", { name: "Tiếp tục với vai trò đã chọn" })
      .click();
    await openNav(page, "Yêu cầu của tôi");
    await page
      .getByRole("link", {
        name:
          kind === "it"
            ? /DX-1049.*Cài đặt công cụ/
            : /DX-1049.*Chuẩn bị phòng/,
      })
      .click();
    for (const label of ["Phê duyệt", "Bắt đầu xử lý", "Hoàn thành"]) {
      await page.getByRole("button", { name: label, exact: true }).click();
      await page
        .getByRole("textbox", { name: "Ghi chú xử lý" })
        .fill(`${label} yêu cầu trong buổi demo.`);
      await page
        .getByRole("button", {
          name: `Xác nhận ${label.toLowerCase()}`,
          exact: true,
        })
        .click();
      await expect(page.getByRole("dialog")).toHaveCount(0);
      await expect(
        page.getByRole("heading", { name: "Lịch sử xử lý" }),
      ).toBeVisible();
    }
    await expect(page.locator(".timeline li")).toHaveCount(4);
    await expect(
      page.getByText("Yêu cầu đã kết thúc. Không còn thao tác xử lý."),
    ).toBeVisible();
  });
}

test("search, filters, empty state, pagination and no attachment input", async ({
  page,
}) => {
  await login(page);
  await openNav(page, "Yêu cầu của tôi");
  await expect(page.getByText("Hiển thị 1–6 trên 7 yêu cầu")).toBeVisible();
  await page.getByRole("button", { name: "Sau", exact: true }).click();
  await expect(page.getByText("Hiển thị 7–7 trên 7 yêu cầu")).toBeVisible();
  await page
    .getByRole("textbox", { name: "Tìm yêu cầu", exact: true })
    .fill("cap quyen");
  await expect(page.locator("tbody tr")).toHaveCount(1);
  await page
    .getByRole("combobox", { name: "Loại dịch vụ" })
    .selectOption("facility");
  await expect(page.getByText("Chưa có yêu cầu phù hợp")).toBeVisible();
  await page.getByRole("button", { name: "Xóa bộ lọc" }).click();
  await expect(page.locator("tbody tr")).toHaveCount(6);
  await page
    .getByRole("link", { name: "Tạo yêu cầu mới", exact: true })
    .click();
  await page.getByRole("button", { name: "Gửi yêu cầu" }).click();
  await expect(page.getByText("Tiêu đề cần từ 8 đến 120 ký tự.")).toBeVisible();
  await expect(page.locator("input[type=file]")).toHaveCount(0);
  await expect(page.getByText("BLOCKED FEATURE")).toBeVisible();
});

for (const fault of ["network", "403", "404", "401"]) {
  test(`simulated ${fault} is safe and requires explicit recovery`, async ({
    page,
  }) => {
    await login(page);
    await openNav(page, "Trạng thái tích hợp");
    await page
      .getByRole("combobox", { name: "Kịch bản lỗi" })
      .selectOption(fault);
    await page.getByRole("button", { name: "Áp dụng lỗi mô phỏng" }).click();
    await page.getByRole("link", { name: "Mở danh sách yêu cầu" }).click();
    if (fault === "401") {
      await expect(page.getByRole("alert")).toContainText("401 mô phỏng");
      await expect(
        page.getByRole("button", { name: "Vào phiên demo" }),
      ).toBeVisible();
    } else {
      await expect(page.getByRole("alert")).toBeVisible();
      await expect(page.locator("tbody tr")).toHaveCount(0);
      await page.getByRole("button", { name: "Tải lại dữ liệu" }).click();
      await expect(page.locator("tbody tr")).toHaveCount(6);
    }
  });
}

test("uncertain write is not retried; submit remains disabled", async ({
  page,
}) => {
  await login(page);
  await openNav(page, "Trạng thái tích hợp");
  await page
    .getByRole("combobox", { name: "Kịch bản lỗi" })
    .selectOption("unknown");
  await page.getByRole("button", { name: "Áp dụng lỗi mô phỏng" }).click();
  // Creating the form does not consume the one-shot write fault.
  await openNav(page, "Tổng quan");
  await fillRequest(page, "it");
  await page.getByRole("button", { name: "Gửi yêu cầu" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Chưa xác định kết quả thao tác",
  );
  await expect(
    page.getByRole("button", { name: "Gửi yêu cầu" }),
  ).toBeDisabled();
});

test("hidden and nonexistent fixture resources have the same message", async ({
  page,
}) => {
  await login(page);
  await page.goto("/requests/DX-1047");
  await expect(page.getByRole("alert")).toContainText(
    "Không tìm thấy yêu cầu hoặc yêu cầu không khả dụng.",
  );
  const hidden = await page.getByRole("alert").textContent();
  await page.goto("/requests/does-not-exist");
  await expect(page.getByRole("alert")).toHaveText(hidden!);
});

test("desktop/tablet/mobile remain usable; keyboard and WCAG checks", async ({
  page,
}) => {
  await login(page);
  const violations: { width: number; items: unknown[] }[] = [];
  for (const [width, height, name] of [
    [1440, 1100, "desktop"],
    [834, 1112, "tablet"],
    [390, 844, "mobile"],
  ] as const) {
    await page.setViewportSize({ width, height });
    await expect(
      page.getByRole("heading", { name: /Chào buổi sáng/ }),
    ).toBeVisible();
    const overflow = await page.evaluate(() => ({
      width: innerWidth,
      scroll: document.documentElement.scrollWidth,
      elements: [...document.querySelectorAll("*")]
        .filter((e) => e.getBoundingClientRect().right > innerWidth + 1)
        .map((e) => ({
          tag: e.tagName,
          class: e.className,
          right: e.getBoundingClientRect().right,
        }))
        .slice(0, 15),
    }));
    expect(overflow.scroll, JSON.stringify(overflow)).toBe(overflow.width);
    await screenshot(page, `09-dashboard-${name}`);
    const results = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    if (results.violations.length)
      violations.push({
        width,
        items: results.violations.map((v) => ({
          id: v.id,
          nodes: v.nodes.map((n) => ({
            target: n.target,
            summary: n.failureSummary,
          })),
        })),
      });
    await openNav(page, "Yêu cầu của tôi");
    await expect(
      page.getByRole("heading", { name: "Danh sách yêu cầu" }),
    ).toBeVisible();
    await openNav(page, "Tổng quan");
    await expect(
      page.getByRole("heading", { name: "Yêu cầu gần đây" }),
    ).toBeVisible();
  }
  expect(violations).toEqual([]);
});

test("keyboard navigation, modal focus, rejection and reduced motion", async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await login(page, "processor");
  await openNav(page, "Bàn xử lý");
  await page.getByRole("link", { name: /DX-1048.*Cấp quyền/ }).click();
  await page.getByRole("button", { name: "Từ chối", exact: true }).click();
  await expect(
    page.getByRole("textbox", { name: "Ghi chú xử lý" }),
  ).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Từ chối", exact: true }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await page
    .getByRole("textbox", { name: "Ghi chú xử lý" })
    .fill("Cần bổ sung phạm vi và ngân sách trước khi xử lý.");
  await page
    .getByRole("button", { name: "Xác nhận từ chối", exact: true })
    .click();
  await expect(
    page.getByText("Yêu cầu đã kết thúc. Không còn thao tác xử lý."),
  ).toBeVisible();
  await expect(page.locator(".timeline li")).toHaveCount(2);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole("button", { name: "Mở menu" }).click();
  await expect(page.locator(".sidebar .brand")).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("button", { name: "Mở menu" })).toBeFocused();
});
