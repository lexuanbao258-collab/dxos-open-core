import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  helpdeskCursor,
  helpdeskId,
  helpdeskWire,
} from "../../../test/helpdeskFixtures";
import { HelpdeskRequestService } from "../HelpdeskRequestService";
import { HelpdeskApiClient } from "./HelpdeskApiClient";
import type { HelpdeskHttpTransport } from "./transport";

const base = "/api/v1/mvp/requests";
const createInput = {
  title: "Sự cố phần mềm",
  description: "Không lưu được báo cáo công việc.",
};
const versionInput = { expectedVersion: 17 };
const assignInput = { assigneeId: "worker-2", expectedAssignmentVersion: 0 };
const resolveInput = {
  ...versionInput,
  resolution: "Đã khắc phục và kiểm tra.",
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

const routes = [
  {
    name: "create",
    method: "POST",
    path: base,
    body: { ...createInput, requestType: "IT_HELPDESK" },
    status: 201,
    response: helpdeskWire(),
    invoke: (s: HelpdeskRequestService) => s.create(createInput),
  },
  {
    name: "list",
    method: "GET",
    path: base,
    status: 200,
    response: { items: [helpdeskWire()], nextCursor: helpdeskCursor },
    invoke: (s: HelpdeskRequestService) => s.list(),
  },
  {
    name: "detail",
    method: "GET",
    path: `${base}/${helpdeskId}`,
    status: 200,
    response: helpdeskWire(),
    invoke: (s: HelpdeskRequestService) => s.detail(helpdeskId),
  },
  {
    name: "triage",
    method: "POST",
    path: `${base}/${helpdeskId}/triage`,
    body: versionInput,
    status: 200,
    response: helpdeskWire(),
    invoke: (s: HelpdeskRequestService) => s.triage(helpdeskId, versionInput),
  },
  {
    name: "assign",
    method: "POST",
    path: `${base}/${helpdeskId}/assign`,
    body: assignInput,
    status: 200,
    response: helpdeskWire(),
    invoke: (s: HelpdeskRequestService) => s.assign(helpdeskId, assignInput),
  },
  {
    name: "start",
    method: "POST",
    path: `${base}/${helpdeskId}/start`,
    body: versionInput,
    status: 200,
    response: helpdeskWire(),
    invoke: (s: HelpdeskRequestService) => s.start(helpdeskId, versionInput),
  },
  {
    name: "resolve",
    method: "POST",
    path: `${base}/${helpdeskId}/resolve`,
    body: resolveInput,
    status: 200,
    response: helpdeskWire({ resolution: resolveInput.resolution }),
    invoke: (s: HelpdeskRequestService) => s.resolve(helpdeskId, resolveInput),
  },
  {
    name: "confirm",
    method: "POST",
    path: `${base}/${helpdeskId}/confirm`,
    body: versionInput,
    status: 200,
    response: helpdeskWire({ state: "CONFIRMED" }),
    invoke: (s: HelpdeskRequestService) => s.confirm(helpdeskId, versionInput),
  },
  {
    name: "history",
    method: "GET",
    path: `${base}/${helpdeskId}/history`,
    status: 200,
    response: history,
    invoke: (s: HelpdeskRequestService) => s.history(helpdeskId),
  },
] as const;

let assertNoNetworkOrStorage: () => void;
beforeEach(() => {
  const fetchSpy = vi.spyOn(globalThis, "fetch").mockImplementation(() => {
    throw new Error("Unexpected LIVE fetch");
  });
  const xhrSpy = vi
    .spyOn(XMLHttpRequest.prototype, "send")
    .mockImplementation(() => {
      throw new Error("Unexpected LIVE XHR");
    });
  const storageSpy = vi.spyOn(Storage.prototype, "setItem");
  assertNoNetworkOrStorage = () => {
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(xhrSpy).not.toHaveBeenCalled();
    expect(storageSpy).not.toHaveBeenCalled();
  };
});
afterEach(() => {
  try {
    assertNoNetworkOrStorage();
  } finally {
    vi.restoreAllMocks();
  }
});

function fixture(status = 200, body: unknown = helpdeskWire()) {
  const transport = vi
    .fn<HelpdeskHttpTransport>()
    .mockResolvedValue({ status, body });
  const onSessionInvalid = vi.fn();
  const client = new HelpdeskApiClient(transport, onSessionInvalid);
  return {
    transport,
    onSessionInvalid,
    client,
    service: new HelpdeskRequestService(client),
  };
}

describe("APP-13 nine reviewed routes through Application service", () => {
  it.each(routes)(
    "$name: exact method/path/body and one injected attempt",
    async (route) => {
      const { transport, service, onSessionInvalid } = fixture(
        route.status,
        route.response,
      );
      const result = await route.invoke(service);
      const body = "body" in route ? route.body : undefined;
      expect(transport).toHaveBeenCalledExactlyOnceWith({
        method: route.method,
        path: route.path,
        headers: {
          Accept: "application/json",
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
        ...(body ? { body } : {}),
      });
      expect(onSessionInvalid).not.toHaveBeenCalled();
      if (route.name === "history") expect(result).toEqual(history);
      else if (route.name === "list")
        expect(result).toMatchObject({
          nextCursor: helpdeskCursor,
          items: [{ version: 17, assignmentVersion: 0 }],
        });
      else
        expect(result).toMatchObject({
          id: helpdeskId,
          version: 17,
          assignmentVersion: 0,
        });
      expect(transport.mock.calls[0][0].path).not.toContain("/actions");
    },
  );

  it("allowlists create/command fields, sends IT_HELPDESK and never sends caller identity", async () => {
    const { transport, client } = fixture(201);
    const input = {
      ...createInput,
      requestType: "FACILITY",
      requesterId: "forged",
      role: "admin",
      priority: "urgent",
    };
    await client.create(input);
    expect(transport.mock.calls[0][0].body).toEqual({
      ...createInput,
      requestType: "IT_HELPDESK",
    });
    transport.mockResolvedValue({ status: 200, body: helpdeskWire() });
    await client.assign(helpdeskId, {
      ...assignInput,
      expectedVersion: 999,
      actor: "forged",
    } as typeof assignInput);
    expect(transport.mock.calls[1][0].body).toEqual(assignInput);
    await client.resolve(helpdeskId, {
      ...resolveInput,
      note: "not-a-field",
    } as typeof resolveInput);
    expect(transport.mock.calls[2][0].body).toEqual(resolveInput);
  });

  it.each([1, 100])(
    "passes explicit after/limit=%s and preserves pagination",
    async (limit) => {
      const { transport, service } = fixture(200, {
        items: [helpdeskWire()],
        nextCursor: helpdeskCursor,
      });
      const page = await service.list({ after: helpdeskId, limit });
      expect(transport.mock.calls[0][0].path).toBe(
        `${base}?after=${helpdeskId}&limit=${limit}`,
      );
      expect(page.nextCursor).toBe(helpdeskCursor);
      transport.mockResolvedValue({
        status: 200,
        body: { items: [], nextCursor: null },
      });
      expect(await service.list({ after: page.nextCursor! })).toEqual({
        items: [],
        nextCursor: null,
      });
      expect(transport.mock.calls[1][0].path).toBe(
        `${base}?after=${helpdeskCursor}`,
      );
    },
  );

  it.each([0, 101, -1, 1.5, NaN, Infinity])(
    "rejects invalid limit %s before transport",
    (limit) => {
      const { transport, client } = fixture();
      expect(() => client.list({ limit })).toThrow(/không hợp lệ/);
      expect(transport).not.toHaveBeenCalled();
    },
  );

  it("rejects invalid IDs, empty text/resolution and unsafe versions before transport", () => {
    const { transport, client } = fixture();
    const calls = [
      () => client.detail("../actions"),
      () => client.list({ after: "invalid-cursor" }),
      () => client.create({ ...createInput, title: " " }),
      () => client.create({ ...createInput, description: "a".repeat(4001) }),
      () => client.resolve(helpdeskId, { ...resolveInput, resolution: " " }),
      () => client.assign(helpdeskId, { ...assignInput, assigneeId: " " }),
      ...[-1, 0.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1].flatMap(
        (version) => [
          () => client.triage(helpdeskId, { expectedVersion: version }),
          () => client.start(helpdeskId, { expectedVersion: version }),
          () => client.confirm(helpdeskId, { expectedVersion: version }),
          () =>
            client.resolve(helpdeskId, {
              ...resolveInput,
              expectedVersion: version,
            }),
          () =>
            client.assign(helpdeskId, {
              ...assignInput,
              expectedAssignmentVersion: version,
            }),
        ],
      ),
    ];
    for (const call of calls) expect(call).toThrow(/không hợp lệ/);
    expect(transport).not.toHaveBeenCalled();
  });
});

describe("APP-13 security/session/error boundaries", () => {
  it.each(
    routes.flatMap((route) =>
      (
        [
          [401, "UNAUTHENTICATED"],
          [403, "FORBIDDEN"],
          [404, "NOT_FOUND"],
          [409, "CONFLICT"],
        ] as const
      ).map(([status, code]) => ({ route, status, code })),
    ),
  )(
    "$route.name: HTTP $status takes precedence over an inconsistent error body, without retry",
    async ({ route, status, code }) => {
      const { service, transport, onSessionInvalid } = fixture(status, {
        code: "COMMIT_OUTCOME_UNKNOWN",
        message: "private identity/resource diagnostic",
      });
      const failure = await route
        .invoke(service)
        .catch((error: unknown) => error);
      expect(failure).toMatchObject({ code });
      expect((failure as Error).message).not.toContain("private");
      expect(transport).toHaveBeenCalledTimes(1);
      expect(onSessionInvalid).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
    },
  );

  it.each([
    [401, "UNAUTHENTICATED"],
    [403, "FORBIDDEN"],
    [404, "NOT_FOUND"],
    [409, "CONFLICT"],
  ] as const)(
    "maps HTTP %s safely on read and write without raw diagnostics",
    async (status, code) => {
      for (const operation of ["read", "write"]) {
        const { client, transport, onSessionInvalid } = fixture(status, {
          message: "sensitive resource/identity",
          code: "diagnostic",
        });
        const failure = await (
          operation === "read"
            ? client.detail(helpdeskId)
            : client.assign(helpdeskId, assignInput)
        ).catch((error: unknown) => error);
        expect(failure).toMatchObject({ code });
        expect((failure as Error).message).not.toContain("sensitive");
        if (status === 404)
          expect((failure as Error).message).toMatch(
            /Không tìm thấy.*không khả dụng/,
          );
        if (status === 409)
          expect((failure as Error).message).toContain("Tải lại");
        expect(transport).toHaveBeenCalledTimes(1);
        expect(onSessionInvalid).toHaveBeenCalledTimes(status === 401 ? 1 : 0);
      }
    },
  );

  it.each([false, true])(
    "401 invalidates once and blocks all later protected operations even if callback throws=%s",
    async (throws) => {
      const { client, transport, service, onSessionInvalid } = fixture(401);
      if (throws)
        onSessionInvalid.mockImplementation(() => {
          throw new Error("UI callback failed");
        });
      await expect(client.detail(helpdeskId)).rejects.toMatchObject({
        code: "UNAUTHENTICATED",
      });
      transport.mockResolvedValue({ status: 200, body: helpdeskWire() });
      for (const route of routes)
        await expect(route.invoke(service)).rejects.toMatchObject({
          code: "UNAUTHENTICATED",
        });
      expect(transport).toHaveBeenCalledTimes(1);
      expect(onSessionInvalid).toHaveBeenCalledTimes(1);
    },
  );

  it.each(routes.filter((route) => route.method === "POST"))(
    "$name is never retried or reconciled automatically after network/unknown write",
    async (route) => {
      for (const mode of [
        "lost response",
        "unknown commit",
        "server failure",
        "timeout",
        "malformed success",
        "unexpected success",
      ]) {
        const { transport, service } = fixture();
        if (mode === "lost response")
          transport.mockRejectedValue(new Error("ACK lost after commit"));
        else
          transport.mockResolvedValue({
            status:
              mode === "unknown commit" || mode === "server failure"
                ? 503
                : mode === "timeout"
                  ? 408
                  : mode === "unexpected success"
                    ? 204
                    : route.status,
            body:
              mode === "unknown commit"
                ? { code: "COMMIT_OUTCOME_UNKNOWN" }
                : { malformed: true },
          });
        await expect(route.invoke(service)).rejects.toMatchObject({
          code: "COMMIT_OUTCOME_UNKNOWN",
        });
        expect(transport).toHaveBeenCalledTimes(1);
      }
    },
  );

  it("keeps network read failure distinct without retry", async () => {
    const { transport, client } = fixture();
    transport.mockRejectedValue(new Error("offline"));
    await expect(client.list()).rejects.toMatchObject({
      code: "NETWORK_FAILURE",
    });
    expect(transport).toHaveBeenCalledTimes(1);
  });

  it.each([
    { requestType: "FACILITY" },
    { version: "17" },
    { assignmentVersion: -1 },
    { version: Number.MAX_SAFE_INTEGER + 1 },
    { state: null },
    { eligibleAssignees: [{ id: "", displayName: null }] },
    { eligibleAssignees: [{ id: "worker", displayName: 123 }] },
    { requesterDisplayName: {} },
    { assigneeDisplayName: 42 },
  ])(
    "rejects malformed RequestView rather than inventing data: %j",
    async (override) => {
      const { client } = fixture(200, { ...helpdeskWire(), ...override });
      await expect(client.detail(helpdeskId)).rejects.toMatchObject({
        code: "INVALID_RESPONSE",
      });
    },
  );

  it.each([
    { items: [], nextCursor: "invalid-cursor" },
    { items: null, nextCursor: null },
    { items: [helpdeskWire({ version: -1 })], nextCursor: null },
    { items: [] },
    [],
    null,
  ])("rejects malformed list envelopes: %j", async (body) => {
    const { service, transport } = fixture(200, body);
    await expect(service.list()).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
    expect(transport).toHaveBeenCalledTimes(1);
  });

  it.each([
    { items: history },
    [{ ...history[0], occurredAt: null }],
    [{ ...history[0], version: "1" }],
    [{ ...history[0], targetState: null }],
    [{ sourceState: "SUBMITTED", targetState: "TRIAGED", version: 1 }],
    null,
  ])("rejects malformed history without inventing fields: %j", async (body) => {
    const { service, transport } = fixture(200, body);
    await expect(service.history(helpdeskId)).rejects.toMatchObject({
      code: "INVALID_RESPONSE",
    });
    expect(transport).toHaveBeenCalledTimes(1);
  });

  it.each([undefined, null, { status: 0 }, { status: 200.5 }, { status: 600 }])(
    "handles invalid transport responses: %j",
    async (response) => {
      const { transport, client } = fixture();
      transport.mockResolvedValue(
        response as Awaited<ReturnType<HelpdeskHttpTransport>>,
      );
      await expect(client.detail(helpdeskId)).rejects.toMatchObject({
        code: "INVALID_RESPONSE",
      });
      await expect(
        client.assign(helpdeskId, assignInput),
      ).rejects.toMatchObject({ code: "COMMIT_OUTCOME_UNKNOWN" });
      expect(transport).toHaveBeenCalledTimes(2);
    },
  );

  it("does not expose HTTP 400/500 diagnostics and does not retry", async () => {
    const { client, transport } = fixture(400, {
      code: "VALIDATION",
      message: "private details",
    });
    await expect(client.assign(helpdeskId, assignInput)).rejects.toMatchObject({
      code: "HTTP_FAILURE",
      status: 400,
    });
    transport.mockResolvedValue({
      status: 500,
      body: { message: "private details" },
    });
    const error = await client.list().catch((failure: unknown) => failure);
    expect(error).toMatchObject({ code: "HTTP_FAILURE", status: 500 });
    expect((error as Error).message).not.toContain("private details");
    expect(transport).toHaveBeenCalledTimes(2);
  });
});
