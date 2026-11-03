import { afterEach, describe, expect, it, vi } from "vitest";
import { MockRequestService } from "./MockRequestService";
import { HttpApiClient } from "./HttpApiClient";
import type { ActionId, CreateRequestInput } from "../domain/request";

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
    for (const method of ["GET", "POST", "PUT", "PATCH", "DELETE"] as const) {
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
      const fetchSpy = vi.spyOn(globalThis, "fetch");
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
      expect(completed.status).toBe(kind === "it" ? "resolved" : "completed");
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
      if (kind === "it") {
        expect(service.actions(completed, "employee")).toEqual([
          { id: "confirm", label: "Xác nhận hoàn thành" },
        ]);
        const confirmed = await service.transition(
          completed.id,
          "confirm",
          "Đã kiểm tra kết quả",
          completed.version,
          "employee",
        );
        expect(confirmed.status).toBe("confirmed");
        expect(confirmed.version).toBe(completed.version + 1);
        expect(confirmed.timeline).toHaveLength(5);
        expect(confirmed.timeline.slice(-2)).toMatchObject([
          {
            title: "IT đã xử lý và thông báo kết quả",
            actor: "Trần Bình",
            note: "Đã kiểm tra hoàn tất",
          },
          {
            title: "Người gửi đã xác nhận hoàn thành",
            actor: "Mai An",
            note: "Đã kiểm tra kết quả",
          },
        ]);
        for (const role of ["employee", "processor"] as const) {
          expect(service.actions(confirmed, role)).toEqual([]);
          for (const action of ["confirm", "complete", "close"] as ActionId[]) {
            await expect(
              service.transition(
                confirmed.id,
                action,
                "Không thêm thao tác đóng",
                confirmed.version,
                role,
              ),
            ).rejects.toMatchObject({ code: "403" });
          }
        }
        expect(await service.get(confirmed.id, "employee")).toEqual(confirmed);
      } else {
        expect(service.actions(completed, "employee")).toEqual([]);
        await expect(
          service.transition(
            completed.id,
            "confirm",
            "Không thêm nghiệm thu",
            completed.version,
            "employee",
          ),
        ).rejects.toMatchObject({ code: "403" });
        expect(await service.get(completed.id, "employee")).toEqual(completed);
      }
      expect(fetchSpy).not.toHaveBeenCalled();
    },
  );
  it("scopes mock fixtures and gives identical hidden/missing responses", async () => {
    const service = new MockRequestService(0);
    expect(
      (await service.list("employee")).every((r) => r.requester === "Mai An"),
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

describe("APP-08 IT confirmation MOCK guards", () => {
  it("permits only the original employee requester, checking stored data again on write", async () => {
    const service = new MockRequestService(0);
    const own = await service.get("DX-1042", "employee");
    const other = await service.get("DX-1044", "processor");
    expect(own.requester).toBe("Mai An");
    expect(other.requester).toBe("Đức Minh");
    expect(service.actions(other, "employee")).toEqual([]);
    for (const item of [own, other]) {
      expect(service.actions(item, "processor")).toEqual([]);
      await expect(
        service.transition(
          item.id,
          "confirm",
          "Xác nhận thay người gửi",
          item.version,
          "processor",
        ),
      ).rejects.toMatchObject({ code: "403" });
      expect(await service.get(item.id, "processor")).toEqual(item);
    }
    // A caller-mutated snapshot cannot grant access to the stored request.
    expect(
      service.actions({ ...other, requester: "Mai An" }, "employee"),
    ).toEqual([{ id: "confirm", label: "Xác nhận hoàn thành" }]);
    const hidden = await service
      .transition(
        other.id,
        "confirm",
        "Không sở hữu yêu cầu",
        other.version,
        "employee",
      )
      .catch((error: unknown) => error);
    const missing = await service
      .transition(
        "NOT-FOUND",
        "confirm",
        "Không tồn tại yêu cầu",
        other.version,
        "employee",
      )
      .catch((error: unknown) => error);
    expect(hidden).toMatchObject({ code: "404" });
    expect(missing).toMatchObject({
      code: "404",
      message: (hidden as Error).message,
    });
    expect(await service.get(other.id, "processor")).toEqual(other);
  });

  it("does not let a processor confirm even a request that processor created", async () => {
    const service = new MockRequestService(0);
    const created = await service.create(input, "processor");
    const approved = await service.transition(
      created.id,
      "approve",
      "Đã kiểm tra phiếu",
      created.version,
      "processor",
    );
    const processing = await service.transition(
      created.id,
      "start",
      "Đã tiếp nhận việc",
      approved.version,
      "processor",
    );
    const resolved = await service.transition(
      created.id,
      "complete",
      "Đã xử lý sự cố",
      processing.version,
      "processor",
    );
    expect(resolved.status).toBe("resolved");
    expect(service.actions(resolved, "processor")).toEqual([]);
    expect(service.actions(resolved, "employee")).toEqual([]);
    await expect(
      service.transition(
        resolved.id,
        "confirm",
        "Tự xác nhận kết quả",
        resolved.version,
        "processor",
      ),
    ).rejects.toMatchObject({ code: "403" });
    await expect(
      service.transition(
        resolved.id,
        "confirm",
        "Không sở hữu phiếu",
        resolved.version,
        "employee",
      ),
    ).rejects.toMatchObject({ code: "404" });
    expect(await service.get(resolved.id, "processor")).toEqual(resolved);
  });

  it.each([
    "pending",
    "approved",
    "processing",
    "completed",
    "confirmed",
    "rejected",
  ] as const)(
    "does not offer requester confirmation for IT status %s",
    async (status) => {
      const service = new MockRequestService(0);
      const own = await service.get("DX-1042", "employee");
      expect(service.actions({ ...own, status }, "employee")).toEqual([]);
    },
  );

  it("rejects premature confirmation against the actual stored status without mutation", async () => {
    const service = new MockRequestService(0);
    const before = await service.get("DX-1046", "employee");
    await expect(
      service.transition(
        before.id,
        "confirm",
        "Chưa hoàn tất xử lý",
        before.version,
        "employee",
      ),
    ).rejects.toMatchObject({ code: "403" });
    expect(await service.get(before.id, "employee")).toEqual(before);
  });

  it("preserves version, note validation and single mutation under concurrent confirmation", async () => {
    const service = new MockRequestService(0);
    const before = await service.get("DX-1042", "employee");
    await expect(
      service.transition(
        before.id,
        "confirm",
        "Đã kiểm tra kết quả",
        before.version - 1,
        "employee",
      ),
    ).rejects.toMatchObject({ code: "CONFLICT" });
    for (const note of ["", "1234", "a".repeat(1001)]) {
      await expect(
        service.transition(
          before.id,
          "confirm",
          note,
          before.version,
          "employee",
        ),
      ).rejects.toMatchObject({ code: "VALIDATION" });
      expect(await service.get(before.id, "employee")).toEqual(before);
    }
    const outcomes = await Promise.allSettled([
      service.transition(
        before.id,
        "confirm",
        "Đã kiểm tra kết quả",
        before.version,
        "employee",
      ),
      service.transition(
        before.id,
        "confirm",
        "Đã kiểm tra kết quả",
        before.version,
        "employee",
      ),
    ]);
    expect(
      outcomes.filter((outcome) => outcome.status === "fulfilled"),
    ).toHaveLength(1);
    expect(
      outcomes.find((outcome) => outcome.status === "rejected"),
    ).toMatchObject({ status: "rejected", reason: { code: "CONFLICT" } });
    const after = await service.get(before.id, "employee");
    expect(after.status).toBe("confirmed");
    expect(after.version).toBe(before.version + 1);
    expect(after.timeline).toHaveLength(before.timeline.length + 1);
    expect(after.timeline.at(-1)?.actor).toBe(before.requester);
  });

  it("never retries or mutates after an uncertain confirmation", async () => {
    const service = new MockRequestService(0);
    const before = await service.get("DX-1042", "employee");
    const transition = vi.spyOn(service, "transition");
    service.setFault("unknown");
    await expect(
      service.transition(
        before.id,
        "confirm",
        "Đã kiểm tra kết quả",
        before.version,
        "employee",
      ),
    ).rejects.toMatchObject({ code: "COMMIT_OUTCOME_UNKNOWN" });
    expect(transition).toHaveBeenCalledTimes(1);
    expect(await service.get(before.id, "employee")).toEqual(before);
  });
});
