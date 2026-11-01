import { HelpdeskClientError } from "../http/errors";
import type {
  HelpdeskHttpRequest,
  HelpdeskHttpTransport,
} from "../http/transport";
import type { BffSessionClient } from "./BffSessionClient";
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
  const scope = session.capture();
  return async (request) => {
    const path = bffPath(request);
    const write = request.method === "POST";
    if (
      write
        ? request.body === null ||
          typeof request.body !== "object" ||
          Array.isArray(request.body)
        : request.body !== undefined
    )
      return invalid();
    const body = write ? JSON.stringify(request.body) : undefined;
    scope.assertActive();
    // Rebuild the headers. Caller-supplied identity, authority and OAuth headers
    // (including runtime properties outside the TS interface) are never copied.
    const headers: Record<string, string> = { Accept: "application/json" };
    if (write) {
      headers["Content-Type"] = "application/json";
      headers["X-CSRF-TOKEN"] = scope.getCsrfToken();
    }
    const response = await fetchBff(path, {
      ...bffRequestOptions(request.method),
      headers,
      ...(write ? { body } : {}),
    });
    const decoded = await readBffJson(response);
    scope.assertActive();
    if (response.status === 401) scope.invalidate();
    return { status: response.status, body: decoded };
  };
}
