// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

import { afterEach, describe, expect, it, vi } from "vitest";
import { helpdeskId } from "../../../test/helpdeskFixtures";
import { executeWriteOnce, MvpApplicationError } from "../errors";
import { HelpdeskApiClient } from "../http/HelpdeskApiClient";
import { HelpdeskClientError } from "../http/errors";
import type { HelpdeskHttpRequest } from "../http/transport";
import { BffSessionClient } from "./BffSessionClient";
import { createHelpdeskBffTransport } from "./createHelpdeskBffTransport";
import type { BffFetch } from "./http";

const BASE = "/api/v1/mvp/requests";
const input = { title: "Test", description: "Test" };

async function fixture(
  rewrite: (request: HelpdeskHttpRequest) => HelpdeskHttpRequest = (r) => r,
) {
  const sessionFetch = vi
    .fn<BffFetch>()
    .mockResolvedValue(
      new Response(
        JSON.stringify({ authenticated: true, csrfToken: "csrf-current" }),
      ),
    );
  const session = new BffSessionClient(sessionFetch);
  await session.load();
  const fetchBff = vi.fn<BffFetch>();
  const transport = createHelpdeskBffTransport(session, fetchBff);
  const invalid = vi.fn(() => session.invalidate());
  // The public client allowlists its own requests. This seam introduces invalid
  // runtime requests to exercise the real BFF validation through send(), without
  // bypassing the API client or changing production validation/DTO contracts.
  const client = new HelpdeskApiClient(
    (request) => transport(rewrite(request)),
    invalid,
  );
  return { client, session, sessionFetch, fetchBff, invalid };
}

function invoke(client: HelpdeskApiClient, write: boolean) {
  return write ? client.create(input) : client.list();
}

function expectNoDispatch(f: Awaited<ReturnType<typeof fixture>>) {
  expect(f.fetchBff).not.toHaveBeenCalled();
  expect(f.sessionFetch).toHaveBeenCalledTimes(1);
  expect(f.invalid).not.toHaveBeenCalled();
  expect(f.session.capture().getCsrfToken()).toBe("csrf-current");
}

afterEach(() => vi.restoreAllMocks());

