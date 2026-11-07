// SPDX-FileCopyrightText: 2026 TRICORE contributors
//
// SPDX-License-Identifier: Apache-2.0

// Internal evidence by identity, not a public error code/phase or wire field.
// Only owned synchronous validation/serialization before fetch may mark failures.
const failures = new WeakSet<object>();

export function predispatchFailure(cause: unknown): object {
  const failure =
    cause !== null && (typeof cause === "object" || typeof cause === "function")
      ? cause
      : new TypeError("Helpdesk request preparation failed", { cause });
  failures.add(failure);
  return failure;
}

export function isPredispatchFailure(cause: unknown): boolean {
  return (
    cause !== null &&
    (typeof cause === "object" || typeof cause === "function") &&
    failures.has(cause)
  );
}
