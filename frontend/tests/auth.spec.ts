import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const evidence = resolve("..", "docs", "evidence", "issue39-portal", "auth");

for (const route of ["login", "register"]) {
  test(`${route}: responsive layouts, keyboard, WCAG and screenshots`, async ({
    page,
  }) => {
    test.setTimeout(60000);
    for (const [width, height, name] of [
      [1440, 1100, "desktop"],
      [834, 1112, "tablet"],
      [390, 844, "mobile"],
      [320, 740, "small-mobile"],
    ] as const) {
      await page.setViewportSize({ width, height });
      await page.goto(`/${route}`);
      await page.waitForLoadState("networkidle");
      await page.evaluate(() => document.fonts.ready);
      await expect(page.getByRole("heading", { level: 1 })).toBeFocused();
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth),
      ).toBe(width);
      await page.keyboard.press("Tab");
      await expect(
        page.getByLabel(route === "login" ? "Email" : "Họ và tên", {
          exact: true,
        }),
      ).toBeFocused();
      const results = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze();
      expect(results.violations).toEqual([]);
      await mkdir(evidence, { recursive: true });
      await page.screenshot({
        path: resolve(evidence, `${route}-${name}.png`),
        fullPage: true,
        animations: "disabled",
      });
    }
    await page
      .getByRole("button", {
        name: route === "login" ? "Đăng nhập" : "Đăng ký tài khoản",
        exact: true,
      })
      .click();
    await expect(page.getByLabel("Email", { exact: true })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(
      (
        await new AxeBuilder({ page })
          .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
          .analyze()
      ).violations,
    ).toEqual([]);
  });
}

test("blocked forms never send credentials, register APIs or grant a demo role", async ({
  page,
}) => {
  const outgoing: string[] = [];
  const consoleMessages: string[] = [];
  const errors: string[] = [];
  page.on("request", (request) => {
    if (
      ["fetch", "xhr", "ping", "websocket"].includes(request.resourceType()) ||
      request.method() !== "GET" ||
      !request.url().startsWith("http://127.0.0.1:4173/")
    )
      outgoing.push(`${request.method()} ${request.url()}`);
  });
  page.on("console", (message) => consoleMessages.push(message.text()));
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/login");
  await page
    .getByLabel("Email", { exact: true })
    .fill("preview@example.edu.vn");
  await page
    .getByLabel("Mật khẩu", { exact: true })
    .fill("Synthetic preview only!");
  await page.getByRole("checkbox", { name: "Ghi nhớ email" }).check();
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("BLOCKED FEATURE");
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByLabel("Mật khẩu", { exact: true })).toHaveValue("");
  expect(await page.evaluate(() => ({ ...localStorage }))).toEqual({
    "dxos-remembered-email-v1": "preview@example.edu.vn",
  });
  expect(await page.evaluate(() => ({ ...sessionStorage }))).toEqual({});
  await page.reload();
  await expect(page.getByLabel("Email", { exact: true })).toHaveValue(
    "preview@example.edu.vn",
  );
  await expect(page.getByLabel("Mật khẩu", { exact: true })).toHaveValue("");
  await page.getByRole("checkbox", { name: "Ghi nhớ email" }).uncheck();
  await page.getByRole("button", { name: "Quên mật khẩu?" }).click();
  await expect(page.getByRole("alert")).toContainText("BLOCKED FEATURE");
  await page.getByRole("link", { name: "Đăng ký", exact: true }).click();
  await expect(page).toHaveURL(/\/register$/);
  await page.getByLabel("Họ và tên").fill("Người dùng thử");
  await page
    .getByLabel("Email", { exact: true })
    .fill("preview@example.edu.vn");
  await page
    .getByLabel("Mật khẩu", { exact: true })
    .fill("Synthetic preview only!");
  await page
    .getByLabel("Xác nhận mật khẩu", { exact: true })
    .fill("Different preview only!");
  await page.getByRole("button", { name: "Đăng ký tài khoản" }).click();
  await expect(page.getByText("Mật khẩu xác nhận chưa khớp.")).toBeVisible();
  await expect(
    page.getByLabel("Xác nhận mật khẩu", { exact: true }),
  ).toBeFocused();
  await page
    .getByLabel("Xác nhận mật khẩu", { exact: true })
    .fill("Synthetic preview only!");
  await expect(page.getByRole("radio")).toHaveCount(2);
  await page.getByRole("radio", { name: "Giảng viên", exact: true }).check();
  await page.getByRole("button", { name: "Đăng ký tài khoản" }).click();
  await expect(page.getByRole("alert")).toContainText("Chưa tạo tài khoản");
  await expect(page).toHaveURL(/\/register$/);
  for (const label of ["Mật khẩu", "Xác nhận mật khẩu"])
    await expect(page.getByLabel(label, { exact: true })).toHaveValue("");
  expect(await page.evaluate(() => ({ ...localStorage }))).toEqual({});
  expect(await page.evaluate(() => ({ ...sessionStorage }))).toEqual({});
  expect(outgoing).toEqual([]);
  expect(
    consoleMessages.some((message) =>
      message.includes("Synthetic preview only!"),
    ),
  ).toBe(false);
  expect(errors).toEqual([]);
  expect(
    (
      await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
        .analyze()
    ).violations,
  ).toEqual([]);
  await page.getByRole("link", { name: "Đăng nhập", exact: true }).click();
  await expect(page.getByLabel("Mật khẩu", { exact: true })).toHaveValue("");
  await page.getByRole("link", { name: "Vào phiên Demo", exact: true }).click();
  await expect(page).toHaveURL(/\/session$/);
  await page
    .getByRole("button", { name: "Vào phiên demo", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: /Chào buổi sáng/ }),
  ).toBeVisible();
});
