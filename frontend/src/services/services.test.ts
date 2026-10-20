import { afterEach, describe, expect, it, vi } from "vitest";
import { MockRequestService } from "./MockRequestService";
import { HttpApiClient } from "./HttpApiClient";
import type { CreateRequestInput } from "../domain/request";

const input: CreateRequestInput = {
  kind: "it",
  title: "Kiểm tra thiết bị mới",
  description: "Thiết bị mới cần được kiểm tra và cài đặt phần mềm.",
  priority: "normal",
  location: "Phòng kỹ thuật",
};
afterEach(() => vi.restoreAllMocks());
describe("closed HTTP boundary", () => {
  it("never calls fetch, including unregistered Workflow routes and mutations", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const client = new HttpApiClient();
    for (const method of ["GET", "POST", "PATCH", "DELETE"] as const) {
      await expect(
        client.request(method, "/api/v1/workflow-instances", {}),
      ).rejects.toMatchObject({ code: "LIVE_BLOCKED" });
    }
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
describe("mock application responses", () => {
  it.each(["it", "facility"] as const)(
    "supports the shared create/approve/process/history UI contract for %s",
    async (kind) => {
      const service = new MockRequestService(0);
      const created = await service.create({ ...input, kind }, "employee");
      expect(created.status).toBe("pending");
      expect(service.actions(created, "employee")).toEqual([]);
      const approved = await service.transition(
        created.id,
        "approve",
        "Đồng ý triển khai",
        0,
        "processor",
      );
      const processing = await service.transition(
        created.id,
        "start",
        "Đã tiếp nhận xử lý",
        approved.version,
        "processor",
      );
      const completed = await service.transition(
        created.id,
        "complete",
        "Đã kiểm tra hoàn tất",
        processing.version,
        "processor",
      );
      expect(completed.timeline).toHaveLength(4);
      expect(completed.status).toBe("completed");
      expect(service.actions(completed, "processor")).toEqual([]);
      await expect(
        service.transition(
          created.id,
          "start",
          "Không được mở lại",
          completed.version,
          "processor",
        ),
      ).rejects.toMatchObject({ code: "403" });
    },
  );
  it("scopes mock fixtures and gives identical hidden/missing responses", async () => {
    const service = new MockRequestService(0);
    expect(
      (await service.list("employee")).every((r) => r.requester === "Ngọc Ánh"),
    ).toBe(true);
    const hidden = await service.get("DX-1047", "employee").catch((e) => e);
    const missing = await service.get("NOT-FOUND", "employee").catch((e) => e);
    expect(hidden.message).toBe(missing.message);
    expect(hidden.code).toBe("404");
    expect((await service.get("DX-1047", "processor")).id).toBe("DX-1047");
  });
  it("prevents stale writes and disallowed actor actions in fixtures", async () => {
    const service = new MockRequestService(0);
    await expect(
      service.transition(
        "DX-1048",
        "approve",
        "Đồng ý yêu cầu",
        99,
        "processor",
      ),
    ).rejects.toMatchObject({ code: "CONFLICT" });
    await expect(
      service.transition("DX-1048", "approve", "Đồng ý yêu cầu", 0, "employee"),
    ).rejects.toMatchObject({ code: "403" });
  });
  it("does not mutate after validation failure", async () => {
    const service = new MockRequestService(0);
    const before = await service.list("processor");
    await expect(
      service.create({ ...input, title: "" }, "employee"),
    ).rejects.toMatchObject({ code: "VALIDATION" });
    expect(await service.list("processor")).toEqual(before);
  });
  it.each(["network", "401", "403", "404"] as const)(
    "exposes %s once, without a read retry",
    async (fault) => {
      const service = new MockRequestService(0);
      service.setFault(fault);
      await expect(service.list("employee")).rejects.toBeInstanceOf(Error);
      expect((await service.list("employee")).length).toBeGreaterThan(0);
    },
  );
  it("never retries uncertain create or transition writes", async () => {
    const service = new MockRequestService(0);
    const create = vi.spyOn(service, "create");
    const transition = vi.spyOn(service, "transition");
    service.setFault("unknown");
    await expect(service.create(input, "employee")).rejects.toMatchObject({
      code: "COMMIT_OUTCOME_UNKNOWN",
    });
    expect(create).toHaveBeenCalledTimes(1);
    service.setFault("unknown");
    await expect(
      service.transition(
        "DX-1048",
        "approve",
        "Đồng ý triển khai",
        0,
        "processor",
      ),
    ).rejects.toMatchObject({ code: "COMMIT_OUTCOME_UNKNOWN" });
    expect(transition).toHaveBeenCalledTimes(1);
    expect((await service.get("DX-1048", "processor")).status).toBe("pending");
  });
  it("returns isolated copies and resets fixtures", async () => {
    const service = new MockRequestService(0);
    const rows = await service.list("employee");
    rows[0].title = "changed";
    expect((await service.get(rows[0].id, "employee")).title).not.toBe(
      "changed",
    );
    await service.create(input, "employee");
    service.reset();
    expect(await service.list("processor")).toHaveLength(10);
  });
});
