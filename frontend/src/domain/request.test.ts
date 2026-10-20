import { describe, expect, it } from "vitest";
import {
  filterRequests,
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
});
