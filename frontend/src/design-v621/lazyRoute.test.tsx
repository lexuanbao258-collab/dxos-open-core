// SPDX-FileCopyrightText: 2026 TRICORE contributors
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { App } from "../App";
import { PreviewLoading } from "./PreviewLoading";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("lazy /design-v621 route", () => {
  it("shows an accessible loading status, then the preview", async () => {
    vi.spyOn(window, "scrollTo").mockImplementation(() => undefined);
    render(
      <MemoryRouter initialEntries={["/design-v621/mine"]}>
        <App />
      </MemoryRouter>,
    );
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Đang tải bản xem trước OpenTrix…");
    expect(
      await screen.findByRole("heading", { name: "Yêu cầu của tôi" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Đang tải bản xem trước OpenTrix…")).toBeNull();
  });

  it("fallback is a plain polite status with no interactive content", () => {
    render(<PreviewLoading />);
    const status = screen.getByRole("status");
    expect(status.querySelector("a,button,input")).toBeNull();
  });
});
