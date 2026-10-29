import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PortalProvider } from "../context";
import { requestService } from "../services/MockRequestService";
import { NewRequest } from "./NewRequest";

beforeEach(() => {
  sessionStorage.setItem("dxos-demo-role", "employee");
  requestService.reset();
  vi.restoreAllMocks();
});
function setup() {
  return render(
    <MemoryRouter initialEntries={["/requests/new"]}>
      <PortalProvider>
        <Routes>
          <Route path="/requests/new" element={<NewRequest />} />
          <Route path="/requests/:id" element={<p>Chi tiết yêu cầu mới</p>} />
        </Routes>
      </PortalProvider>
    </MemoryRouter>,
  );
}
async function fill() {
  const user = userEvent.setup();
  await user.type(
    screen.getByRole("textbox", { name: /Tiêu đề yêu cầu/ }),
    "Cài đặt phần mềm cho máy tính",
  );
  await user.type(
    screen.getByRole("textbox", { name: /Mô tả chi tiết/ }),
    "Đề nghị cài đặt các công cụ phát triển cho dự án mới.",
  );
  await user.type(screen.getByRole("textbox", { name: /Vị trí/ }), "Tầng 3");
  return user;
}
describe("request form", () => {
  it("shows accessible validation and focuses the first invalid field", async () => {
    setup();
    await userEvent.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
    expect(screen.getByText("Tiêu đề cần từ 8 đến 120 ký tự.")).toBeVisible();
    expect(
      screen.getByRole("textbox", { name: /Tiêu đề yêu cầu/ }),
    ).toHaveFocus();
    expect(screen.queryByLabelText(/upload/i)).not.toBeInTheDocument();
    expect(screen.getByText("BLOCKED FEATURE")).toBeVisible();
  });
  it("creates a mock request and announces success", async () => {
    setup();
    const user = await fill();
    await user.click(screen.getByRole("button", { name: "Gửi yêu cầu" }));
    expect(await screen.findByText("Chi tiết yêu cầu mới")).toBeVisible();
    expect(screen.getByRole("status")).toHaveTextContent("Đã tạo yêu cầu demo");
  });
  it("blocks duplicate submit and further writes after an uncertain result", async () => {
    const spy = vi.spyOn(requestService, "create");
    setup();
    const user = await fill();
    requestService.setFault("unknown");
    await user.dblClick(screen.getByRole("button", { name: "Gửi yêu cầu" }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Chưa xác định kết quả",
      ),
    );
    expect(screen.getByRole("button", { name: "Gửi yêu cầu" })).toBeDisabled();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
