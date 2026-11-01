import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { BffSessionClient } from "./BffSessionClient";
import type { BffFetch } from "./http";
import { BFF_BROWSER_AUTH_CONTRACT } from "./browserAuthContract";

function response(body: unknown, status = 200) {
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

describe("A05 unbound BFF session", () => {
  it("is closed without I/O until an explicit load; exposes no CSRF in state", async () => {
    const fetchBff = vi
      .fn<BffFetch>()
      .mockResolvedValue(
        response({ authenticated: true, csrfToken: "csrf-test-a" }),
      );
    const session = new BffSessionClient(fetchBff);
    expect(fetchBff).not.toHaveBeenCalled();
    expect(session.getState()).toEqual({ authenticated: false });
    expect(() => session.capture()).toThrow("SESSION_REQUIRED");
    await expect(session.load()).resolves.toEqual({ authenticated: true });
    expect(fetchBff).toHaveBeenCalledExactlyOnceWith("/bff/session", {
      method: "GET",
      mode: "same-origin",
      credentials: "same-origin",
      redirect: "error",
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
    expect(session.getState()).toEqual({ authenticated: true });
    expect(Object.isFrozen(session.getState())).toBe(true);
    expect(session.capture().getCsrfToken()).toBe("csrf-test-a");
  });

  it("accepts anonymous state and retires a previously authenticated scope", async () => {
    const fetchBff = vi
      .fn<BffFetch>()
      .mockResolvedValueOnce(
        response({ authenticated: true, csrfToken: "csrf-test-a" }),
      )
      .mockResolvedValueOnce(response({ authenticated: false }));
    const session = new BffSessionClient(fetchBff);
    await session.load();
    const old = session.capture();
    await expect(session.load()).resolves.toEqual({ authenticated: false });
    expect(() => old.getCsrfToken()).toThrow("STALE_SESSION");
    expect(() => session.capture()).toThrow("SESSION_REQUIRED");
  });

  it.each([
    null,
    [],
    {},
    true,
    { authenticated: "true" },
    { authenticated: 1 },
    { authenticated: true },
    { authenticated: true, csrfToken: null },
    { authenticated: true, csrfToken: 1 },
    { authenticated: true, csrfToken: "" },
    { authenticated: true, csrfToken: " " },
    { authenticated: true, csrfToken: "csrf-test-a\n" },
    { authenticated: true, csrfToken: "a\r\nX-Role:admin" },
    { authenticated: true, csrfToken: "csrf-test-a", role: "admin" },
    {
      authenticated: true,
      csrfToken: "csrf-test-a",
      accessToken: "unexpected",
    },
    { authenticated: false, csrfToken: "csrf-test-a" },
  ])("rejects malformed or extended session schema %j", async (body) => {
    const fetchBff = vi.fn<BffFetch>().mockResolvedValue(response(body));
    const session = new BffSessionClient(fetchBff);
    await expect(session.load()).rejects.toMatchObject({
      code: "INVALID_SESSION_RESPONSE",
    });
    expect(session.getState()).toEqual({ authenticated: false });
    expect(() => session.capture()).toThrow("SESSION_REQUIRED");
    expect(fetchBff).toHaveBeenCalledTimes(1);
  });

  it.each(["", "<html>sign in</html>", "{broken"])(
    "fails closed for non-JSON session reply %j",
    async (body) => {
      const session = new BffSessionClient(
        vi.fn<BffFetch>().mockResolvedValue(new Response(body)),
      );
      await expect(session.load()).rejects.toMatchObject({
        code: "INVALID_SESSION_RESPONSE",
      });
      expect(session.getState().authenticated).toBe(false);
    },
  );

  it.each([201, 302, 401, 403, 404, 409, 500, 503])(
    "rejects session HTTP %s even with a valid-looking body",
    async (status) => {
      const fetchBff = vi
        .fn<BffFetch>()
        .mockResolvedValue(
          response({ authenticated: true, csrfToken: "csrf-test-a" }, status),
        );
      const session = new BffSessionClient(fetchBff);
      await expect(session.load()).rejects.toMatchObject({
        code: "INVALID_SESSION_RESPONSE",
      });
      expect(session.getState().authenticated).toBe(false);
      expect(fetchBff).toHaveBeenCalledTimes(1);
    },
  );

  it("clears CSRF as soon as a probe starts, including after network failure, without retry", async () => {
    const pending = deferred<Response>();
    const fetchBff = vi
      .fn<BffFetch>()
      .mockResolvedValueOnce(
        response({ authenticated: true, csrfToken: "csrf-test-a" }),
      )
      .mockReturnValueOnce(pending.promise);
    const session = new BffSessionClient(fetchBff);
    await session.load();
    const old = session.capture();
    const load = session.load();
    expect(session.getState().authenticated).toBe(false);
    expect(() => old.getCsrfToken()).toThrow("STALE_SESSION");
    const failure = expect(load).rejects.toMatchObject({
      code: "SESSION_NETWORK_FAILURE",
    });
    pending.reject(new TypeError("offline"));
    await failure;
    expect(session.getState().authenticated).toBe(false);
    expect(fetchBff).toHaveBeenCalledTimes(2);
  });

  it.each(["authenticated", "anonymous", "malformed", "network"])(
    "ignores a late %s probe from a previous generation",
    async (mode) => {
      const pending = deferred<Response>();
      const fetchBff = vi
        .fn<BffFetch>()
        .mockReturnValueOnce(pending.promise)
        .mockResolvedValueOnce(
          response({ authenticated: true, csrfToken: "csrf-test-new" }),
        );
      const session = new BffSessionClient(fetchBff);
      const old = session.load();
      await session.load();
      const failure = expect(old).rejects.toMatchObject({
        code: "STALE_SESSION",
      });
      if (mode === "network") pending.reject(new Error("offline"));
      else
        pending.resolve(
          response(
            mode === "authenticated"
              ? { authenticated: true, csrfToken: "csrf-test-old" }
              : mode === "anonymous"
                ? { authenticated: false }
                : {},
          ),
        );
      await failure;
      expect(session.capture().getCsrfToken()).toBe("csrf-test-new");
      expect(fetchBff).toHaveBeenCalledTimes(2);
    },
  );

  it("does not revive a session invalidated while a body is being consumed", async () => {
    const pending = deferred<string>();
    const reply = response({});
    const text = vi.spyOn(reply, "text").mockReturnValue(pending.promise);
    const session = new BffSessionClient(
      vi.fn<BffFetch>().mockResolvedValue(reply),
    );
    const load = session.load();
    await vi.waitFor(() => expect(text).toHaveBeenCalledTimes(1));
    session.invalidate();
    pending.resolve(
      JSON.stringify({ authenticated: true, csrfToken: "csrf-test-old" }),
    );
    await expect(load).rejects.toMatchObject({ code: "STALE_SESSION" });
    expect(session.getState().authenticated).toBe(false);
  });

  it("an old scope cannot invalidate or read the successor's CSRF", async () => {
    const fetchBff = vi
      .fn<BffFetch>()
      .mockResolvedValueOnce(
        response({ authenticated: true, csrfToken: "csrf-test-old" }),
      )
      .mockResolvedValueOnce(
        response({ authenticated: true, csrfToken: "csrf-test-new" }),
      );
    const session = new BffSessionClient(fetchBff);
    await session.load();
    const old = session.capture();
    await session.load();
    old.invalidate();
    expect(() => old.getCsrfToken()).toThrow("STALE_SESSION");
    expect(session.capture().getCsrfToken()).toBe("csrf-test-new");
  });

  it("ships login/logout contract data only, with the three Yến gates pending", async () => {
    await import("./browserAuthContract");
    expect(BFF_BROWSER_AUTH_CONTRACT).toEqual({
      login: {
        method: "GET",
        path: "/oauth2/authorization/keycloak",
        navigation: "top-level",
      },
      logout: {
        method: "POST",
        path: "/bff/logout",
        csrfHeader: "X-CSRF-TOKEN",
        browserOidcRedirect: "PENDING YEN CONFIRMATION",
      },
      csrf403: "PENDING YEN CONFIRMATION",
      sessionExpiry: "PENDING YEN CONFIRMATION",
    });
    expect(
      Object.values(BFF_BROWSER_AUTH_CONTRACT).some(
        (value) => typeof value === "function",
      ),
    ).toBe(false);
  });
});
