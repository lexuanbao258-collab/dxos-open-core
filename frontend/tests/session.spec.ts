import { test, expect } from "@playwright/test";

test("demo login restores the tab; logout keeps protected routes closed after back and reload", async ({
  page,
}) => {
  const apiCalls: string[] = [];
  page.on("request", (request) => {
    if (new URL(request.url()).pathname.startsWith("/api/"))
      apiCalls.push(request.url());
  });
  await page.goto("/requests");
  await expect(page).toHaveURL(/\/session$/);
  await page.getByRole("button", { name: "Vào phiên demo" }).click();
  await expect(page).toHaveURL(/\/$/);
  expect(
    await page.evaluate(() => sessionStorage.getItem("dxos-demo-role")),
  ).toBe("employee");
  await page.goto("/requests");
  await expect(page).toHaveURL(/\/requests$/);
  await expect(page.getByRole("link", { name: "Xem DX-1048" })).toBeVisible();
  await page.reload();
  await expect(page).toHaveURL(/\/requests$/);
  await expect(page.getByRole("link", { name: "Xem DX-1048" })).toBeVisible();
  await page.getByRole("navigation").locator('a[href="/session"]').click();
  await page.getByRole("button", { name: "Kết thúc phiên" }).click();
  expect(
    await page.evaluate(() => sessionStorage.getItem("dxos-demo-role")),
  ).toBeNull();
  await page.goBack();
  await expect(page).toHaveURL(/\/session$/);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Bắt đầu trải nghiệm" }),
  ).toBeVisible();
  await page.goto("/requests/new");
  await expect(page).toHaveURL(/\/session$/);
  await expect(
    page.getByRole("heading", { name: "Bắt đầu trải nghiệm" }),
  ).toBeVisible();
  expect(apiCalls).toEqual([]);
});

test("demo 401 clears the session and requires explicit reentry", async ({
  page,
}) => {
  await page.goto("/session");
  await page.getByRole("button", { name: "Vào phiên demo" }).click();
  await expect(page).toHaveURL(/\/$/);
  await page.goto("/requests");
  await expect(page.getByRole("link", { name: "Xem DX-1048" })).toBeVisible();
  await page
    .getByRole("link", { name: "Trạng thái tích hợp", exact: true })
    .click();
  await page.getByLabel("Kịch bản lỗi").selectOption("401");
  await page.getByRole("button", { name: "Áp dụng lỗi mô phỏng" }).click();
  await page
    .getByRole("link", { name: "Mở danh sách yêu cầu", exact: true })
    .click();
  await expect(page).toHaveURL(/\/session$/);
  await expect(page.getByRole("alert")).toContainText("Phiên đã hết hạn");
  expect(
    await page.evaluate(() => sessionStorage.getItem("dxos-demo-role")),
  ).toBeNull();
  await page.getByRole("button", { name: "Vào phiên demo" }).click();
  await expect(page).toHaveURL(/\/$/);
  expect(
    await page.evaluate(() => sessionStorage.getItem("dxos-demo-role")),
  ).toBe("employee");
  await page.getByRole("navigation").locator('a[href="/session"]').click();
  await expect(
    page.getByRole("heading", { name: "Phiên demo đang hoạt động" }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
});
