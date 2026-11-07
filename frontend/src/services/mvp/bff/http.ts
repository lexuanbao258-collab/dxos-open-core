// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

/** Explicit dependency only. Importing or constructing an adapter performs no I/O. */
export type BffFetch = (path: string, init: RequestInit) => Promise<Response>;

export function bffRequestOptions(method: "GET" | "POST"): RequestInit {
  return {
    method,
    mode: "same-origin",
    credentials: "same-origin",
    redirect: "error",
    cache: "no-store",
  };
}

/** A readable status remains useful even when JSON/body consumption fails. */
export async function readBffJson(response: Response): Promise<unknown> {
  try {
    return JSON.parse(await response.text()) as unknown;
  } catch {
    return undefined;
  }
}
