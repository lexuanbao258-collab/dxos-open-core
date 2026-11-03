/** Local protocol errors, independent of the Backend ApiError wire envelope. */
export type HelpdeskClientErrorCode =
  | "INVALID_INPUT"
  | "INVALID_RESPONSE"
  | "HTTP_FAILURE";

const messages: Record<HelpdeskClientErrorCode, string> = {
  INVALID_INPUT: "Dữ liệu gửi tới Helpdesk API không hợp lệ.",
  INVALID_RESPONSE: "Helpdesk API trả dữ liệu không đúng contract đã review.",
  HTTP_FAILURE: "Helpdesk API chưa thể hoàn tất yêu cầu.",
};

export class HelpdeskClientError extends Error {
  constructor(
    readonly code: HelpdeskClientErrorCode,
    readonly status?: number,
    readonly backendCode?: string,
  ) {
    super(messages[code]);
    this.name = "HelpdeskClientError";
  }
}
