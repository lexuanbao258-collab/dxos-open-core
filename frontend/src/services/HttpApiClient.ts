import { PortalError } from "./RequestService";

/** Deliberately closed seam. No endpoint, token, transport or enable flag until security/contract review. */
export class HttpApiClient {
  readonly mode = "BLOCKED" as const;
  async request(
    _method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE",
    _path: string,
    _body?: unknown,
  ): Promise<never> {
    throw new PortalError(
      "LIVE_BLOCKED",
      "BLOCKED API · Tích hợp LIVE chưa được phê duyệt (#22/#23/#24/#37).",
    );
  }
}
