import { describe, expect, it } from "vitest";
import {
  filterRequests,
  statusLabels,
  validateRequest,
  type CreateRequestInput,
} from "./request";
import { makeFixtures } from "../fixtures/requests";

const input: CreateRequestInput = {
  kind: "it",
  title: "Cài đặt máy tính mới",
  description: "Cần cài đặt môi trường làm việc cho dự án mới.",
  priority: "normal",
  location: "Tầng 3",
};
describe("shared request model", () => {
  it.each(["it", "facility"] as const)(
    "validates both %s services through one schema",
    (kind) => {
      expect(validateRequest({ ...input, kind })).toEqual({});
    },
  );
  it("rejects blank, short and oversized fields", () => {
    expect(
      validateRequest({
        ...input,
        title: "   ",
        description: "ngắn",
        location: "",
      }),
    ).toHaveProperty("title");
    expect(
      Object.keys(
        validateRequest({
          ...input,
          title: "x".repeat(121),
          description: "x".repeat(2001),
          location: "x".repeat(101),
        }),
      ),
    ).toHaveLength(3);
  });
  it("searches Vietnamese without accents and combines filters", () => {
    expect(
      filterRequests(makeFixtures(), "dieu hoa", "facility", "processing"),
    ).toHaveLength(1);
    expect(filterRequests(makeFixtures(), "DX-1048")).toHaveLength(1);
    expect(filterRequests(makeFixtures(), "khong co ket qua")).toHaveLength(0);
    expect(
      filterRequests(makeFixtures(), "", "it").every((r) => r.kind === "it"),
    ).toBe(true);
  });
  it("keeps resolved IT, confirmed IT and completed Facility distinct", () => {
    expect(statusLabels.resolved).toBe("Chờ nhân viên xác nhận");
    expect(statusLabels.confirmed).toBe("Đã xác nhận hoàn thành");
    expect(statusLabels.completed).toBe("Hoàn thành");
    expect(statusLabels).not.toHaveProperty("closed");
    const fixtures = makeFixtures();
    const resolved = filterRequests(fixtures, "", "it", "resolved");
    expect(resolved.map((item) => item.id)).toEqual(["DX-1044", "DX-1042"]);
    for (const item of resolved) {
      expect(item.timeline.at(-1)?.title).toBe(
        "IT đã xử lý và thông báo kết quả",
      );
      expect(item.updatedAt).toBe(item.timeline.at(-1)?.at);
      expect(item.version).toBe(item.timeline.length - 1);
    }
    const confirmed = { ...resolved[1], status: "confirmed" as const };
    expect(
      filterRequests([...fixtures, confirmed], "", "it", "confirmed"),
    ).toEqual([confirmed]);
    expect(
      filterRequests(fixtures, "", "facility", "completed").map(
        (item) => item.id,
      ),
    ).toEqual(["DX-1039"]);
  });
});
