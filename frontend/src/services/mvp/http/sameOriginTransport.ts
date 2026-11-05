// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

import type { HelpdeskHttpTransport } from "./transport";

const BASE = "/api/v1/mvp/requests";
const UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
const READ = new RegExp(`^${BASE}/${UUID}(?:/history)?$`, "i");
const WRITE = new RegExp(
  `^${BASE}/${UUID}/(?:triage|assign|start|resolve|confirm)$`,
  "i",
);

function requireLocalHttps() {
  if (
    location.protocol !== "https:" ||
    !["localhost", "127.0.0.1", "[::1]"].includes(location.hostname)
  )
    throw new Error(
      "Helpdesk integration requires a trusted local HTTPS origin.",
    );
}

/** CSRF stays in memory; OAuth credentials remain at the BFF. */
export async function bootstrapHelpdeskSession(fetcher: typeof fetch = fetch) {
  requireLocalHttps();
  const response = await fetcher("/bff/session", {
    method: "GET",
    credentials: "same-origin",
    mode: "same-origin",
    redirect: "error",
    cache: "no-store",
    headers: { Accept: "application/json" },
  });
  if (response.status === 401) return null;
  if (!response.ok) throw new Error("Unable to load BFF session.");
  const body: unknown = await response.json();
  if (body && typeof body === "object" && "authenticated" in body) {
    if (body.authenticated === false) return null;
    if (
      body.authenticated === true &&
      "csrfToken" in body &&
      typeof body.csrfToken === "string" &&
      body.csrfToken.trim()
    ) {
      return { csrfToken: body.csrfToken };
    }
  }
  throw new Error("Invalid BFF session response.");
}

export function sameOriginHelpdeskTransport(
  csrfToken: string,
  fetcher: typeof fetch = fetch,
): HelpdeskHttpTransport {
  requireLocalHttps();
  if (!csrfToken.trim()) throw new Error("BFF CSRF token is required.");
  return async (request) => {
    requireLocalHttps();
    const url = new URL(request.path, location.origin);
    const list = url.pathname === BASE;
    const allowed =
      request.method === "GET"
        ? list || READ.test(url.pathname)
        : request.method === "POST" && (list || WRITE.test(url.pathname));
    if (
      !allowed ||
      url.origin !== location.origin ||
      url.pathname + url.search !== request.path ||
      url.hash ||
      (url.search && (request.method !== "GET" || !list)) ||
      [...url.searchParams.keys()].some(
        (key) =>
          !["after", "limit"].includes(key) ||
          url.searchParams.getAll(key).length !== 1,
      )
    ) {
      throw new Error("Unapproved Helpdesk route.");
    }
    const response = await fetcher(`/bff${request.path}`, {
      method: request.method,
      credentials: "same-origin",
      mode: "same-origin",
      redirect: "error",
      cache: "no-store",
      headers: {
        Accept: "application/json",
        ...(request.method === "POST"
          ? {
              "Content-Type": "application/json",
              "X-CSRF-TOKEN": csrfToken,
            }
          : {}),
      },
      ...(request.method === "POST"
        ? { body: JSON.stringify(request.body) }
        : {}),
    });
    const text = await response.text();
    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      /* Preserve HTTP errors with empty/non-JSON bodies. */
    }
    return { status: response.status, body };
  };
}
