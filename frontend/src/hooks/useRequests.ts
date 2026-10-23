import { useEffect, useState } from "react";
import type { ServiceRequest } from "../domain/request";
import { usePortal } from "../context";
import { requestService } from "../services/portalService";
import { safeError } from "../services/RequestService";

export function useRequests(id?: string) {
  const { role, revision, expireSession } = usePortal();
  const [data, setData] = useState<ServiceRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [attempt, retry] = useState(0);
  useEffect(() => {
    let ignore = false;
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
        if (!ignore) setData(items);
      })
      .catch((e: unknown) => {
        if (!ignore) {
          setError(safeError(e));
          if (e instanceof Error && "code" in e && e.code === "401") {
            expireSession();
          }
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [id, role, revision, attempt, expireSession]);
  return { data, loading, error, reload: () => retry((v) => v + 1) };
}
