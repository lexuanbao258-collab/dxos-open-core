/** Internal error categories, not an assumed Backend error envelope. */
export type MvpErrorCode =
  | "UNAUTHENTICATED"
  | "FORBIDDEN"
  | "NOT_FOUND"
  | "CONFLICT"
  | "NETWORK_FAILURE"
  | "COMMIT_OUTCOME_UNKNOWN";

const messages: Record<MvpErrorCode, string> = {
  UNAUTHENTICATED: "Phiên đã hết hạn hoặc chưa được xác thực.",
  FORBIDDEN: "Bạn không có quyền thực hiện thao tác này.",
  NOT_FOUND: "Không tìm thấy yêu cầu hoặc yêu cầu không khả dụng.",
  CONFLICT:
    "Dữ liệu đã thay đổi. Tải lại trước khi quyết định thao tác tiếp theo.",
  NETWORK_FAILURE: "Không thể tải dữ liệu do lỗi kết nối.",
  COMMIT_OUTCOME_UNKNOWN:
    "Chưa xác định kết quả ghi. Không gửi lại; cần đối soát trước khi tiếp tục.",
};

export class MvpApplicationError extends Error {
  constructor(
    readonly code: MvpErrorCode,
    options?: ErrorOptions,
  ) {
    super(messages[code], options);
    this.name = "MvpApplicationError";
  }
}

/** Call only for a known HTTP response; does not inspect or guess JSON fields. */
export function errorFromHttpStatus(
  status: 401 | 403 | 404 | 409,
): MvpApplicationError {
  const codes = {
    401: "UNAUTHENTICATED",
    403: "FORBIDDEN",
    404: "NOT_FOUND",
    409: "CONFLICT",
  } as const;
  return new MvpApplicationError(codes[status]);
}

/**
 * For a failed attempted operation without proof that a write did not commit.
 * A timeout/abort/lost response cannot safely be treated as a rejected write.
 */
export function networkFailure(
  operation: "read" | "write",
  cause?: unknown,
): MvpApplicationError {
  return new MvpApplicationError(
    operation === "write" ? "COMMIT_OUTCOME_UNKNOWN" : "NETWORK_FAILURE",
    { cause },
  );
}

/**
 * Invoke one already-prepared write once. No retry, token refresh, replay,
 * idempotency key, automatic read/reconciliation or session/UI side effects.
 * Run local validation before calling. Unknown failures are conservative because
 * the invocation may have committed. A future adapter must not hide uncertain
 * orchestration outcomes behind an ordinary rejection category.
 */
export async function executeWriteOnce<T>(write: () => Promise<T>): Promise<T> {
  try {
    return await write();
  } catch (error) {
    if (error instanceof MvpApplicationError) {
      if (error.code !== "NETWORK_FAILURE") throw error;
    }
    throw networkFailure("write", error);
  }
}
