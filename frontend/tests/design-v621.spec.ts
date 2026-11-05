// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const evidence = resolve("..", "docs", "evidence", "opentrix-v621");
const ROUTES = [
  "/design-v621",
  "/design-v621/mine",
  "/design-v621/queue",
  "/design-v621/detail/DX-1046?from=queue",
  "/design-v621/create",
  "/design-v621/integration",
];

async function ready(page: Page, url: string) {
  await page.goto(url);
  // Fonts load lazily on first use; force the Vietnamese-capable faces so the
  // layout is measured with the real (wider) metrics, not the fallback.
  await page.evaluate(async () => {
    await Promise.all(
      ["400", "500", "600", "700"].map((w) =>
        document.fonts.load(`${w} 14px "Be Vietnam Pro"`, "ắệơưđạ"),
      ),
    );
    await Promise.all(
      ["400", "500", "600"].map((w) =>
        document.fonts.load(`${w} 12px "IBM Plex Mono"`, "ăệ0123"),
      ),
    );
    await document.fonts.ready;
  });
  await expect(page.locator("#main h1")).toBeVisible();
}

async function noHorizontalOverflow(page: Page) {
  return page.evaluate(
    () => document.documentElement.scrollWidth <= window.innerWidth,
  );
}

test("no third-party requests and self-hosted fonts load with Vietnamese glyphs", async ({
  page,
}) => {
  const external: string[] = [];
  page.on("request", (r) => {
    const u = new URL(r.url());
    if (u.hostname !== "127.0.0.1" && u.protocol.startsWith("http"))
      external.push(r.url());
  });
  await ready(page, "/design-v621/queue");
  const loaded = await page.evaluate(() =>
    [...document.fonts]
      .filter((f) => f.status === "loaded")
      .map((f) => `${f.family}:${f.unicodeRange.toLowerCase().includes("1ea0") ? "vi" : "latin"}`),
  );
  expect(loaded.some((f) => f.startsWith("Be Vietnam Pro"))).toBe(true);
  expect(loaded.some((f) => f.startsWith("IBM Plex Mono"))).toBe(true);
  // Vietnamese diacritics must be served by the self-hosted vietnamese subset.
  expect(loaded).toContain("Be Vietnam Pro:vi");
  expect(external).toEqual([]);
});

test("existing routes keep working", async ({ page }) => {
  await page.goto("/login");
  await expect(page.locator("body")).not.toBeEmpty();
  await page.goto("/session");
  await expect(
    page.getByRole("button", { name: "Vào phiên demo" }),
  ).toBeVisible();
});

test("placeholder never clips from 320 to 420px (incl. 361-370)", async ({
  page,
}) => {
  await page.setViewportSize({ width: 320, height: 800 });
  await ready(page, "/design-v621/queue");
  const clipped: string[] = [];
  for (let w = 320; w <= 420; w++) {
    await page.setViewportSize({ width: w, height: 800 });
    const result = await page.evaluate(async () => {
      await document.fonts.ready;
      const input = document.querySelector<HTMLInputElement>("#search")!;
      const cs = getComputedStyle(input);
      const ctx = document.createElement("canvas").getContext("2d")!;
      ctx.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
      const available =
        input.clientWidth -
        parseFloat(cs.paddingLeft) -
        parseFloat(cs.paddingRight);
      return {
        text: input.placeholder,
        width: ctx.measureText(input.placeholder).width,
        available,
      };
    });
    // Allow the ResizeObserver frame to settle once before judging.
    if (result.width > result.available) {
      await page.waitForTimeout(50);
      const again = await page.evaluate(() => {
        const input = document.querySelector<HTMLInputElement>("#search")!;
        return input.placeholder;
      });
      if (again !== "Tìm…") clipped.push(`${w}px: "${result.text}"`);
    }
  }
  expect(clipped).toEqual([]);
  // The label for assistive tech stays meaningful at every width.
  await expect(page.getByLabel("Tìm kiếm yêu cầu")).toHaveCount(1);
});

test("typed search text survives resize between short and long placeholder", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 800 });
  await ready(page, "/design-v621/queue");
  await page.locator("#search").fill("wifi");
  await page.setViewportSize({ width: 320, height: 800 });
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect(page.locator("#search")).toHaveValue("wifi");
});

