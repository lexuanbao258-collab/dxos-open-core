import { useEffect, useState } from "react";
import type { ServiceRequest } from "../domain/request";
import { usePortal } from "../context";
import { safeError } from "../services/RequestService";
import { useSessionOperation } from "./useSessionOperation";

export function useRequests(id?: string) {
  const { role, revision, expireSession, requestService } = usePortal();
  const captureOperation = useSessionOperation();
  const [data, setData] = useState<ServiceRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, retry] = useState(0);
  useEffect(() => {
    let ignore = false;
    const session = captureOperation();
    setLoading(true);
    setError("");
    setData([]);
    if (!role) {
      setLoading(false);
      return;
    }
    const operation = id
      ? requestService.get(id, role).then((item) => [item])
      : requestService.list(role);
    operation
      .then((items) => {
        if (!ignore && session.canUpdate()) setData(items);
      })
      .catch((e: unknown) => {
        if (!session.isCurrent()) return;
        if (e instanceof Error && "code" in e && e.code === "401") {
          expireSession();
          return;
        }
        if (!ignore && session.canUpdate()) setError(safeError(e));
      })
      .finally(() => {
        if (!ignore && session.canUpdate()) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [
    id,
    role,
    revision,
    attempt,
    expireSession,
    requestService,
    captureOperation,
  ]);
  return { data, loading, error, reload: () => retry((v) => v + 1) };
}
