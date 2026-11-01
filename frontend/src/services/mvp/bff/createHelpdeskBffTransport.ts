import { MvpSessionError } from "../errors";
import { HelpdeskClientError } from "../http/errors";
import { predispatchFailure } from "../http/predispatch";
import type {
  HelpdeskHttpRequest,
  HelpdeskHttpTransport,
} from "../http/transport";
import { BffSessionError, type BffSessionClient } from "./BffSessionClient";
import { bffRequestOptions, readBffJson, type BffFetch } from "./http";

const BASE = "/api/v1/mvp/requests";
const UUID =
  "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const ID = new RegExp(`^${UUID}$`);
const READ = new RegExp(`^${BASE}/${UUID}(?:/history)?$`);
const WRITE = new RegExp(
  `^${BASE}/${UUID}/(?:triage|assign|start|resolve|confirm)$`,
);

function invalid(): never {
  throw new HelpdeskClientError("INVALID_INPUT");
}

function checkSession<T>(phase: MvpSessionError["phase"], check: () => T): T {
  try {
    return check();
  } catch (cause) {
    if (
      cause instanceof BffSessionError &&
      (cause.code === "SESSION_REQUIRED" || cause.code === "STALE_SESSION")
    )
      throw new MvpSessionError(cause.code, phase, { cause });
    throw cause;
  }
}

/** Validate the original string; URL normalization must not admit extra routes. */
function bffPath({ method, path }: HelpdeskHttpRequest): string {
  if (typeof path !== "string" || /[\s\\#]/.test(path)) return invalid();
  if (method === "POST") {
    if (path !== BASE && !WRITE.test(path)) return invalid();
  } else if (method === "GET") {
    if (path !== BASE && !READ.test(path)) {
      if (!path.startsWith(`${BASE}?`)) return invalid();
      const query = path.slice(BASE.length + 1);
      const params = new URLSearchParams(query);
      if (!query || params.size !== query.split("&").length) return invalid();
      const seen = new Set<string>();
      for (const [key, value] of params) {
        if (seen.has(key)) return invalid();
        seen.add(key);
        if (key === "after") {
          if (value.length !== 36 || !ID.test(value)) return invalid();
        } else if (key === "limit") {
          if (!/^(?:[1-9]\d?|100)$/.test(value) || /\s/.test(value))
            return invalid();
        } else return invalid();
      }
    }
  } else return invalid();
  return `/bff${path}`;
}

/**
 * No host/URL option, default instance or automatic session bootstrap.
 * Capture only a verified session. Never retry, refresh or reconcile a write.
 */
export function createHelpdeskBffTransport(
  session: BffSessionClient,
  fetchBff: BffFetch,
): HelpdeskHttpTransport {
  const scope = checkSession("before-fetch", () => session.capture());
  return async (request) => {
    let path: string;
    let body: string | undefined;
    let write: boolean;
    try {
      path = bffPath(request);
      write = request.method === "POST";
      if (
        write
          ? request.body === null ||
            typeof request.body !== "object" ||
            Array.isArray(request.body)
          : request.body !== undefined
      )
        return invalid();
      body = write ? JSON.stringify(request.body) : undefined;
      if (write && body === undefined) return invalid();
    } catch (cause) {
      // Keep INVALID_INPUT/native serialization errors intact. This block has
      // not invoked fetch; matching error names/codes alone are not evidence.
      throw predispatchFailure(cause);
    }
    checkSession("before-fetch", scope.assertActive);
    // Rebuild the headers. Caller-supplied identity, authority and OAuth headers
    // (including runtime properties outside the TS interface) are never copied.
    const headers: Record<string, string> = { Accept: "application/json" };
    if (write) {
      headers["Content-Type"] = "application/json";
      headers["X-CSRF-TOKEN"] = checkSession(
        "before-fetch",
        scope.getCsrfToken,
      );
    }
    let response: Response;
    try {
      response = await fetchBff(path, {
        ...bffRequestOptions(request.method),
        headers,
        ...(write ? { body } : {}),
      });
    } catch (cause) {
      // Only our own scope check is lifecycle evidence. Never trust a code/name
      // from fetch as proof that a POST was blocked before dispatch.
      checkSession("after-fetch", scope.assertActive);
      throw new Error("BFF fetch failed", { cause });
    }
    const decoded = await readBffJson(response);
    checkSession("after-fetch", scope.assertActive);
    const assertCurrent =
      response.status === 401 ? scope.invalidate() : scope.assertActive;
    return {
      status: response.status,
      body: decoded,
      assertSessionCurrent: () => checkSession("after-fetch", assertCurrent),
    };
  };
}
