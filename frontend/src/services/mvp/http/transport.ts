// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

/** The concrete transport handles JSON and the BFF session boundary. */
export interface HelpdeskHttpRequest {
  readonly method: "GET" | "POST";
  /** Relative path only, scoped to the reviewed Helpdesk namespace. */
  readonly path: string;
  readonly headers: Readonly<{
    Accept: "application/json";
    "Content-Type"?: "application/json";
  }>;
  readonly body?: Readonly<Record<string, unknown>>;
}

export interface HelpdeskHttpResponse {
  readonly status: number;
  readonly body?: unknown;
}

/**
 * Injected boundary; the client selects no host, credentials or integration mode.
 * Resolve ALL HTTP responses, including non-2xx; reject only if no usable response
 * was obtained. One invocation must mean one attempt, with no automatic retry,
 * redirect replay, token-refresh replay or write reconciliation.
 */
export type HelpdeskHttpTransport = (
  request: HelpdeskHttpRequest,
) => Promise<HelpdeskHttpResponse>;
