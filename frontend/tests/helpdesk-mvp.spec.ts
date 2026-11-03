import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("APP-14 fake transport journey reuses Portal shell with no API/external requests", async ({
  page,
}) => {
  test.skip(
    !test.info().project.metadata.fakeHelpdeskPreview,
    "Use tests/mvp.playwright.config.ts for the explicit fake preview entry.",
  );
  const unexpected: string[] = [];
  const errors: string[] = [];
  page.on("request", (request) => {
    if (
      !request.url().startsWith("http://127.0.0.1:4173/") ||
      /\/api\//.test(request.url())
    )
      unexpected.push(request.url());
  });
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/tests/mvp-preview.html");
  await page.getByRole("button", { name: "Vào phiên demo" }).click();
  await page.getByRole("link", { name: "IT Helpdesk MVP · kiểm thử" }).click();
  await expect(
    page.getByText("Chưa có yêu cầu IT", { exact: true }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Tạo yêu cầu IT", exact: true }).click();
  await page
    .getByRole("textbox", { name: "Tiêu đề yêu cầu" })
    .fill("APP-14 kiểm tra báo cáo công việc");
  await page
    .getByRole("textbox", { name: "Mô tả chi tiết" })
    .fill("Không lưu được báo cáo trong phần mềm nội bộ.");
  await page.getByRole("button", { name: "Gửi yêu cầu IT" }).dblclick();
  await page
    .getByRole("link", { name: /APP-14 kiểm tra báo cáo công việc/ })
    .click();
  await expect(page.getByTestId("mvp-state")).toHaveText("SUBMITTED");
  await page.getByRole("button", { name: "Tiếp nhận", exact: true }).dblclick();
  await expect(page.getByTestId("mvp-state")).toHaveText("TRIAGED");
  await page
    .getByRole("combobox", { name: "Người xử lý" })
    .selectOption("choice-0");
  await page.getByRole("button", { name: "Phân công", exact: true }).dblclick();
  await expect(page.getByTestId("mvp-assignment-version")).toHaveText("1");
  await expect(page.getByTestId("mvp-version")).toHaveText("2");
  await page.getByRole("button", { name: "Bắt đầu xử lý" }).click();
  await expect(page.getByTestId("mvp-state")).toHaveText("IN_PROGRESS");
  await page
    .getByRole("textbox", { name: "Kết quả xử lý" })
    .fill("Đã sửa lỗi và kiểm tra lưu báo cáo.");
  await page.getByRole("button", { name: "Ghi kết quả xử lý" }).click();
  await expect(page.getByTestId("mvp-state")).toHaveText("RESOLVED");
  await page.getByRole("button", { name: "Xác nhận hoàn thành" }).dblclick();
  await expect(page.getByTestId("mvp-state")).toHaveText("CONFIRMED");
  await expect(
    page
      .getByRole("region", { name: "Lịch sử Workflow" })
      .getByRole("listitem"),
  ).toHaveCount(4);
  await page
    .getByRole("button", { name: "Tải lại chi tiết và lịch sử" })
    .click();
  await expect(page.getByTestId("mvp-state")).toHaveText("CONFIRMED");
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1100 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBeTruthy();
    const result = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(result.violations.map(({ id }) => id)).toEqual([]);
  }
  expect(unexpected).toEqual([]);
  expect(errors).toEqual([]);
});
