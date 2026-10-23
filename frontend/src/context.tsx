import {
  createContext,
  useCallback,
  useContext,
  useState,
  type ReactNode,
} from "react";
import type { DemoRole } from "./domain/request";

interface PortalContextValue {
  role: DemoRole | null;
  setRole: (role: DemoRole | null) => void;
  sessionExpired: boolean;
  expireSession: () => void;
  revision: number;
  refresh: () => void;
  notify: (message: string) => void;
}
const PortalContext = createContext<PortalContextValue | null>(null);
export function PortalProvider({ children }: { children: ReactNode }) {
  const [role, updateRole] = useState<DemoRole | null>(() => {
    const value = sessionStorage.getItem("dxos-demo-role");
    return value === "employee" || value === "processor" ? value : null;
  });
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState("");
  const [sessionExpired, setSessionExpired] = useState(false);
  const setRole = useCallback((value: DemoRole | null) => {
    value
      ? sessionStorage.setItem("dxos-demo-role", value)
      : sessionStorage.removeItem("dxos-demo-role");
    updateRole(value);
    setSessionExpired(false);
    setRevision((v) => v + 1);
  }, []);
  const expireSession = useCallback(() => {
    sessionStorage.removeItem("dxos-demo-role");
    updateRole(null);
    setSessionExpired(true);
    setNotice("");
  }, []);
  return (
    <PortalContext.Provider
      value={{
        role,
        setRole,
        sessionExpired,
        expireSession,
        revision,
        refresh: () => setRevision((v) => v + 1),
        notify: setNotice,
      }}
    >
      {children}
      {notice && (
        <div className="toast" role="status">
          <span>✓</span>
          <div>{notice}</div>
          <button onClick={() => setNotice("")} aria-label="Đóng thông báo">
            ×
          </button>
        </div>
      )}
    </PortalContext.Provider>
  );
}
export function usePortal() {
  const ctx = useContext(PortalContext);
  if (!ctx) throw new Error("PortalProvider required");
  return ctx;
}
