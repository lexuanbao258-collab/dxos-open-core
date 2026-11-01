import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  helpdeskCursor,
  helpdeskId,
  helpdeskWire,
} from "../../../test/helpdeskFixtures";
import { HelpdeskApiClient } from "../http/HelpdeskApiClient";
import type { HelpdeskHttpRequest } from "../http/transport";
import { BffSessionClient } from "./BffSessionClient";
import { createHelpdeskBffTransport } from "./createHelpdeskBffTransport";
import type { BffFetch } from "./http";

const BASE = "/api/v1/mvp/requests";
const get: HelpdeskHttpRequest = {
  method: "GET",
  path: BASE,
  headers: { Accept: "application/json" },
};
const post: HelpdeskHttpRequest = {
  ...get,
  method: "POST",
  body: { title: "Test", description: "Test", requestType: "IT_HELPDESK" },
};
const history = [
  {
    transitionId: "triage",
    sourceState: "SUBMITTED",
    targetState: "TRIAGED",
    version: 1,
    occurredAt: "2026-10-26T01:00:00Z",
  },
];
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

async function fixture() {
  const sessionFetch = vi
    .fn<BffFetch>()
    .mockImplementation(async () =>
      json({ authenticated: true, csrfToken: "csrf-test-a" }),
    );
  const session = new BffSessionClient(sessionFetch);
  await session.load();
  const fetchBff = vi.fn<BffFetch>();
  const transport = createHelpdeskBffTransport(session, fetchBff);
  const invalid = vi.fn();
  return {
    session,
    sessionFetch,
    fetchBff,
    transport,
    invalid,
    client: new HelpdeskApiClient(transport, invalid),
  };
}

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
  vi.spyOn(XMLHttpRequest.prototype, "send");
  vi.spyOn(Storage.prototype, "getItem");
  vi.spyOn(Storage.prototype, "setItem");
  vi.spyOn(Storage.prototype, "removeItem");
});
afterEach(() => {
  try {
    expect(fetch).not.toHaveBeenCalled();
    expect(XMLHttpRequest.prototype.send).not.toHaveBeenCalled();
    expect(Storage.prototype.getItem).not.toHaveBeenCalled();
    expect(Storage.prototype.setItem).not.toHaveBeenCalled();
    expect(Storage.prototype.removeItem).not.toHaveBeenCalled();
  } finally {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  }
});

const version = { expectedVersion: 17 };
const assign = { assigneeId: "worker-test", expectedAssignmentVersion: 0 };
const resolve = { ...version, resolution: "Fixed" };
const routes = [
  {
    name: "create",
    method: "POST",
    path: BASE,
    body: post.body,
    status: 201,
    reply: helpdeskWire(),
    invoke: (c: HelpdeskApiClient) =>
      c.create({ title: "Test", description: "Test" }),
  },
  {
    name: "list",
    method: "GET",
    path: `${BASE}?after=${helpdeskCursor}&limit=20`,
    body: undefined,
    status: 200,
    reply: { items: [helpdeskWire()], nextCursor: helpdeskCursor },
    invoke: (c: HelpdeskApiClient) =>
      c.list({ after: helpdeskCursor, limit: 20 }),
  },
  {
    name: "detail",
    method: "GET",
    path: `${BASE}/${helpdeskId}`,
    body: undefined,
    status: 200,
    reply: helpdeskWire(),
    invoke: (c: HelpdeskApiClient) => c.detail(helpdeskId),
  },
  {
    name: "triage",
    method: "POST",
    path: `${BASE}/${helpdeskId}/triage`,
    body: version,
    status: 200,
    reply: helpdeskWire(),
    invoke: (c: HelpdeskApiClient) => c.triage(helpdeskId, version),
  },
  {
    name: "assign",
    method: "POST",
    path: `${BASE}/${helpdeskId}/assign`,
    body: assign,
    status: 200,
    reply: helpdeskWire(),
    invoke: (c: HelpdeskApiClient) => c.assign(helpdeskId, assign),
  },
  {
    name: "start",
    method: "POST",
    path: `${BASE}/${helpdeskId}/start`,
    body: version,
    status: 200,
    reply: helpdeskWire(),
    invoke: (c: HelpdeskApiClient) => c.start(helpdeskId, version),
  },
  {
    name: "resolve",
    method: "POST",
    path: `${BASE}/${helpdeskId}/resolve`,
    body: resolve,
    status: 200,
    reply: helpdeskWire(),
    invoke: (c: HelpdeskApiClient) => c.resolve(helpdeskId, resolve),
  },
  {
    name: "confirm",
    method: "POST",
    path: `${BASE}/${helpdeskId}/confirm`,
    body: version,
    status: 200,
    reply: helpdeskWire({ state: "CONFIRMED" }),
    invoke: (c: HelpdeskApiClient) => c.confirm(helpdeskId, version),
  },
  {
    name: "history",
    method: "GET",
    path: `${BASE}/${helpdeskId}/history`,
    body: undefined,
    status: 200,
    reply: history,
    invoke: (c: HelpdeskApiClient) => c.history(helpdeskId),
  },
] as const;

