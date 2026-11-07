// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { helpdeskId, helpdeskWire } from "../../../test/helpdeskFixtures";
import { executeWriteOnce, MvpSessionError } from "../errors";
import { HelpdeskApiClient } from "../http/HelpdeskApiClient";
import type { HelpdeskHttpTransport } from "../http/transport";
import { BffSessionClient, BffSessionError } from "./BffSessionClient";
import { createHelpdeskBffTransport } from "./createHelpdeskBffTransport";
import type { BffFetch } from "./http";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status });
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

async function fixture() {
  const sessionFetch = vi
    .fn<BffFetch>()
    .mockImplementation(async () =>
      json({ authenticated: true, csrfToken: "csrf-old" }),
    );
  const session = new BffSessionClient(sessionFetch);
  await session.load();
  const fetchBff = vi.fn<BffFetch>();
  const transport = createHelpdeskBffTransport(session, fetchBff);
  // A stale callback would actually retire the successor, making isolation
  // failures observable rather than merely checking a spy's call count.
  const invalid = vi.fn(() => session.invalidate());
  return {
    session,
    sessionFetch,
    fetchBff,
    transport,
    invalid,
    client: new HelpdeskApiClient(transport, invalid),
  };
}

const version = { expectedVersion: 17 };
const routes = [
  {
    name: "list",
    write: false,
    status: 200,
    invoke: (c: HelpdeskApiClient) => c.list(),
  },
  {
    name: "detail",
    write: false,
    status: 200,
    invoke: (c: HelpdeskApiClient) => c.detail(helpdeskId),
  },
  {
    name: "history",
    write: false,
    status: 200,
    invoke: (c: HelpdeskApiClient) => c.history(helpdeskId),
  },
  {
    name: "create",
    write: true,
    status: 201,
    invoke: (c: HelpdeskApiClient) =>
      c.create({ title: "Test", description: "Test" }),
  },
  {
    name: "triage",
    write: true,
    status: 200,
    invoke: (c: HelpdeskApiClient) => c.triage(helpdeskId, version),
  },
  {
    name: "assign",
    write: true,
    status: 200,
    invoke: (c: HelpdeskApiClient) =>
      c.assign(helpdeskId, {
        assigneeId: "worker",
        expectedAssignmentVersion: 0,
      }),
  },
  {
    name: "start",
    write: true,
    status: 200,
    invoke: (c: HelpdeskApiClient) => c.start(helpdeskId, version),
  },
  {
    name: "resolve",
    write: true,
    status: 200,
    invoke: (c: HelpdeskApiClient) =>
      c.resolve(helpdeskId, { ...version, resolution: "Fixed" }),
  },
  {
    name: "confirm",
    write: true,
    status: 200,
    invoke: (c: HelpdeskApiClient) => c.confirm(helpdeskId, version),
  },
] as const;

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn());
  vi.spyOn(XMLHttpRequest.prototype, "send");
  vi.spyOn(Storage.prototype, "setItem");
  vi.spyOn(Storage.prototype, "getItem");
  vi.spyOn(Storage.prototype, "removeItem");
});

afterEach(() => {
  try {
    expect(fetch).not.toHaveBeenCalled();
    expect(XMLHttpRequest.prototype.send).not.toHaveBeenCalled();
    expect(Storage.prototype.setItem).not.toHaveBeenCalled();
    expect(Storage.prototype.getItem).not.toHaveBeenCalled();
    expect(Storage.prototype.removeItem).not.toHaveBeenCalled();
  } finally {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  }
});

