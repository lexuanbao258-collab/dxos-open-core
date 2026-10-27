import { HelpdeskClientError } from "./errors";
import type {
  HelpdeskHistoryEntry,
  HelpdeskPage,
  HelpdeskRequest,
} from "./types";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function validateId(id: string): string {
  if (typeof id !== "string" || !UUID.test(id)) {
    throw new HelpdeskClientError("INVALID_INPUT");
  }
  return id;
}

/** Java Long accepts a wider range; reject unsafe JS numbers, never round/coerce. */
export function validateVersion(version: number): number {
  if (!Number.isSafeInteger(version) || version < 0) {
    throw new HelpdeskClientError("INVALID_INPUT");
  }
  return version;
}

export function validateText(text: string, max: number): string {
  if (
    typeof text !== "string" ||
    text.trim().length === 0 ||
    text.length > max
  ) {
    throw new HelpdeskClientError("INVALID_INPUT");
  }
  return text;
}

function invalidResponse(): never {
  throw new HelpdeskClientError("INVALID_RESPONSE");
}

function object(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return invalidResponse();
  }
  return value as Record<string, unknown>;
}

function text(value: unknown): string {
  return typeof value === "string" ? value : invalidResponse();
}

function nullableText(value: unknown): string | null {
  return value === null ? null : text(value);
}

function id(value: unknown): string {
  const candidate = text(value);
  return UUID.test(candidate) ? candidate : invalidResponse();
}

function version(value: unknown): number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0
    ? value
    : invalidResponse();
}

function array(value: unknown): unknown[] {
  return Array.isArray(value) ? value : invalidResponse();
}

export function decodeHelpdeskRequest(body: unknown): HelpdeskRequest {
  const source = object(body);
  if (source.requestType !== "IT_HELPDESK") return invalidResponse();
  return {
    id: id(source.id),
    title: text(source.title),
    description: text(source.description),
    requestType: "IT_HELPDESK",
    requesterId: text(source.requesterId),
    assigneeId: nullableText(source.assigneeId),
    resolution: nullableText(source.resolution),
    assignmentVersion: version(source.assignmentVersion),
    workflowInstanceId: text(source.workflowInstanceId),
    state: text(source.state),
    version: version(source.version),
    allowedActions: array(source.allowedActions).map(text),
    ...(source.requesterDisplayName === undefined
      ? {}
      : { requesterDisplayName: nullableText(source.requesterDisplayName) }),
    ...(source.assigneeDisplayName === undefined
      ? {}
      : { assigneeDisplayName: nullableText(source.assigneeDisplayName) }),
    ...(source.eligibleAssignees === undefined
      ? {}
      : {
          eligibleAssignees:
            source.eligibleAssignees === null
              ? null
              : array(source.eligibleAssignees).map((value) => {
                  const option = object(value);
                  const assigneeId = text(option.id);
                  if (!assigneeId.trim()) return invalidResponse();
                  return {
                    id: assigneeId,
                    displayName: nullableText(option.displayName),
                  };
                }),
        }),
  };
}

export function decodeHelpdeskPage(body: unknown): HelpdeskPage {
  const source = object(body);
  return {
    items: array(source.items).map(decodeHelpdeskRequest),
    nextCursor: source.nextCursor === null ? null : id(source.nextCursor),
  };
}

export function decodeHelpdeskHistory(
  body: unknown,
): readonly HelpdeskHistoryEntry[] {
  return array(body).map((value) => {
    const source = object(value);
    const occurredAt = source.occurredAt;
    if (
      typeof occurredAt !== "string" &&
      !(typeof occurredAt === "number" && Number.isFinite(occurredAt))
    )
      return invalidResponse();
    return {
      transitionId: text(source.transitionId),
      sourceState: text(source.sourceState),
      targetState: text(source.targetState),
      version: version(source.version),
      occurredAt,
    };
  });
}
