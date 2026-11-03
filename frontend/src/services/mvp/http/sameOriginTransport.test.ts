import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { helpdeskId, helpdeskWire } from "../../../test/helpdeskFixtures";
import { HelpdeskApiClient } from "./HelpdeskApiClient";
import {
  bootstrapHelpdeskSession,
  sameOriginHelpdeskTransport,
} from "./sameOriginTransport";

const base = "/api/v1/mvp/requests";
beforeEach(() => vi.stubGlobal("location", new URL("https://localhost:8443")));
afterEach(() => vi.unstubAllGlobals());

describe("local same-origin BFF boundary", () => {
  it("bootstraps only session state and keeps CSRF in memory", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(
        Response.json({ authenticated: true, csrfToken: "csrf-test" }),
      );
    expect(await bootstrapHelpdeskSession(fetcher)).toEqual({
      csrfToken: "csrf-test",
    });
    expect(fetcher).toHaveBeenCalledExactlyOnceWith(
      "/bff/session",
      expect.objectContaining({
        credentials: "same-origin",
        mode: "same-origin",
        redirect: "error",
        cache: "no-store",
      }),
    );
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
  });

  it.each([false, true])(
    "rejects unusable session state (malformed=%s)",
    async (malformed) => {
      const fetcher = vi
        .fn<typeof fetch>()
        .mockResolvedValue(Response.json({ authenticated: malformed }));
      if (malformed)
        await expect(bootstrapHelpdeskSession(fetcher)).rejects.toThrow();
      else expect(await bootstrapHelpdeskSession(fetcher)).toBeNull();
    },
  );

  it.each(["http://localhost:8443", "https://portal.example.test"])(
    "blocks non-local or insecure origins: %s",
    async (origin) => {
      vi.stubGlobal("location", new URL(origin));
      const fetcher = vi.fn<typeof fetch>();
      await expect(bootstrapHelpdeskSession(fetcher)).rejects.toThrow();
      expect(() => sameOriginHelpdeskTransport("csrf", fetcher)).toThrow();
      expect(fetcher).not.toHaveBeenCalled();
    },
  );

  it("maps exactly the nine routes with CSRF only on POST and no auth headers", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockImplementation(async () => Response.json({}));
    const transport = sameOriginHelpdeskTransport("csrf-test", fetcher);
    const routes = [
      ["GET", `${base}?limit=20&after=${helpdeskId}`],
      ["POST", base],
      ["GET", `${base}/${helpdeskId}`],
      ["GET", `${base}/${helpdeskId}/history`],
      ...["triage", "assign", "start", "resolve", "confirm"].map((a) => [
        "POST",
        `${base}/${helpdeskId}/${a}`,
      ]),
    ];
    for (const [method, path] of routes) {
      await transport({
        method: method as "GET" | "POST",
        path,
        headers: { Accept: "application/json" },
        body: {},
      });
      const [url, init] = fetcher.mock.calls.at(-1)!;
      expect(url).toBe(`/bff${path}`);
      expect(init).toMatchObject({
        credentials: "same-origin",
        mode: "same-origin",
        redirect: "error",
        cache: "no-store",
      });
      const headers = new Headers(init?.headers);
      expect(headers.has("Authorization")).toBe(false);
      expect(headers.has("X-Role")).toBe(false);
      expect(headers.get("X-CSRF-TOKEN")).toBe(
        method === "POST" ? "csrf-test" : null,
      );
    }
    expect(fetcher).toHaveBeenCalledTimes(9);
  });

  it.each([
    ["GET", "https://evil.test/api/v1/mvp/requests"],
    ["GET", "//evil.test/api/v1/mvp/requests"],
    ["GET", `${base}/../requests`],
    ["POST", `${base}/${helpdeskId}/delete`],
    ["GET", `${base}/${helpdeskId}/triage`],
    ["POST", `${base}/${helpdeskId}/history`],
    ["GET", `${base}?target=evil`],
    ["POST", `${base}?limit=1`],
    ["GET", `${base}?limit=1&limit=2`],
    ["GET", `${base}#fragment`],
  ])("rejects unapproved route %s %s before fetch", async (method, path) => {
    const fetcher = vi.fn<typeof fetch>();
    await expect(
      sameOriginHelpdeskTransport(
        "csrf",
        fetcher,
      )({
        method: method as "GET" | "POST",
        path,
        headers: { Accept: "application/json" },
      }),
    ).rejects.toThrow();
    expect(fetcher).not.toHaveBeenCalled();
  });

  it.each([
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
  ])("preserves %s without retry", async (status, code) => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response("", { status: Number(status) }));
    const client = new HelpdeskApiClient(
      sameOriginHelpdeskTransport("csrf", fetcher),
      vi.fn(),
    );
    await expect(
      client.triage(helpdeskId, { expectedVersion: 0 }),
    ).rejects.toMatchObject({ code });
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it("invalidates a 401 session and does not replay further operations", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValue(new Response("", { status: 401 }));
    const invalid = vi.fn();
    const client = new HelpdeskApiClient(
      sameOriginHelpdeskTransport("csrf", fetcher),
      invalid,
    );
    await expect(client.list()).rejects.toMatchObject({
      code: "UNAUTHENTICATED",
    });
    await expect(
      client.create({ title: "test", description: "test" }),
    ).rejects.toMatchObject({ code: "UNAUTHENTICATED" });
    expect(invalid).toHaveBeenCalledTimes(1);
    expect(fetcher).toHaveBeenCalledTimes(1);
  });

  it.each(["lost", "unknown", "malformed"])(
    "reports uncertain write (%s) without retry",
    async (failure) => {
      const fetcher = vi.fn<typeof fetch>();
      if (failure === "lost")
        fetcher.mockRejectedValue(new TypeError("connection lost"));
      else
        fetcher.mockResolvedValue(
          failure === "unknown"
            ? Response.json({ code: "COMMIT_OUTCOME_UNKNOWN" }, { status: 503 })
            : new Response("not-json", { status: 201 }),
        );
      const client = new HelpdeskApiClient(
        sameOriginHelpdeskTransport("csrf", fetcher),
        vi.fn(),
      );
      await expect(
        client.create({ title: "test", description: "test" }),
      ).rejects.toMatchObject({ code: "COMMIT_OUTCOME_UNKNOWN" });
      expect(fetcher).toHaveBeenCalledTimes(1);
    },
  );

  it("handles read network failure and valid committed responses", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockRejectedValueOnce(new TypeError("offline"))
      .mockResolvedValueOnce(Response.json(helpdeskWire(), { status: 201 }));
    const client = new HelpdeskApiClient(
      sameOriginHelpdeskTransport("csrf", fetcher),
      vi.fn(),
    );
    await expect(client.list()).rejects.toMatchObject({
      code: "NETWORK_FAILURE",
    });
    expect(
      (await client.create({ title: "test", description: "test" })).id,
    ).toBe(helpdeskId);
    expect(fetcher).toHaveBeenCalledTimes(2);
  });
});