describe("A05-R06 session lifecycle through transport → HelpdeskApiClient", () => {
  it.each(routes)(
    "$name: SESSION_REQUIRED is local preflight, never network/unknown write",
    async (route) => {
      const sessionFetch = vi.fn<BffFetch>();
      const session = new BffSessionClient(sessionFetch);
      const fetchBff = vi.fn<BffFetch>();
      const invalid = vi.fn();
      // The production factory rejects synchronously without a verified session.
      // Lazy injection exercises that same rejection inside send()'s boundary;
      // it does not load/refresh a session or ship a lazy production adapter.
      const client = new HelpdeskApiClient(
        async (request) =>
          createHelpdeskBffTransport(session, fetchBff)(request),
        invalid,
      );
      const failure = await route
        .invoke(client)
        .catch((error: unknown) => error);
      expect(failure).toBeInstanceOf(MvpSessionError);
      expect(failure).toMatchObject({
        code: "SESSION_REQUIRED",
        phase: "before-fetch",
      });
      expect(fetchBff).not.toHaveBeenCalled();
      expect(sessionFetch).not.toHaveBeenCalled();
      expect(invalid).not.toHaveBeenCalled();
    },
  );

  it.each(
    routes.flatMap((route) =>
      ["invalidate", "replace", "anonymous"].map((mode) => ({ route, mode })),
    ),
  )(
    "$route.name: retired scope ($mode) rejects before fetch without expiring the new session",
    async ({ route, mode }) => {
      const f = await fixture();
      if (mode === "invalidate") f.session.invalidate();
      else {
        f.sessionFetch.mockResolvedValue(
          json(
            mode === "anonymous"
              ? { authenticated: false }
              : { authenticated: true, csrfToken: "csrf-new" },
          ),
        );
        await f.session.load();
      }
      const failure = await route
        .invoke(f.client)
        .catch((error: unknown) => error);
      expect(failure).toBeInstanceOf(MvpSessionError);
      expect(failure).toMatchObject({
        code: "STALE_SESSION",
        phase: "before-fetch",
      });
      expect(f.fetchBff).not.toHaveBeenCalled();
      expect(f.sessionFetch).toHaveBeenCalledTimes(
        mode === "invalidate" ? 1 : 2,
      );
      expect(f.invalid).not.toHaveBeenCalled();
      expect(f.session.getState().authenticated).toBe(mode === "replace");
    },
  );

  it("checks CSRF ownership before invoking POST fetch", async () => {
    const f = await fixture();
    const scope = f.session.capture();
    vi.spyOn(f.session, "capture").mockReturnValue({
      ...scope,
      getCsrfToken: () => {
        f.session.invalidate();
        return scope.getCsrfToken();
      },
    });
    const client = new HelpdeskApiClient(
      createHelpdeskBffTransport(f.session, f.fetchBff),
      f.invalid,
    );
    await expect(
      client.create({ title: "Test", description: "Test" }),
    ).rejects.toMatchObject({ code: "STALE_SESSION", phase: "before-fetch" });
    expect(f.fetchBff).not.toHaveBeenCalled();
    expect(f.invalid).not.toHaveBeenCalled();
  });

  it.each(
    routes.flatMap((route) =>
      ["success", "401", "403", "503", "network"].map((mode) => ({
        route,
        mode,
      })),
    ),
  )(
    "$route.name: old $mode after fetch cannot return data or invalidate a replacement",
    async ({ route, mode }) => {
      const f = await fixture();
      const pendingFetch = deferred<Response>();
      f.fetchBff.mockReturnValueOnce(pendingFetch.promise);
      const pending = route.invoke(f.client).catch((error: unknown) => error);
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      f.sessionFetch.mockResolvedValue(
        json({ authenticated: true, csrfToken: "csrf-new" }),
      );
      await f.session.load();
      const nextInvalid = vi.fn();
      const next = new HelpdeskApiClient(
        createHelpdeskBffTransport(f.session, f.fetchBff),
        nextInvalid,
      );
      if (mode === "network") pendingFetch.reject(new TypeError("lost ACK"));
      else
        pendingFetch.resolve(
          json(
            { private: "old session data" },
            mode === "success" ? route.status : Number(mode),
          ),
        );
      const failure = await pending;
      expect(failure).toMatchObject(
        route.write
          ? {
              code: "COMMIT_OUTCOME_UNKNOWN",
              cause: { code: "STALE_SESSION", phase: "after-fetch" },
            }
          : { code: "STALE_SESSION", phase: "after-fetch" },
      );
      expect((failure as Error).message).not.toContain("old session data");
      expect(f.session.capture().getCsrfToken()).toBe("csrf-new");
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      expect(f.invalid).not.toHaveBeenCalled();
      // Explicit use of a newly constructed client, not replay/reconciliation.
      f.fetchBff.mockResolvedValueOnce(json(helpdeskWire(), 201));
      await expect(
        next.create({ title: "New", description: "New" }),
      ).resolves.toMatchObject({ id: helpdeskId });
      expect(f.fetchBff).toHaveBeenCalledTimes(2);
      expect(f.fetchBff.mock.calls[1][1].headers).toMatchObject({
        "X-CSRF-TOKEN": "csrf-new",
      });
      expect(f.sessionFetch).toHaveBeenCalledTimes(2);
      expect(nextInvalid).not.toHaveBeenCalled();
    },
  );

  it.each(
    [false, true].flatMap((write) =>
      [200, 401, 403, 503].map((status) => ({ write, status })),
    ),
  )(
    "body pending: HTTP $status (write=$write) is stale after replacement",
    async ({ write, status }) => {
      const f = await fixture();
      const body = deferred<string>();
      const started = deferred<void>();
      const response = json({}, status);
      vi.spyOn(response, "text").mockImplementation(() => {
        started.resolve();
        return body.promise;
      });
      f.fetchBff.mockResolvedValue(response);
      const pending = (
        write
          ? f.client.create({ title: "Test", description: "Test" })
          : f.client.list()
      ).catch((error: unknown) => error);
      await started.promise;
      f.sessionFetch.mockResolvedValue(
        json({ authenticated: true, csrfToken: "csrf-new" }),
      );
      await f.session.load();
      body.reject(new TypeError("stream lost"));
      expect(await pending).toMatchObject(
        write
          ? {
              code: "COMMIT_OUTCOME_UNKNOWN",
              cause: { code: "STALE_SESSION", phase: "after-fetch" },
            }
          : { code: "STALE_SESSION", phase: "after-fetch" },
      );
      expect(f.invalid).not.toHaveBeenCalled();
      expect(f.session.capture().getCsrfToken()).toBe("csrf-new");
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
    },
  );

  it.each(
    [false, true].flatMap((write) =>
      [200, 401, 403, 503].map((status) => ({ write, status })),
    ),
  )(
    "consumption guard: HTTP $status (write=$write) cannot cross an await into the successor",
    async ({ write, status }) => {
      const f = await fixture();
      f.fetchBff.mockResolvedValue(json({ private: "old" }, status));
      const transport: HelpdeskHttpTransport = async (request) => {
        const response = await f.transport(request);
        // An explicit replacement happens after transport validation, before
        // send() resumes. In particular, old 401 callbacks must not reach it.
        f.sessionFetch.mockResolvedValue(
          json({ authenticated: true, csrfToken: "csrf-new" }),
        );
        await f.session.load();
        return response;
      };
      const client = new HelpdeskApiClient(transport, f.invalid);
      const pending = write
        ? client.create({ title: "Test", description: "Test" })
        : client.list();
      await expect(pending).rejects.toMatchObject(
        write
          ? {
              code: "COMMIT_OUTCOME_UNKNOWN",
              cause: { code: "STALE_SESSION", phase: "after-fetch" },
            }
          : { code: "STALE_SESSION", phase: "after-fetch" },
      );
      expect(f.session.capture().getCsrfToken()).toBe("csrf-new");
      expect(f.invalid).not.toHaveBeenCalled();
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
    },
  );

  it.each(
    [
      new BffSessionError("SESSION_REQUIRED"),
      new BffSessionError("STALE_SESSION"),
      new MvpSessionError("SESSION_REQUIRED", "before-fetch"),
      { code: "STALE_SESSION", phase: "before-fetch" },
    ].flatMap((cause) => [false, true].map((sync) => ({ cause, sync }))),
  )(
    "does not trust fetch failure $cause (synchronous=$sync) as proof of a blocked write",
    async ({ cause, sync }) => {
      const f = await fixture();
      if (sync)
        f.fetchBff.mockImplementation(() => {
          throw cause;
        });
      else f.fetchBff.mockRejectedValue(cause);
      await expect(
        f.client.create({ title: "Test", description: "Test" }),
      ).rejects.toMatchObject({ code: "COMMIT_OUTCOME_UNKNOWN" });
      await expect(f.client.list()).rejects.toMatchObject({
        code: "NETWORK_FAILURE",
      });
      expect(f.fetchBff).toHaveBeenCalledTimes(2);
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
      expect(f.invalid).not.toHaveBeenCalled();
    },
  );

  it("a CSRF 403 stays FORBIDDEN and keeps the current session without refresh or replay", async () => {
    const f = await fixture();
    f.fetchBff.mockResolvedValueOnce(json({ code: "CSRF_REJECTED" }, 403));
    await expect(
      f.client.create({ title: "Test", description: "Test" }),
    ).rejects.toMatchObject({ code: "FORBIDDEN" });
    expect(f.fetchBff).toHaveBeenCalledTimes(1);
    expect(f.session.capture().getCsrfToken()).toBe("csrf-old");
    expect(f.invalid).not.toHaveBeenCalled();
    f.fetchBff.mockResolvedValueOnce(json(helpdeskWire(), 201));
    await f.client.create({
      title: "Explicit new action",
      description: "Test",
    });
    expect(f.fetchBff).toHaveBeenCalledTimes(2);
    expect(f.fetchBff.mock.calls[1][1].headers).toMatchObject({
      "X-CSRF-TOKEN": "csrf-old",
    });
    expect(f.sessionFetch).toHaveBeenCalledTimes(1);
  });

  it.each(["SESSION_REQUIRED", "STALE_SESSION"] as const)(
    "executeWriteOnce preserves %s preflight evidence but keeps attempted writes uncertain",
    async (code) => {
      const preflight = new MvpSessionError(code, "before-fetch");
      await expect(
        executeWriteOnce(async () => {
          throw preflight;
        }),
      ).rejects.toBe(preflight);
      const attempted = new MvpSessionError(code, "after-fetch");
      await expect(
        executeWriteOnce(async () => {
          throw attempted;
        }),
      ).rejects.toMatchObject({
        code: "COMMIT_OUTCOME_UNKNOWN",
        cause: attempted,
      });
    },
  );
});