describe("A05 BFF transport", () => {
  it.each(routes)(
    "$name preserves the reviewed wire request and uses only same-origin BFF",
    async (route) => {
      const f = await fixture();
      f.fetchBff.mockResolvedValue(json(route.reply, route.status));
      await route.invoke(f.client);
      expect(f.fetchBff).toHaveBeenCalledExactlyOnceWith(`/bff${route.path}`, {
        method: route.method,
        mode: "same-origin",
        credentials: "same-origin",
        redirect: "error",
        cache: "no-store",
        headers: {
          Accept: "application/json",
          ...(route.method === "POST"
            ? {
                "Content-Type": "application/json",
                "X-CSRF-TOKEN": "csrf-test-a",
              }
            : {}),
        },
        ...(route.body ? { body: JSON.stringify(route.body) } : {}),
      });
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
      expect(f.invalid).not.toHaveBeenCalled();
    },
  );

  it.each([
    "",
    "?limit=1",
    "?limit=100",
    `?after=${helpdeskCursor}`,
    `?limit=20&after=${helpdeskCursor}`,
  ])("accepts only reviewed optional list parameters: %s", async (query) => {
    const f = await fixture();
    f.fetchBff.mockResolvedValue(json({ items: [], nextCursor: null }));
    await f.transport({ ...get, path: BASE + query });
    expect(f.fetchBff.mock.calls[0][0]).toBe(`/bff${BASE}${query}`);
  });

  it.each([
    "https://evil.example/api/v1/mvp/requests",
    "http://127.0.0.1/api/v1/mvp/requests",
    "//evil.example/api/v1/mvp/requests",
    `/bff${BASE}`,
    `${BASE}/`,
    `${BASE}x`,
    `${BASE}/../requests`,
    `${BASE}/%2e%2e/requests`,
    `${BASE}#fragment`,
    `${BASE}?`,
    `${BASE}?limit=20&`,
    `${BASE}?limit=20&&after=${helpdeskCursor}`,
    `${BASE}?limit=20#fragment`,
    `${BASE}?limit=0`,
    `${BASE}?limit=101`,
    `${BASE}?limit=1.5`,
    `${BASE}?limit=01`,
    `${BASE}?limit=1%0A`,
    `${BASE}?limit=20&limit=30`,
    `${BASE}?after=bad`,
    `${BASE}?after=${helpdeskCursor}%0A`,
    `${BASE}?role=admin`,
    `${BASE}?url=https://evil.example`,
    `${BASE}/${helpdeskId}?limit=20`,
    `${BASE}/${helpdeskId}/history?limit=20`,
    `${BASE}/not-a-uuid`,
    `${BASE}/${helpdeskId}\n`,
    `${BASE}\\${helpdeskId}`,
    `${BASE}/${helpdeskId}/start_work`,
    `${BASE}/${helpdeskId}/actions`,
    `${BASE}/${helpdeskId}/delete`,
    "/bff/session",
    "/bff/logout",
    "/oauth2/authorization/keycloak",
  ])(
    "rejects unapproved path/query before any API network: %s",
    async (path) => {
      const f = await fixture();
      await expect(f.transport({ ...get, path })).rejects.toMatchObject({
        code: "INVALID_INPUT",
      });
      expect(f.fetchBff).not.toHaveBeenCalled();
    },
  );

  it.each([
    { ...post, path: `${BASE}/${helpdeskId}` },
    { ...post, path: `${BASE}/${helpdeskId}/history` },
    { ...post, path: `${BASE}?limit=20` },
    { ...get, path: `${BASE}/${helpdeskId}/triage` },
    { ...get, method: "PUT" },
    { ...get, method: "DELETE" },
    { ...get, body: {} },
    { ...post, body: undefined },
    { ...post, body: null },
    { ...post, body: [] },
  ])("rejects wrong method/body/route pairing %j", async (request) => {
    const f = await fixture();
    await expect(
      f.transport(request as HelpdeskHttpRequest),
    ).rejects.toMatchObject({ code: "INVALID_INPUT" });
    expect(f.fetchBff).not.toHaveBeenCalled();
  });

  it("cannot construct without verified CSRF; retiring a session prevents GET and POST network", async () => {
    const sessionFetch = vi
      .fn<BffFetch>()
      .mockResolvedValue(json({ authenticated: false }));
    const session = new BffSessionClient(sessionFetch);
    const fetchBff = vi.fn<BffFetch>();
    expect(() => createHelpdeskBffTransport(session, fetchBff)).toThrow(
      "SESSION_REQUIRED",
    );
    expect(sessionFetch).not.toHaveBeenCalled();
    await session.load();
    expect(() => createHelpdeskBffTransport(session, fetchBff)).toThrow(
      "SESSION_REQUIRED",
    );
    const f = await fixture();
    f.session.invalidate();
    for (const request of [get, post])
      await expect(f.transport(request)).rejects.toMatchObject({
        code: "STALE_SESSION",
      });
    expect(fetchBff).not.toHaveBeenCalled();
    expect(f.fetchBff).not.toHaveBeenCalled();
  });

  it("never forwards caller identity, OAuth, Cookie or CSRF headers", async () => {
    const f = await fixture();
    f.fetchBff.mockResolvedValue(json(helpdeskWire(), 201));
    await f.transport({
      ...post,
      headers: {
        Accept: "application/json",
        Authorization: "Bearer forged",
        Cookie: "forged",
        "X-User": "admin",
        "X-Role": "admin",
        "X-CSRF-TOKEN": "forged",
      },
    } as HelpdeskHttpRequest);
    expect(f.fetchBff.mock.calls[0][1].headers).toEqual({
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-CSRF-TOKEN": "csrf-test-a",
    });
  });

  it.each(
    [401, 403, 404, 409].flatMap((status) =>
      [
        "",
        "<html>private</html>",
        "{broken",
        '{"code":"COMMIT_OUTCOME_UNKNOWN"}',
      ].flatMap((body) =>
        [false, true].map((write) => ({ status, body, write })),
      ),
    ),
  )(
    "preserves HTTP $status with body $body (write=$write), without replay",
    async ({ status, body, write }) => {
      const f = await fixture();
      f.fetchBff.mockResolvedValue(new Response(body, { status }));
      await expect(
        write
          ? f.client.create({ title: "Test", description: "Test" })
          : f.client.list(),
      ).rejects.toMatchObject({
        code: (
          {
            401: "UNAUTHENTICATED",
            403: "FORBIDDEN",
            404: "NOT_FOUND",
            409: "CONFLICT",
          } as Record<number, string>
        )[status],
      });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
      expect(f.invalid).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
      expect(f.session.getState().authenticated).toBe(status !== 401);
    },
  );

  it.each([
    200, 201, 302, 400, 401, 403, 404, 409, 413, 415, 422, 429, 500, 503,
  ])(
    "does not reinterpret raw HTTP %s on body-read failure",
    async (status) => {
      const f = await fixture();
      const reply = new Response("unused", { status });
      vi.spyOn(reply, "text").mockRejectedValue(new TypeError("stream lost"));
      f.fetchBff.mockResolvedValue(reply);
      await expect(f.transport(get)).resolves.toEqual({
        status,
        body: undefined,
      });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
    },
  );

  it.each(["", "<html>not json</html>", "{broken"])(
    "retains success status but does not invent JSON for %j",
    async (body) => {
      const f = await fixture();
      f.fetchBff.mockResolvedValue(new Response(body, { status: 201 }));
      await expect(f.transport(post)).resolves.toEqual({
        status: 201,
        body: undefined,
      });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
    },
  );

  it.each([
    "network",
    "redirect",
    "abort",
    "empty success",
    "malformed success",
    "backend unknown",
    "bff unknown",
  ])(
    "keeps an attempted write uncertain after %s and never retries/reconciles",
    async (mode) => {
      const f = await fixture();
      if (mode === "network" || mode === "redirect")
        f.fetchBff.mockRejectedValue(new TypeError(mode));
      else if (mode === "abort")
        f.fetchBff.mockRejectedValue(new DOMException("Aborted", "AbortError"));
      else
        f.fetchBff.mockResolvedValue(
          mode.endsWith("unknown")
            ? json(
                { code: "COMMIT_OUTCOME_UNKNOWN" },
                mode === "backend unknown" ? 500 : 503,
              )
            : new Response(mode === "empty success" ? "" : "{broken", {
                status: 201,
              }),
        );
      await expect(
        f.client.create({ title: "Test", description: "Test" }),
      ).rejects.toMatchObject({ code: "COMMIT_OUTCOME_UNKNOWN" });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
    },
  );

  it("propagates read network failure without retry or session bootstrap", async () => {
    const f = await fixture();
    f.fetchBff.mockRejectedValue(new TypeError("offline"));
    await expect(f.client.list()).rejects.toMatchObject({
      code: "NETWORK_FAILURE",
    });
    expect(f.fetchBff).toHaveBeenCalledTimes(1);
    expect(f.sessionFetch).toHaveBeenCalledTimes(1);
  });

  it.each([400, 413, 415, 422])(
    "keeps the existing client rejection contract for write HTTP %s",
    async (status) => {
      const f = await fixture();
      f.fetchBff.mockResolvedValue(json({ code: "VALIDATION_FAILED" }, status));
      await expect(
        f.client.create({ title: "Test", description: "Test" }),
      ).rejects.toMatchObject({ code: "HTTP_FAILURE", status });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
    },
  );

  it("discards a write reply consumed after session replacement without replaying it", async () => {
    const f = await fixture();
    let finish!: (body: string) => void;
    const body = new Promise<string>((resolve) => {
      finish = resolve;
    });
    const reply = json(helpdeskWire(), 201);
    const text = vi.spyOn(reply, "text").mockReturnValue(body);
    f.fetchBff.mockResolvedValue(reply);
    const pending = f.client.create({ title: "Test", description: "Test" });
    await vi.waitFor(() => expect(text).toHaveBeenCalledTimes(1));
    f.sessionFetch.mockResolvedValue(
      json({ authenticated: true, csrfToken: "csrf-test-new" }),
    );
    await f.session.load();
    finish(JSON.stringify(helpdeskWire()));
    await expect(pending).rejects.toMatchObject({
      code: "COMMIT_OUTCOME_UNKNOWN",
    });
    expect(f.session.capture().getCsrfToken()).toBe("csrf-test-new");
    expect(f.fetchBff).toHaveBeenCalledTimes(1);
    expect(f.invalid).not.toHaveBeenCalled();
  });

  it("a current 401 retires this transport and client until explicit session reentry", async () => {
    const f = await fixture();
    f.fetchBff.mockResolvedValue(new Response("", { status: 401 }));
    await expect(f.client.list()).rejects.toMatchObject({
      code: "UNAUTHENTICATED",
    });
    await expect(f.client.detail(helpdeskId)).rejects.toMatchObject({
      code: "UNAUTHENTICATED",
    });
    expect(() => createHelpdeskBffTransport(f.session, f.fetchBff)).toThrow(
      "SESSION_REQUIRED",
    );
    await f.session.load();
    const next = new HelpdeskApiClient(
      createHelpdeskBffTransport(f.session, f.fetchBff),
      vi.fn(),
    );
    f.fetchBff.mockResolvedValue(json({ items: [], nextCursor: null }));
    await expect(next.list()).resolves.toMatchObject({ items: [] });
    expect(f.fetchBff).toHaveBeenCalledTimes(2);
    expect(f.invalid).toHaveBeenCalledTimes(1);
  });

  it.each([200, 401, 403])(
    "an old HTTP %s reply cannot expose data or invalidate the successor",
    async (status) => {
      const f = await fixture();
      let finish!: (response: Response) => void;
      f.fetchBff.mockReturnValue(
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
      );
      const pending = f.transport(get);
      f.sessionFetch.mockResolvedValue(
        json({ authenticated: true, csrfToken: "csrf-test-new" }),
      );
      await f.session.load();
      finish(json({ private: "old session" }, status));
      await expect(pending).rejects.toMatchObject({ code: "STALE_SESSION" });
      expect(f.session.capture().getCsrfToken()).toBe("csrf-test-new");
      expect(f.invalid).not.toHaveBeenCalled();
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
    },
  );

  it("leaves default MOCK and the legacy LIVE_BLOCKED boundary intact", async () => {
    const { requestService } = await import("../../portalService");
    const { requestService: mock } = await import("../../MockRequestService");
    const { HttpApiClient } = await import("../../HttpApiClient");
    expect(requestService).toBe(mock);
    await expect(
      new HttpApiClient().request("POST", BASE, {}),
    ).rejects.toMatchObject({ code: "LIVE_BLOCKED" });
  });
});