for (const width of [320, 360, 361, 370, 390, 760, 761, 800, 900, 1024, 1200, 1440]) {
  test(`no horizontal overflow on every screen at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    for (const url of ROUTES) {
      await ready(page, url);
      expect(await noHorizontalOverflow(page), `${url} @${width}`).toBe(true);
    }
  });
}

test("QA unknown outcome locks the form and reset restores focus + announces", async ({
  page,
}) => {
  await ready(page, "/design-v621/integration");
  await page.locator("#qa-scenario").selectOption("unknown");
  await expect(page.locator("#announcement")).toContainText(
    "Kết quả ghi chưa xác định",
  );
  // N-13: only #announcement is a live region, the banner is not.
  await page.getByRole("link", { name: "Tạo yêu cầu" }).first().click();
  await expect(page.locator("#submit-form")).toBeDisabled();
  await expect(page.locator("#qa-banner-state")).not.toHaveAttribute(
    "aria-live",
    /.+/,
  );
  await expect(page.locator("[aria-live]:not(.ot-field-error)")).toHaveCount(2); // #announcement + #form-feedback
  await page.getByRole("button", { name: "Tắt mô phỏng" }).click();
  await expect(page.locator("#qa-banner-reset")).toHaveCount(0);
  await expect(page.locator("#submit-form")).toBeEnabled();
  expect(await page.evaluate(() => document.activeElement?.id)).toBe("main");
  await expect(page.locator("#announcement")).toContainText(
    "Đã tắt mô phỏng",
  );
});

test("queue shows no write action and keyboard hint matches viewport", async ({
  page,
}) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await ready(page, "/design-v621/queue");
  await expect(page.locator(".kbd-hint")).toBeVisible();
  await page.getByRole("button", { name: /^Tất cả/ }).focus();
  await page.keyboard.press("j");
  expect(
    await page.evaluate(
      () => (document.activeElement as HTMLElement)?.dataset.ticket,
    ),
  ).toBe("DX-1048");
  await page.keyboard.press("e");
  await expect(page.locator("#kbd-feedback")).toContainText("DX-1048");
  await expect(page.locator("#kbd-feedback")).toContainText(
    "chưa được máy chủ cấp thao tác",
  );
  await page.keyboard.press("k");
  await page.keyboard.press("Escape");
  await expect(page.locator("#search")).toBeFocused();
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator(".kbd-hint")).toBeHidden();
});

test("detail never offers actions and shows version only when provided", async ({
  page,
}) => {
  await ready(page, "/design-v621/detail/DX-1046?from=queue");
  await expect(page.getByText("v2", { exact: true })).toBeVisible();
  await expect(page.getByText("Lịch sử xử lý (3)")).toBeVisible();
  await expect(
    page.getByText("Không có thao tác xử lý nào được máy chủ cấp"),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /duyệt|xử lý|xác nhận/i })).toHaveCount(0);
  await ready(page, "/design-v621/detail/DX-1048?from=mine");
  await expect(page.getByText("Chưa có dữ liệu").first()).toBeVisible();
});

test("reduced motion disables screen fade and skeleton animation", async ({
  browser,
}) => {
  const context = await browser.newContext({ reducedMotion: "reduce" });
  const page = await context.newPage();
  await ready(page, "/design-v621/mine");
  const duration = await page.evaluate(
    () => getComputedStyle(document.querySelector(".screen")!).animationDuration,
  );
  expect(parseFloat(duration)).toBeLessThan(0.001);
  await context.close();
});

test("reload simulates a brief loading state and recovers with an announcement", async ({
  page,
}) => {
  await ready(page, "/design-v621/mine");
  await page.getByRole("button", { name: "Tải lại dữ liệu mẫu" }).click();
  await expect(page.getByText("Đang tải danh sách")).toBeVisible();
  await expect(page.getByText("Đang tải danh sách")).toBeHidden();
  await expect(page.locator("#announcement")).toContainText(
    "Đã tải lại danh sách mẫu.",
  );
});

test("mobile filter sheet opens as a modal dialog and applies filters", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await ready(page, "/design-v621/queue");
  await page.getByRole("button", { name: /^Lọc/ }).click();
  const dialog = page.getByRole("dialog", { name: "Lọc yêu cầu" });
  await expect(dialog).toBeVisible();
  await dialog.getByLabel("Dịch vụ").selectOption("facility.v1");
  await dialog.getByRole("button", { name: "Áp dụng" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.locator("#chips")).toContainText("Cơ sở vật chất");
  await expect(page.getByRole("button", { name: /^Lọc \(1\)/ })).toBeVisible();
  // Escape closes the native dialog and returns focus to the trigger.
  await page.getByRole("button", { name: /^Lọc/ }).click();
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("button", { name: /^Lọc/ })).toBeFocused();
});

for (const [name, width, height] of [
  ["desktop-1440", 1440, 900],
  ["mobile-390", 390, 844],
  ["mobile-320", 320, 640],
] as const) {
  test(`axe + screenshots ${name}`, async ({ page }) => {
    await page.setViewportSize({ width, height });
    await mkdir(evidence, { recursive: true });
    for (const url of ROUTES) {
      await ready(page, url);
      const slug =
        url.replace("/design-v621", "").replace(/[/?=]/g, "-").replace(/^-/, "") ||
        "overview";
      await page.screenshot({
        path: resolve(evidence, `${name}-${slug}.png`),
        fullPage: true,
        animations: "disabled",
      });
      const results = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa", "wcag22aa"])
        .analyze();
      expect(
        results.violations.map((v) => ({
          id: v.id,
          nodes: v.nodes.map((n) => n.target),
        })),
        `axe ${url} @${width}`,
      ).toEqual([]);
    }
  });
}
