import { useCallback, useEffect, useRef } from "react";
import { usePortal } from "../context";

/** Capture before awaiting: 401 still expires the same session after navigation,
 * while UI updates require the originating screen and session to remain current.
 * Cancelling UI work does not cancel or replay an attempted write.
 */
export function useSessionOperation() {
  const { captureSession } = usePortal();
  const mounted = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  return useCallback(() => {
    const isCurrent = captureSession();
    return {
      isCurrent,
      canUpdate: () => mounted.current && isCurrent(),
    };
  }, [captureSession]);
}
