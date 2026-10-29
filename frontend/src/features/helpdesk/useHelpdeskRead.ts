import { useEffect, useState } from "react";
import { usePortal } from "../../context";
import { useSessionOperation } from "../../hooks/useSessionOperation";
import { MvpApplicationError } from "../../services/mvp/errors";
import { HelpdeskClientError } from "../../services/mvp/http/errors";

export function helpdeskErrorMessage(error: unknown) {
  return error instanceof MvpApplicationError ||
    error instanceof HelpdeskClientError
    ? error.message
    : "Không thể tải dữ liệu Helpdesk. Vui lòng tải lại.";
}

/** Cancel obsolete reads; a new loader must never expose a previous resource. */
export function useHelpdeskRead<T>(load: () => Promise<T>) {
  const { expireSession } = usePortal();
  const captureOperation = useSessionOperation();
  const [revision, setRevision] = useState(0);
  const [result, setResult] = useState<{
    load: typeof load;
    captureOperation: typeof captureOperation;
    revision: number;
    data?: T;
    error?: string;
  }>();
  useEffect(() => {
    let active = true;
    const operation = captureOperation();
    Promise.resolve()
      .then(() => {
        if (active && operation.canUpdate()) return load();
      })
      .then(
        (data) => {
          if (active && operation.canUpdate())
            setResult({ load, captureOperation, revision, data });
        },
        (error: unknown) => {
          if (!operation.isCurrent()) return;
          if (
            error instanceof MvpApplicationError &&
            error.code === "UNAUTHENTICATED"
          )
            expireSession();
          if (active && operation.canUpdate())
            setResult({
              load,
              captureOperation,
              revision,
              error: helpdeskErrorMessage(error),
            });
        },
      );
    return () => {
      active = false;
    };
  }, [load, revision, expireSession, captureOperation]);
  const current =
    result?.load === load &&
    result.captureOperation === captureOperation &&
    result.revision === revision
      ? result
      : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: current === undefined,
    reload: () => setRevision((value) => value + 1),
    revision,
  };
}
