import { useId, useRef, useState, type FormEvent } from "react";
import { AlertCircle, LoaderCircle } from "lucide-react";
import {
  executeWriteOnce,
  MvpApplicationError,
  networkFailure,
} from "../services/mvp/errors";
import type {
  Assignee,
  AssigneeId,
  EligibleAssignees,
} from "../services/mvp/types";
import { Empty, Icon } from "./ui";

/** Internal UI capability, populated only from confirmed Backend permission. */
export type AssignmentPermission = "allowed" | "denied" | "unknown";

/**
 * CANDIDATE callback seam, not an HTTP DTO or a concurrency token.
 * A ready callback must be bound by Application to the request and its approved
 * assignment-specific guard. Never use Workflow version as that guard.
 * The Helpdesk Application binding uses the reviewed assignmentVersion contract.
 */
export type AssignmentSubmitBinding =
  | { readonly status: "blocked"; readonly reason?: string }
  | {
      readonly status: "ready";
      readonly submitAssignment: (assigneeId: AssigneeId) => Promise<void>;
    };

export interface AssignmentFormProps {
  readonly currentAssignee?: Assignee | null;
  readonly eligibleAssignees?: EligibleAssignees;
  readonly assignmentPermission?: AssignmentPermission;
  readonly loading?: boolean;
  /** Safe, user-facing Application message, not a raw server diagnostic. */
  readonly error?: string;
  readonly submission?: AssignmentSubmitBinding;
}

/**
 * Reusable form, mounted by APP-14 only with an injected MVP service. No role/state inference,
 * token creation, Workflow transition, optimistic assignee update or HTTP calls.
 * Write errors stop this instance; reopening/reconciliation belongs to Application
 * after its contract is approved. Prop changes do not retry or clear that stop.
 */
export function AssignmentForm({
  currentAssignee,
  eligibleAssignees,
  assignmentPermission = "unknown",
  loading = false,
  error,
  submission,
}: AssignmentFormProps) {
  const formId = useId();
  const [selectedId, setSelectedId] = useState<AssigneeId | null>(null);
  const [busy, setBusy] = useState(false);
  const [writeError, setWriteError] = useState<MvpApplicationError | null>(
    null,
  );
  const lock = useRef(false);
  const choices = eligibleAssignees?.items ?? [];
  // Local option keys are NOT IDs. Preserve the actual opaque ID reference.
  // A refreshed reference requires an explicit new selection; no guessed ID
  // equality/serialization protocol is introduced while the DTO is unconfirmed.
  const selectedIndex = choices.findIndex((item) => item.id === selectedId);
  const selected = choices[selectedIndex];
  const staleSelection = selectedId !== null && selected === undefined;
  const bindingReady = submission?.status === "ready";
  const blocked =
    assignmentPermission !== "allowed" ||
    !bindingReady ||
    loading ||
    Boolean(error) ||
    busy ||
    writeError !== null;
  const canSubmit = !blocked && selected !== undefined;

  const hint =
    assignmentPermission !== "allowed"
      ? assignmentPermission === "denied"
        ? "Bạn không có quyền phân công yêu cầu này."
        : "Chưa xác nhận quyền phân công."
      : !bindingReady
        ? submission?.status === "blocked" && submission.reason
          ? submission.reason
          : "Phân công chưa sẵn sàng."
        : writeError
          ? "Đã dừng gửi. Cần kiểm tra kết quả trước khi tiếp tục."
          : staleSelection
            ? "Danh sách đã thay đổi. Hãy chọn lại người xử lý."
            : "Chọn người xử lý từ danh sách được cung cấp.";

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (lock.current || !canSubmit || submission?.status !== "ready") return;
    lock.current = true;
    setBusy(true);
    try {
      await executeWriteOnce(() => submission.submitAssignment(selected.id));
      // Application owns authoritative data. Never replace currentAssignee or
      // announce an optimistic success; a caller may refresh its supplied props.
      setSelectedId(null);
    } catch (failure) {
      setWriteError(
        failure instanceof MvpApplicationError
          ? failure
          : networkFailure("write", failure),
      );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }

  return (
    <form
      className="panel request-form"
      aria-labelledby={`${formId}-heading`}
      aria-busy={loading || busy}
      onSubmit={submit}
    >
      <h2 id={`${formId}-heading`}>Phân công xử lý</h2>
      <p>
        Người phụ trách hiện tại:{" "}
        <span>
          {currentAssignee === undefined
            ? "Chưa có thông tin được cung cấp"
            : currentAssignee === null
              ? "Chưa phân công"
              : currentAssignee.displayName || "Tên không được cung cấp"}
        </span>
      </p>
      {loading && (
        <div className="loading-state" role="status">
          <Icon icon={LoaderCircle} />
          <p>Đang tải dữ liệu phân công…</p>
        </div>
      )}
      {error && (
        <div className="inline-notice" role="alert">
          <Icon icon={AlertCircle} />
          <p>{error}</p>
        </div>
      )}
      {writeError && (
        <div className="inline-notice" role="alert">
          <Icon icon={AlertCircle} />
          <p>{writeError.message}</p>
        </div>
      )}
      {!loading && !error && choices.length === 0 && (
        <Empty
          title={
            eligibleAssignees === undefined
              ? "Chưa có danh sách người xử lý"
              : "Danh sách được cung cấp chưa có người xử lý"
          }
          description="Chưa có người xử lý để chọn trong dữ liệu được phép cung cấp."
        />
      )}
      <fieldset disabled={blocked || choices.length === 0}>
        <legend>Chọn người xử lý</legend>
        <label className="field" htmlFor={`${formId}-assignee`}>
          Người xử lý
          <select
            id={`${formId}-assignee`}
            value={selectedIndex < 0 ? "" : `choice-${selectedIndex}`}
            required
            aria-describedby={`${formId}-hint`}
            onChange={(event) => {
              const item = choices.find(
                (_, index) => `choice-${index}` === event.currentTarget.value,
              );
              setSelectedId(item?.id ?? null);
            }}
          >
            <option value="">Chọn người xử lý</option>
            {choices.map((item, index) => (
              <option key={`choice-${index}`} value={`choice-${index}`}>
                {item.displayName || "Tên không được cung cấp"}
              </option>
            ))}
          </select>
        </label>
        <div className="form-actions">
          <button
            type="submit"
            className="button primary"
            disabled={!canSubmit}
          >
            {busy ? "Đang phân công…" : "Phân công"}
          </button>
        </div>
      </fieldset>
      <p id={`${formId}-hint`} className="field-hint">
        {hint}
      </p>
    </form>
  );
}