describe("A05-B04 confirmed predispatch errors through HelpdeskApiClient and BFF", () => {
  it.each(
    [false, true].flatMap((write) =>
      [
        "https://evil.example/api/v1/mvp/requests",
        `${BASE}/../requests`,
        `${BASE}/${helpdeskId}/start_work`,
        `${BASE}?role=admin`,
      ].map((path) => ({ write, path })),
    ),
  )(
    "invalid path $path (write=$write) preserves INVALID_INPUT with fetch=0",
    async ({ write, path }) => {
      const f = await fixture((request) => ({ ...request, path }));
      const failure = await invoke(f.client, write).catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(HelpdeskClientError);
      expect(failure).toMatchObject({ code: "INVALID_INPUT" });
      expect(failure).not.toHaveProperty("phase");
      expectNoDispatch(f);
    },
  );

  it.each([
    { write: false, body: {} },
    { write: false, body: null },
    { write: true, body: undefined },
    { write: true, body: null },
    { write: true, body: [] },
    { write: true, body: "invalid" },
  ])(
    "invalid body $body (write=$write) preserves INVALID_INPUT with fetch=0",
    async ({ write, body }) => {
      const f = await fixture((request) => ({
        ...request,
        body: body as HelpdeskHttpRequest["body"],
      }));
      const failure = await invoke(f.client, write).catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(HelpdeskClientError);
      expect(failure).toMatchObject({ code: "INVALID_INPUT" });
      expectNoDispatch(f);
    },
  );

  const invalidInputs = [
    { name: "GET ID", invoke: (c: HelpdeskApiClient) => c.detail("../bad") },
    {
      name: "GET cursor",
      invoke: (c: HelpdeskApiClient) => c.list({ after: "bad" }),
    },
    {
      name: "GET limit",
      invoke: (c: HelpdeskApiClient) => c.list({ limit: 0 }),
    },
    {
      name: "POST title",
      invoke: (c: HelpdeskApiClient) => c.create({ ...input, title: " " }),
    },
    {
      name: "POST version",
      invoke: (c: HelpdeskApiClient) =>
        c.start(helpdeskId, { expectedVersion: -1 }),
    },
    {
      name: "POST assignment version",
      invoke: (c: HelpdeskApiClient) =>
        c.assign(helpdeskId, {
          assigneeId: "worker",
          expectedAssignmentVersion: NaN,
        }),
    },
    {
      name: "POST resolution",
      invoke: (c: HelpdeskApiClient) =>
        c.resolve(helpdeskId, { expectedVersion: 17, resolution: " " }),
    },
  ];

  it.each(invalidInputs)(
    "$name stays the existing synchronous INVALID_INPUT with fetch=0",
    async (route) => {
      const f = await fixture();
      expect(() => route.invoke(f.client)).toThrow(HelpdeskClientError);
      expectNoDispatch(f);
    },
  );

  it.each(invalidInputs)(
    "executeWriteOnce preserves local $name validation without replay",
    async (route) => {
      const f = await fixture();
      const write = vi.fn(async () => route.invoke(f.client));
      const failure = await executeWriteOnce(write).catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(HelpdeskClientError);
      expect(failure).toMatchObject({ code: "INVALID_INPUT" });
      expect(write).toHaveBeenCalledTimes(1);
      expectNoDispatch(f);
    },
  );

  it("executeWriteOnce preserves the exact BFF INVALID_INPUT instance", async () => {
    const f = await fixture((request) => ({ ...request, path: "/bff/logout" }));
    let original: unknown;
    const write = vi.fn(async () => {
      try {
        return await f.client.create(input);
      } catch (error) {
        original = error;
        throw error;
      }
    });
    const failure = await executeWriteOnce(write).catch((e: unknown) => e);
    expect(failure).toBe(original);
    expect(failure).toMatchObject({ code: "INVALID_INPUT" });
    expect(write).toHaveBeenCalledTimes(1);
    expectNoDispatch(f);
  });

  it.each(["cycle", "bigint", "throwing toJSON", "throwing getter"])(
    "%s serialization before fetch preserves the native error through executeWriteOnce",
    async (mode) => {
      const body: Record<string, unknown> = {};
      const original = new TypeError("local serializer failure");
      if (mode === "cycle") body.self = body;
      else if (mode === "bigint") body.version = 1n;
      else if (mode === "throwing toJSON")
        body.toJSON = () => {
          throw original;
        };
      else
        Object.defineProperty(body, "title", {
          enumerable: true,
          get: () => {
            throw original;
          },
        });
      const f = await fixture((request) => ({ ...request, body }));
      const write = vi.fn(() => f.client.create(input));
      const failure = await executeWriteOnce(write).catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(TypeError);
      expect(failure).not.toBeInstanceOf(MvpApplicationError);
      expect(failure).not.toHaveProperty("code");
      if (mode.startsWith("throwing")) expect(failure).toBe(original);
      expect(write).toHaveBeenCalledTimes(1);
      expectNoDispatch(f);
    },
  );

  it.each([undefined, Symbol("body"), () => {}])(
    "a body whose toJSON returns %s is INVALID_INPUT, never a bodyless POST",
    async (value) => {
      const f = await fixture((request) => ({
        ...request,
        body: { toJSON: () => value },
      }));
      await expect(
        executeWriteOnce(() => f.client.create(input)),
      ).rejects.toMatchObject({ code: "INVALID_INPUT" });
      expectNoDispatch(f);
    },
  );

  it("normalizes a primitive serialization exception without claiming a POST attempt", async () => {
    const f = await fixture((request) => ({
      ...request,
      body: {
        toJSON: () => {
          throw "local failure";
        },
      },
    }));
    await expect(
      executeWriteOnce(() => f.client.create(input)),
    ).rejects.toMatchObject({ name: "TypeError", cause: "local failure" });
    expectNoDispatch(f);
  });

  it.each(
    [false, true].flatMap((write) =>
      [false, true].flatMap((sync) =>
        [
          "INVALID_INPUT",
          "TypeError",
          "forged phase",
          "previous predispatch error",
        ].map((kind) => ({ write, sync, kind })),
      ),
    ),
  )(
    "fetch invocation: $kind (write=$write, sync=$sync) has fetch=1 and no replay",
    async ({ write, sync, kind }) => {
      const f = await fixture();
      let cause: unknown = new HelpdeskClientError("INVALID_INPUT");
      if (kind === "TypeError") cause = new TypeError("fetch failed");
      else if (kind === "forged phase")
        cause = { code: "INVALID_INPUT", phase: "before-fetch" };
      else if (kind === "previous predispatch error") {
        // Reuse an actually marked validation error: the fetch boundary must
        // strip even authentic earlier evidence belonging to another operation.
        try {
          f.client.create({ ...input, title: " " });
        } catch (error) {
          cause = error;
        }
      }
      if (sync)
        f.fetchBff.mockImplementation(() => {
          throw cause;
        });
      else f.fetchBff.mockRejectedValue(cause);
      const failure = await invoke(f.client, write).catch((e: unknown) => e);
      expect(failure).toBeInstanceOf(MvpApplicationError);
      expect(failure).toMatchObject({
        code: write ? "COMMIT_OUTCOME_UNKNOWN" : "NETWORK_FAILURE",
      });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
      expect(f.invalid).not.toHaveBeenCalled();
      expect(f.session.getState()).toEqual({ authenticated: true });
    },
  );

  it("a committed POST with a lost response stays unknown through executeWriteOnce, fetch=1", async () => {
    const f = await fixture();
    let commits = 0;
    f.fetchBff.mockImplementation(async () => {
      commits++;
      throw new TypeError("ACK lost after commit");
    });
    const write = vi.fn(() => f.client.create(input));
    await expect(executeWriteOnce(write)).rejects.toMatchObject({
      code: "COMMIT_OUTCOME_UNKNOWN",
    });
    expect(commits).toBe(1);
    expect(write).toHaveBeenCalledTimes(1);
    expect(f.fetchBff).toHaveBeenCalledTimes(1);
    expect(f.sessionFetch).toHaveBeenCalledTimes(1);
    expect(f.invalid).not.toHaveBeenCalled();
  });

  it.each(
    [false, true].flatMap((write) =>
      [401, 403].map((status) => ({ write, status })),
    ),
  )(
    "current status-only $status (write=$write) preserves HTTP semantics with fetch=1",
    async ({ write, status }) => {
      const f = await fixture();
      f.fetchBff.mockResolvedValue(new Response(null, { status }));
      await expect(invoke(f.client, write)).rejects.toMatchObject({
        code: status === 401 ? "UNAUTHENTICATED" : "FORBIDDEN",
      });
      expect(f.fetchBff).toHaveBeenCalledTimes(1);
      expect(f.invalid).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
      expect(f.session.getState().authenticated).toBe(status !== 401);
      expect(f.sessionFetch).toHaveBeenCalledTimes(1);
    },
  );
});
