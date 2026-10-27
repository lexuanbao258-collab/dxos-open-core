import {
  createContext,
  useCallback,
  useContext,
  useState,
  useRef,
  type ReactNode,
} from "react";
import type { DemoRole } from "./domain/request";
import { requestService as defaultRequestService } from "./services/portalService";
import type { RequestService } from "./services/RequestService";
import type { MvpHelpdeskRequestPort } from "./services/mvp/helpdeskPortalPort";
import type { MvpApplicationError } from "./services/mvp/errors";

interface PortalContextValue {
  requestService: RequestService;
  helpdeskService?: MvpHelpdeskRequestPort;
  helpdeskWriteStops: ReadonlyMap<string, MvpApplicationError>;
  stopHelpdeskWrite: (key: string, error: MvpApplicationError) => void;
  releaseHelpdeskReadStop: (key: string) => void;
  helpdeskPending: ReadonlySet<string>;
  beginHelpdeskWrite: (key: string) => boolean;
  endHelpdeskWrite: (key: string) => void;
  role: DemoRole | null;
  setRole: (role: DemoRole | null) => void;
  sessionExpired: boolean;
  expireSession: () => void;
  captureSession: () => () => boolean;
  revision: number;
  refresh: () => void;
  notify: (message: string) => void;
}
const PortalContext = createContext<PortalContextValue | null>(null);
// Default stays MOCK. The optional MVP port has its own presentation model;
// it never masquerades as RequestService or selects a transport from an env flag.
export function PortalProvider({
  children,
  requestService = defaultRequestService,
  helpdeskService,
}: {
  children: ReactNode;
  requestService?: RequestService;
  helpdeskService?: MvpHelpdeskRequestPort;
}) {
  // Invalidate pending UI work synchronously on logout/expiry/role changes.
  // This is a demo lifecycle boundary, not an authentication credential.
  const [sessionScope, setSessionScope] = useState(() => ({}));
  const currentSessionScope = useRef(sessionScope);
  const currentServices = useRef({ requestService, helpdeskService });
  currentServices.current = { requestService, helpdeskService };
  const captureSession = useCallback(() => {
    return () =>
      currentSessionScope.current === sessionScope &&
      currentServices.current.requestService === requestService &&
      currentServices.current.helpdeskService === helpdeskService;
  }, [sessionScope, requestService, helpdeskService]);
  // In-memory stops survive feature navigation. Unknown writes require an
  // approved reconciliation procedure; a read or role switch cannot release them.
  const [helpdeskWriteStops, setHelpdeskWriteStops] = useState<
    ReadonlyMap<string, MvpApplicationError>
  >(() => new Map());
  const pendingWrites = useRef(new Set<string>());
  const [helpdeskPending, setHelpdeskPending] = useState<ReadonlySet<string>>(
    () => new Set(),
  );
  const beginHelpdeskWrite = useCallback((key: string) => {
    if (pendingWrites.current.has(key)) return false;
    pendingWrites.current.add(key);
    setHelpdeskPending(new Set(pendingWrites.current));
    return true;
  }, []);
  const endHelpdeskWrite = useCallback((key: string) => {
    pendingWrites.current.delete(key);
    setHelpdeskPending(new Set(pendingWrites.current));
  }, []);
  const stopHelpdeskWrite = useCallback(
    (key: string, error: MvpApplicationError) => {
      setHelpdeskWriteStops((previous) => new Map(previous).set(key, error));
    },
    [],
  );
  const releaseHelpdeskReadStop = useCallback((key: string) => {
    setHelpdeskWriteStops((previous) => {
      const code = previous.get(key)?.code;
      if (code !== "CONFLICT" && code !== "FORBIDDEN" && code !== "NOT_FOUND")
        return previous;
      const next = new Map(previous);
      next.delete(key);
      return next;
    });
  }, []);
  const [role, updateRole] = useState<DemoRole | null>(() => {
    try {
      const value = sessionStorage.getItem("dxos-demo-role");
      return value === "employee" || value === "processor" ? value : null;
    } catch {
      return null;
    }
  });
  const [revision, setRevision] = useState(0);
  const [notice, setNotice] = useState("");
  const [sessionExpired, setSessionExpired] = useState(false);
  const setRole = useCallback((value: DemoRole | null) => {
    const nextScope = {};
    currentSessionScope.current = nextScope;
    setSessionScope(nextScope);
    try {
      value
        ? sessionStorage.setItem("dxos-demo-role", value)
        : sessionStorage.removeItem("dxos-demo-role");
    } catch {
      // Storage is optional; the current demo session lives in React state.
    }
    updateRole(value);
    setSessionExpired(false);
    setNotice("");
    setRevision((v) => v + 1);
  }, []);
  const expireSession = useCallback(() => {
    const nextScope = {};
    currentSessionScope.current = nextScope;
    setSessionScope(nextScope);
    try {
      sessionStorage.removeItem("dxos-demo-role");
    } catch {
      // Always expire the in-memory session, even when storage is blocked.
    }
    updateRole(null);
    setSessionExpired(true);
    setNotice("");
  }, []);
  return (
    <PortalContext.Provider
      value={{
        requestService,
        helpdeskService,
        helpdeskWriteStops,
        stopHelpdeskWrite,
        releaseHelpdeskReadStop,
        helpdeskPending,
        beginHelpdeskWrite,
        endHelpdeskWrite,
        role,
        setRole,
        sessionExpired,
        expireSession,
        captureSession,
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
