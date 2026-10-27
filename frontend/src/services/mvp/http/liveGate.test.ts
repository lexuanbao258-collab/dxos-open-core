import { afterEach, describe, expect, it, vi } from "vitest";
import { HttpApiClient } from "../../HttpApiClient";
import {
  MockRequestService,
  requestService as mockService,
} from "../../MockRequestService";
import { requestService as portalService } from "../../portalService";
import { helpdeskId } from "../../../test/helpdeskFixtures";

afterEach(() => vi.restoreAllMocks());

describe("APP-13 LIVE_BLOCKED regression", () => {
  it("keeps MOCK as composition-root default and blocks all nine Helpdesk routes without network", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    const xhrSpy = vi.spyOn(XMLHttpRequest.prototype, "send");
    const client = new HttpApiClient();
    const base = "/api/v1/mvp/requests";
    const routes = [
      ["POST", base],
      ["GET", base],
      ["GET", `${base}/${helpdeskId}`],
      ...["triage", "assign", "start", "resolve", "confirm"].map((action) => [
        "POST",
        `${base}/${helpdeskId}/${action}`,
      ]),
      ["GET", `${base}/${helpdeskId}/history`],
    ] as const;
    expect(routes).toHaveLength(9);
    expect(portalService).toBe(mockService);
    expect(portalService).toBeInstanceOf(MockRequestService);
    expect(client.mode).toBe("BLOCKED");
    for (const [method, path] of routes) {
      await expect(
        client.request(method as "GET" | "POST", path),
      ).rejects.toMatchObject({ code: "LIVE_BLOCKED" });
    }
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
  });
});
