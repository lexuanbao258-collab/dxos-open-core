import {
  errorFromHttpStatus,
  MvpApplicationError,
  networkFailure,
} from "../errors";
import {
  decodeHelpdeskHistory,
  decodeHelpdeskPage,
  decodeHelpdeskRequest,
  validateId,
  validateText,
  validateVersion,
} from "./codecs";
import { HelpdeskClientError } from "./errors";
import type { HelpdeskHttpRequest, HelpdeskHttpTransport } from "./transport";
import type {
  HelpdeskAssignInput,
  HelpdeskCreateInput,
  HelpdeskListInput,
  HelpdeskResolveInput,
  HelpdeskVersionInput,
} from "./types";

const BASE = "/api/v1/mvp/requests";

/**
 * PR #63 wire client, unbound to Portal, RequestService and MvpRequestPort.
 * No concrete network transport is shipped. Construction requires injection.
 * Nine explicit routes only; /start maps to start_work inside Backend, not here.
 */
export class HelpdeskApiClient {
  private sessionInvalid = false;

  constructor(
    private readonly transport: HelpdeskHttpTransport,
    private readonly onSessionInvalid: () => void,
  ) {
    if (
      typeof transport !== "function" ||
      typeof onSessionInvalid !== "function"
    ) {
      throw new HelpdeskClientError("INVALID_INPUT");
    }
  }

  create(input: HelpdeskCreateInput) {
    return this.post(
      BASE,
      {
        title: validateText(input.title, 200),
        description: validateText(input.description, 4000),
        requestType: "IT_HELPDESK",
      },
      201,
    );
  }

  list(input: HelpdeskListInput = {}) {
    const query = new URLSearchParams();
    if (input.after !== undefined) query.set("after", validateId(input.after));
    if (input.limit !== undefined) {
      if (
        !Number.isInteger(input.limit) ||
        input.limit < 1 ||
        input.limit > 100
      ) {
        throw new HelpdeskClientError("INVALID_INPUT");
      }
      query.set("limit", String(input.limit));
    }
    const suffix = query.size === 0 ? "" : `?${query}`;
    return this.get(BASE + suffix, decodeHelpdeskPage);
  }

  detail(id: string) {
    return this.get(this.path(id), decodeHelpdeskRequest);
  }

  triage(id: string, input: HelpdeskVersionInput) {
    return this.post(this.path(id, "/triage"), {
      expectedVersion: validateVersion(input.expectedVersion),
    });
  }

  assign(id: string, input: HelpdeskAssignInput) {
    return this.post(this.path(id, "/assign"), {
      assigneeId: validateText(input.assigneeId, 1024),
      expectedAssignmentVersion: validateVersion(
        input.expectedAssignmentVersion,
      ),
    });
  }

  start(id: string, input: HelpdeskVersionInput) {
    return this.post(this.path(id, "/start"), {
      expectedVersion: validateVersion(input.expectedVersion),
    });
  }

  resolve(id: string, input: HelpdeskResolveInput) {
    return this.post(this.path(id, "/resolve"), {
      expectedVersion: validateVersion(input.expectedVersion),
      resolution: validateText(input.resolution, 4000),
    });
  }

  confirm(id: string, input: HelpdeskVersionInput) {
    return this.post(this.path(id, "/confirm"), {
      expectedVersion: validateVersion(input.expectedVersion),
    });
  }

  history(id: string) {
    return this.get(this.path(id, "/history"), decodeHelpdeskHistory);
  }

  private path(id: string, suffix = "") {
    return `${BASE}/${encodeURIComponent(validateId(id))}${suffix}`;
  }

  private get<T>(path: string, decode: (body: unknown) => T): Promise<T> {
    return this.send(
      { method: "GET", path, headers: { Accept: "application/json" } },
      200,
      decode,
    );
  }

  private post(
    path: string,
    body: Readonly<Record<string, unknown>>,
    status = 200,
  ) {
    return this.send(
      {
        method: "POST",
        path,
        body,
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
      },
      status,
      decodeHelpdeskRequest,
    );
  }

  private async send<T>(
    request: HelpdeskHttpRequest,
    expectedStatus: number,
    decode: (body: unknown) => T,
  ): Promise<T> {
    // A 401 closes this client instance. A new approved session needs a new client;
    // no refresh/replay or token storage is introduced here.
    if (this.sessionInvalid) throw errorFromHttpStatus(401);
    const write = request.method === "POST";
    let response;
    try {
      response = await this.transport(request); // Exactly one attempt; never replay.
    } catch (cause) {
      throw networkFailure(write ? "write" : "read", cause);
    }
    if (
      !response ||
      !Number.isInteger(response.status) ||
      response.status < 100 ||
      response.status > 599
    ) {
      const cause = new HelpdeskClientError("INVALID_RESPONSE");
      throw write ? networkFailure("write", cause) : cause;
    }
    if (response.status !== expectedStatus) {
      if (response.status === 401) {
        if (!this.sessionInvalid) {
          this.sessionInvalid = true;
          try {
            this.onSessionInvalid();
          } catch {
            /* Keep the safe 401 boundary. */
          }
        }
        throw errorFromHttpStatus(401);
      }
      const body = response.body;
      const backendCode =
        body !== null &&
        typeof body === "object" &&
        "code" in body &&
        typeof body.code === "string"
          ? body.code
          : undefined;
      const status = response.status;
      if (status === 403 || status === 404 || status === 409) {
        throw errorFromHttpStatus(status);
      }
      if (backendCode === "COMMIT_OUTCOME_UNKNOWN") {
        throw new MvpApplicationError("COMMIT_OUTCOME_UNKNOWN");
      }
      if (
        write &&
        (status === 408 || status >= 500 || (status >= 200 && status < 400))
      ) {
        throw networkFailure(
          "write",
          new HelpdeskClientError("HTTP_FAILURE", status, backendCode),
        );
      }
      throw new HelpdeskClientError("HTTP_FAILURE", status, backendCode);
    }
    try {
      return decode(response.body);
    } catch (cause) {
      // A successful write with an unreadable reply may already be committed.
      throw write ? networkFailure("write", cause) : cause;
    }
  }
}
